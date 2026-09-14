package app.archiverecovery.extract;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SevenZipExtractionService {
    private static final int MAX_MESSAGE_LENGTH = 900;
    private static final Pattern PROGRESS_PERCENT = Pattern.compile(
            "(?:^|[\\s\\[\\(:])([0-9]{1,3})\\s*%(?=$|[\\s\\]\\)])");
    private static final Pattern CURRENT_ENTRY = Pattern.compile(
            "(?:^|[0-9]{1,3}%\\s*)-\\s+([^\\r\\n]+)$");
    private final ArchiveExtractionPlanner planner = new ArchiveExtractionPlanner();
    private final BundledSevenZip bundledSevenZip;

    public SevenZipExtractionService() {
        this(new BundledSevenZip());
    }

    SevenZipExtractionService(BundledSevenZip bundledSevenZip) {
        this.bundledSevenZip = bundledSevenZip;
    }

    public ExtractionBatchResult extract(Collection<Path> archives,
                                         ExtractionSettings settings,
                                         PasswordProvider passwordProvider,
                                         Consumer<String> progress) throws IOException, InterruptedException {
        return extract(archives, settings, passwordProvider, null, progress);
    }

    public ExtractionBatchResult extract(Collection<Path> archives,
                                         ExtractionSettings settings,
                                         PasswordProvider passwordProvider,
                                         NestedArchiveDecisionProvider decisionProvider,
                                         Consumer<String> progress) throws IOException, InterruptedException {
        return extract(archives, settings, passwordProvider, decisionProvider, progress,
                ignored -> {
                });
    }

    public ExtractionBatchResult extract(Collection<Path> archives,
                                         ExtractionSettings settings,
                                         PasswordProvider passwordProvider,
                                         NestedArchiveDecisionProvider decisionProvider,
                                         Consumer<String> progress,
                                         Consumer<ExtractionProgress> detailedProgress)
            throws IOException, InterruptedException {
        validateSettings(settings);
        Files.createDirectories(settings.outputRoot());
        List<ExtractionTask> tasks = planner.createTasks(archives, settings.outputRoot());
        if (tasks.isEmpty()) {
            detailedProgress.accept(new ExtractionProgress(
                    ExtractionProgress.Phase.PREPARING_LAYER,
                    0, 0, 0, null, "没有发现可解压的入口文件"));
            return new ExtractionBatchResult(List.of());
        }

        ExecutorService executor = Executors.newFixedThreadPool(settings.concurrency());
        try {
            List<ExtractionResult> allResults = new ArrayList<>();
            int nestedArchiveCount = 0;
            boolean depthLimitReached = false;
            int consolidatedMultipartGroupCount = 0;
            int movedMultipartFileCount = 0;
            Set<String> organizationWarnings = new LinkedHashSet<>();
            int recoveredDisguisedFileCount = 0;
            int userConfirmedMultipartMovedCount = 0;
            boolean stoppedByUser = false;
            List<ExtractionTask> currentLayer = tasks;

            while (!currentLayer.isEmpty()) {
                int currentDepth = currentLayer.get(0).nestedDepth();
                String layerName = currentDepth == 0 ? "首层" : "嵌套第 " + currentDepth + " 层";
                detailedProgress.accept(new ExtractionProgress(
                        ExtractionProgress.Phase.PREPARING_LAYER,
                        currentDepth, 0, currentLayer.size(), null,
                        "准备解压" + layerName + "的 " + currentLayer.size() + " 个压缩包"));
                List<ExtractionResult> layerResults = executeLayer(
                        currentLayer, settings, passwordProvider, progress,
                        detailedProgress, executor);
                allResults.addAll(layerResults);

                List<Path> successfulOutputDirectories = layerResults.stream()
                        .filter(ExtractionResult::success)
                        .map(ExtractionResult::outputDirectory)
                        .toList();
                if (successfulOutputDirectories.isEmpty()) {
                    break;
                }

                int nextDepth = currentLayer.get(0).nestedDepth() + 1;
                detailedProgress.accept(new ExtractionProgress(
                        ExtractionProgress.Phase.SCANNING_NESTED,
                        nextDepth, currentLayer.size(), currentLayer.size(), null,
                        "本层解压完成，正在检查第 " + nextDepth + " 层嵌套文件"));
                NestedPreparation preparation = prepareNestedLayer(successfulOutputDirectories,
                        nextDepth, nextDepth <= settings.maxNestedDepth(),
                        decisionProvider, progress, detailedProgress);
                consolidatedMultipartGroupCount += preparation.consolidatedGroupCount();
                movedMultipartFileCount += preparation.movedVolumeCount();
                recoveredDisguisedFileCount += preparation.recoveredFileCount();
                userConfirmedMultipartMovedCount += preparation.userMovedFileCount();
                organizationWarnings.addAll(preparation.warnings());
                if (preparation.stoppedByUser()) {
                    stoppedByUser = true;
                    break;
                }
                List<ExtractionTask> nextLayer = preparation.tasks();
                if (nextDepth > settings.maxNestedDepth() && !nextLayer.isEmpty()) {
                    depthLimitReached = true;
                    break;
                }
                if (nextLayer.isEmpty()) {
                    break;
                }

                nestedArchiveCount += nextLayer.size();
                progress.accept("发现第 " + nextDepth + " 层压缩包 "
                        + nextLayer.size() + " 个，继续解压……");
                currentLayer = nextLayer;
            }
            return new ExtractionBatchResult(allResults, nestedArchiveCount, depthLimitReached,
                    consolidatedMultipartGroupCount, movedMultipartFileCount,
                    new ArrayList<>(organizationWarnings), recoveredDisguisedFileCount,
                    userConfirmedMultipartMovedCount, stoppedByUser);
        } finally {
            executor.shutdownNow();
        }
    }

    private NestedPreparation prepareNestedLayer(List<Path> extractedRoots,
                                                 int nestedDepth,
                                                 boolean reviewEnabled,
                                                 NestedArchiveDecisionProvider decisionProvider,
                                                 Consumer<String> progress,
                                                 Consumer<ExtractionProgress> detailedProgress) {
        Map<String, ExtractionTask> tasks = new LinkedHashMap<>();
        Set<String> warnings = new LinkedHashSet<>();
        int consolidatedGroups = 0;
        int movedVolumes = 0;
        int recoveredFiles = 0;
        int userMovedFiles = 0;

        for (Path root : extractedRoots) {
            ArchiveExtractionPlanner.NestedArchivePlan plan =
                    planner.createNestedPlan(List.of(root), nestedDepth);
            consolidatedGroups += plan.consolidatedGroupCount();
            movedVolumes += plan.movedVolumeCount();
            warnings.addAll(plan.warnings());
            reportConsolidation(plan, progress);

            if (decisionProvider == null || !reviewEnabled) {
                addTasks(tasks, plan.tasks());
                continue;
            }

            boolean firstReview = true;
            while ((firstReview && !plan.scannedFiles().isEmpty()) || hasUndecidedFiles(plan)) {
                firstReview = false;
                NestedArchiveInspection inspection = createInspection(root, nestedDepth, plan);
                detailedProgress.accept(new ExtractionProgress(
                        ExtractionProgress.Phase.WAITING_FOR_USER,
                        nestedDepth, 0, inspection.files().size(), null,
                        "等待确认第 " + nestedDepth + " 层发现的文件"));
                NestedArchiveDecision decision = decisionProvider.requestDecision(inspection);
                if (decision == null || decision.action() == NestedArchiveDecision.Action.STOP_ALL) {
                    return new NestedPreparation(new ArrayList<>(tasks.values()), consolidatedGroups,
                            movedVolumes, recoveredFiles, userMovedFiles,
                            new ArrayList<>(warnings), true);
                }
                if (decision.action() == NestedArchiveDecision.Action.FINISH_LAYER) {
                    break;
                }

                NestedArchiveRecoveryService.RecoveryResult recovery =
                        new NestedArchiveRecoveryService().recover(
                                root, decision.selectedFiles(), decision.renameMode());
                recoveredFiles += recovery.renamedCount();
                userMovedFiles += recovery.movedCount();
                warnings.addAll(recovery.warnings());
                if (recovery.renamedCount() > 0) {
                    progress.accept("第 " + nestedDepth + " 层已恢复伪装后缀 "
                            + recovery.renamedCount() + " 个文件");
                }

                ArchiveExtractionPlanner.NestedArchivePlan refreshed =
                        planner.createNestedPlan(List.of(root), nestedDepth);
                consolidatedGroups += refreshed.consolidatedGroupCount();
                movedVolumes += refreshed.movedVolumeCount();
                warnings.addAll(refreshed.warnings());
                reportConsolidation(refreshed, progress);
                plan = refreshed;

                if (recovery.renamedCount() == 0 && recovery.movedCount() == 0) {
                    progress.accept("所选文件未能完成修改，请重新选择或结束本层判断");
                }
            }
            addTasks(tasks, plan.tasks());
        }
        return new NestedPreparation(new ArrayList<>(tasks.values()), consolidatedGroups,
                movedVolumes, recoveredFiles, userMovedFiles,
                new ArrayList<>(warnings), false);
    }

    private NestedArchiveInspection createInspection(Path root,
                                                     int nestedDepth,
                                                     ArchiveExtractionPlanner.NestedArchivePlan plan) {
        List<NestedArchiveInspection.FileEntry> files = new ArrayList<>();
        for (Path path : plan.scannedFiles()) {
            long size;
            try {
                size = Files.size(path);
            } catch (IOException exception) {
                size = -1;
            }
            files.add(new NestedArchiveInspection.FileEntry(path, size,
                    plan.recognizedArchiveFiles().contains(pathKey(path))));
        }
        return new NestedArchiveInspection(root, nestedDepth, files);
    }

    private boolean hasUndecidedFiles(ArchiveExtractionPlanner.NestedArchivePlan plan) {
        return plan.scannedFiles().stream()
                .anyMatch(path -> !plan.recognizedArchiveFiles().contains(pathKey(path)));
    }

    private void addTasks(Map<String, ExtractionTask> destination,
                          List<ExtractionTask> tasks) {
        for (ExtractionTask task : tasks) {
            destination.putIfAbsent(pathKey(task.archive()), task);
        }
    }

    private void reportConsolidation(ArchiveExtractionPlanner.NestedArchivePlan plan,
                                     Consumer<String> progress) {
        if (plan.consolidatedGroupCount() > 0) {
            progress.accept("已整理跨文件夹分卷 " + plan.consolidatedGroupCount()
                    + " 组，共迁移 " + plan.movedVolumeCount() + " 个文件");
        }
    }

    private String pathKey(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    private List<ExtractionResult> executeLayer(List<ExtractionTask> tasks,
                                                ExtractionSettings settings,
                                                PasswordProvider passwordProvider,
                                                Consumer<String> progress,
                                                Consumer<ExtractionProgress> detailedProgress,
                                                ExecutorService executor) throws InterruptedException {
        List<Future<ExtractionResult>> futures = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger();
        for (ExtractionTask task : tasks) {
            Callable<ExtractionResult> work = () -> {
                detailedProgress.accept(new ExtractionProgress(
                        ExtractionProgress.Phase.EXTRACTING,
                        task.nestedDepth(), completed.get(), tasks.size(), task.archive(),
                        "正在解压：" + task.archive().getFileName()));
                try {
                    ExtractionResult result = extractOne(
                            task, settings, passwordProvider, progress, detailedProgress,
                            completed, tasks.size());
                    int finished = completed.incrementAndGet();
                    detailedProgress.accept(new ExtractionProgress(
                            ExtractionProgress.Phase.EXTRACTING,
                            task.nestedDepth(), finished, tasks.size(), task.archive(),
                            "已完成：" + task.archive().getFileName(),
                            result.success() ? 100 : -1));
                    return result;
                } catch (Exception exception) {
                    int finished = completed.incrementAndGet();
                    detailedProgress.accept(new ExtractionProgress(
                            ExtractionProgress.Phase.EXTRACTING,
                            task.nestedDepth(), finished, tasks.size(), task.archive(),
                            "解压异常：" + task.archive().getFileName()));
                    throw exception;
                }
            };
            futures.add(executor.submit(work));
        }

        List<ExtractionResult> results = new ArrayList<>();
        for (int index = 0; index < futures.size(); index++) {
            try {
                results.add(futures.get(index).get());
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                ExtractionTask task = tasks.get(index);
                results.add(new ExtractionResult(task.archive(), task.outputDirectory(), false, -1,
                        friendlyMessage(cause)));
            }
        }
        return results;
    }

    private ExtractionResult extractOne(ExtractionTask task,
                                        ExtractionSettings settings,
                                        PasswordProvider passwordProvider,
                                        Consumer<String> progress,
                                        Consumer<ExtractionProgress> detailedProgress,
                                        AtomicInteger completed,
                                        int total) throws IOException, InterruptedException {
        Files.createDirectories(task.outputDirectory());
        String layerText = task.nestedDepth() == 0
                ? ""
                : "（嵌套第 " + task.nestedDepth() + " 层）";
        progress.accept("正在解压" + layerText + "：" + task.archive().getFileName());

        char[] password = null;
        try {
            while (true) {
                ProcessAttempt attempt = runProcess(settings, task, password,
                        processProgress -> detailedProgress.accept(new ExtractionProgress(
                                ExtractionProgress.Phase.EXTRACTING,
                                task.nestedDepth(), completed.get(), total, task.archive(),
                                "正在解压：" + task.archive().getFileName(),
                                processProgress.percent(), processProgress.currentEntry())));
                if (attempt.exitCode() == 0) {
                    return new ExtractionResult(task.archive(), task.outputDirectory(), true, 0, "解压成功");
                }
                if (!isPasswordError(attempt.output()) || passwordProvider == null) {
                    return failedResult(task, attempt);
                }

                progress.accept("等待输入密码：" + task.archive().getFileName());
                detailedProgress.accept(new ExtractionProgress(
                        ExtractionProgress.Phase.WAITING_FOR_USER,
                        task.nestedDepth(), completed.get(), total, task.archive(),
                        "等待输入密码：" + task.archive().getFileName()));
                var suppliedPassword = passwordProvider.requestPassword(
                        task.archive(), password, attempt.output());
                clearPassword(password);
                password = null;
                if (suppliedPassword.isEmpty()) {
                    deleteIfEmpty(task.outputDirectory());
                    return new ExtractionResult(task.archive(), task.outputDirectory(), false,
                            attempt.exitCode(), "用户取消了密码输入");
                }
                password = suppliedPassword.get();
            }
        } finally {
            clearPassword(password);
        }
    }

    private ProcessAttempt runProcess(ExtractionSettings settings,
                                      ExtractionTask task,
                                      char[] password,
                                      Consumer<SevenZipProcessProgress> processProgress)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(createCommand(settings, task))
                .redirectErrorStream(true)
                .start();
        try (OutputStreamWriter writer = new OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8)) {
            if (password != null && password.length > 0) {
                writer.write(password);
            }
            writer.write(System.lineSeparator());
            writer.flush();
        }
        String output = readOutputTail(process.getInputStream(), processProgress);
        int exitCode = process.waitFor();
        return new ProcessAttempt(exitCode, sanitizeOutput(output));
    }

    private ExtractionResult failedResult(ExtractionTask task, ProcessAttempt attempt) {
        deleteIfEmpty(task.outputDirectory());
        String message = attempt.output().isBlank()
                ? "7z.exe 返回错误代码 " + attempt.exitCode()
                : attempt.output();
        return new ExtractionResult(task.archive(), task.outputDirectory(), false,
                attempt.exitCode(), message);
    }

    private void deleteIfEmpty(Path directory) {
        try {
            if (!Files.isDirectory(directory)) {
                return;
            }
            try (var children = Files.list(directory)) {
                if (children.findAny().isEmpty()) {
                    Files.deleteIfExists(directory);
                }
            }
        } catch (IOException ignored) {
            // 解压失败信息更重要；空目录无法清理时保留，不覆盖原始错误。
        }
    }

    boolean isPasswordError(String output) {
        String lower = output == null ? "" : output.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("0xa0000020")
                || lower.contains("0xa0000021")
                || lower.contains("enter password")
                || lower.contains("cannot open encrypted archive")
                || lower.contains("data error in encrypted file")
                || lower.contains("password is needed")
                || lower.contains("password required")
                || lower.contains("password is required")
                || lower.contains("invalid password")
                || lower.contains("wrong password")
                || lower.contains("incorrect password")
                || lower.contains("需要密码")
                || lower.contains("密码错误");
    }

    private void clearPassword(char[] password) {
        if (password != null) {
            java.util.Arrays.fill(password, '\0');
        }
    }

    List<String> createCommand(ExtractionSettings settings, ExtractionTask task) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(bundledSevenZip.executable().toString());
        command.add("x");
        command.add("-y");
        command.add("-aou");
        command.add("-bb1");
        command.add("-bsp1");
        command.add("-bso1");
        command.add("-bse2");
        command.add("-sccUTF-8");
        command.add("-o" + task.outputDirectory());
        command.add("--");
        command.add(task.archive().toString());
        return command;
    }

    private void validateSettings(ExtractionSettings settings) throws IOException {
        Path executable = bundledSevenZip.executable();
        if (!Files.isRegularFile(executable)
                || !Files.isRegularFile(executable.resolveSibling("7z.dll"))) {
            throw new IOException("内置 7-Zip 组件不完整，请重新下载程序");
        }
        if (Files.exists(settings.outputRoot()) && !Files.isDirectory(settings.outputRoot())) {
            throw new IOException("解压根目录不是文件夹：" + settings.outputRoot());
        }
    }

    String readOutputTail(InputStream inputStream,
                          Consumer<SevenZipProcessProgress> processProgress) throws IOException {
        StringBuilder tail = new StringBuilder();
        StringBuilder liveLine = new StringBuilder();
        SevenZipProcessProgress lastProgress = new SevenZipProcessProgress(-1, "");
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[512];
            int length;
            while ((length = reader.read(buffer)) >= 0) {
                String chunk = new String(buffer, 0, length);
                tail.append(chunk);
                for (int index = 0; index < length; index++) {
                    char character = buffer[index];
                    if (character == '\r' || character == '\n') {
                        if (!liveLine.isEmpty()) {
                            lastProgress = reportProgressLine(
                                    liveLine.toString(), lastProgress, processProgress);
                            liveLine.setLength(0);
                        }
                    } else {
                        liveLine.append(character);
                        if (liveLine.length() > 4096) {
                            liveLine.delete(0, liveLine.length() - 4096);
                        }
                    }
                }
                if (tail.length() > MAX_MESSAGE_LENGTH * 2) {
                    tail.delete(0, tail.length() - MAX_MESSAGE_LENGTH);
                }
            }
        }
        if (!liveLine.isEmpty()) {
            reportProgressLine(liveLine.toString(), lastProgress, processProgress);
        }
        return tail.toString();
    }

    private SevenZipProcessProgress reportProgressLine(
            String line,
            SevenZipProcessProgress previous,
            Consumer<SevenZipProcessProgress> processProgress) {
        int percent = parseProgressPercent(line).orElse(previous.percent());
        String currentEntry = previous.currentEntry();
        Matcher entryMatcher = CURRENT_ENTRY.matcher(line.strip());
        if (entryMatcher.find()) {
            currentEntry = entryMatcher.group(1).strip();
        }
        SevenZipProcessProgress current = new SevenZipProcessProgress(percent, currentEntry);
        if (!current.equals(previous)) {
            processProgress.accept(current);
        }
        return current;
    }

    OptionalInt parseProgressPercent(String line) {
        if (line == null || line.isBlank()) {
            return OptionalInt.empty();
        }
        return findLastProgressPercent(PROGRESS_PERCENT.matcher(line));
    }

    private OptionalInt findLastProgressPercent(Matcher matcher) {
        int percent = -1;
        while (matcher.find()) {
            int candidate = Integer.parseInt(matcher.group(1));
            if (candidate <= 100) {
                percent = candidate;
            }
        }
        return percent < 0 ? OptionalInt.empty() : OptionalInt.of(percent);
    }

    private String sanitizeOutput(String output) {
        String result = output == null ? "" : output.strip();
        if (result.length() > MAX_MESSAGE_LENGTH) {
            result = result.substring(result.length() - MAX_MESSAGE_LENGTH);
        }
        return result;
    }

    private String friendlyMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知解压错误";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private record ProcessAttempt(int exitCode, String output) {
    }

    record SevenZipProcessProgress(int percent, String currentEntry) {
        SevenZipProcessProgress {
            percent = Math.max(-1, Math.min(100, percent));
            currentEntry = currentEntry == null ? "" : currentEntry;
        }
    }

    private record NestedPreparation(List<ExtractionTask> tasks,
                                     int consolidatedGroupCount,
                                     int movedVolumeCount,
                                     int recoveredFileCount,
                                     int userMovedFileCount,
                                     List<String> warnings,
                                     boolean stoppedByUser) {
    }
}
