package com.example.extract;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public final class BzExtractionService {
    private static final int MAX_MESSAGE_LENGTH = 900;
    private final ArchiveExtractionPlanner planner = new ArchiveExtractionPlanner();

    public ExtractionBatchResult extract(Collection<Path> archives,
                                         ExtractionSettings settings,
                                         PasswordProvider passwordProvider,
                                         Consumer<String> progress) throws IOException, InterruptedException {
        validateSettings(settings);
        Files.createDirectories(settings.outputRoot());
        List<ExtractionTask> tasks = planner.createTasks(archives, settings.outputRoot());
        if (tasks.isEmpty()) {
            return new ExtractionBatchResult(List.of());
        }

        ExecutorService executor = Executors.newFixedThreadPool(settings.concurrency());
        try {
            List<ExtractionResult> allResults = new ArrayList<>();
            int nestedArchiveCount = 0;
            boolean depthLimitReached = false;
            int consolidatedMultipartGroupCount = 0;
            int movedMultipartFileCount = 0;
            List<String> organizationWarnings = new ArrayList<>();
            List<ExtractionTask> currentLayer = tasks;

            while (!currentLayer.isEmpty()) {
                List<ExtractionResult> layerResults = executeLayer(
                        currentLayer, settings, passwordProvider, progress, executor);
                allResults.addAll(layerResults);

                List<Path> successfulOutputDirectories = layerResults.stream()
                        .filter(ExtractionResult::success)
                        .map(ExtractionResult::outputDirectory)
                        .toList();
                if (successfulOutputDirectories.isEmpty()) {
                    break;
                }

                int nextDepth = currentLayer.get(0).nestedDepth() + 1;
                ArchiveExtractionPlanner.NestedArchivePlan nestedPlan = planner.createNestedPlan(
                        successfulOutputDirectories, nextDepth);
                consolidatedMultipartGroupCount += nestedPlan.consolidatedGroupCount();
                movedMultipartFileCount += nestedPlan.movedVolumeCount();
                organizationWarnings.addAll(nestedPlan.warnings());
                if (nestedPlan.consolidatedGroupCount() > 0) {
                    progress.accept("已整理跨文件夹分卷 " + nestedPlan.consolidatedGroupCount()
                            + " 组，共迁移 " + nestedPlan.movedVolumeCount() + " 个文件");
                }
                List<ExtractionTask> nextLayer = nestedPlan.tasks();
                if (nextLayer.isEmpty()) {
                    break;
                }
                if (nextDepth > settings.maxNestedDepth()) {
                    depthLimitReached = true;
                    break;
                }

                nestedArchiveCount += nextLayer.size();
                progress.accept("发现第 " + nextDepth + " 层压缩包 "
                        + nextLayer.size() + " 个，继续解压……");
                currentLayer = nextLayer;
            }
            return new ExtractionBatchResult(allResults, nestedArchiveCount, depthLimitReached,
                    consolidatedMultipartGroupCount, movedMultipartFileCount, organizationWarnings);
        } finally {
            executor.shutdownNow();
        }
    }

    private List<ExtractionResult> executeLayer(List<ExtractionTask> tasks,
                                                ExtractionSettings settings,
                                                PasswordProvider passwordProvider,
                                                Consumer<String> progress,
                                                ExecutorService executor) throws InterruptedException {
        List<Future<ExtractionResult>> futures = new ArrayList<>();
        for (ExtractionTask task : tasks) {
            Callable<ExtractionResult> work = () -> extractOne(
                    task, settings, passwordProvider, progress);
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
                                        Consumer<String> progress) throws IOException, InterruptedException {
        Files.createDirectories(task.outputDirectory());
        String layerText = task.nestedDepth() == 0
                ? ""
                : "（嵌套第 " + task.nestedDepth() + " 层）";
        progress.accept("正在解压" + layerText + "：" + task.archive().getFileName());

        char[] password = null;
        try {
            while (true) {
                ProcessAttempt attempt = runProcess(settings, task, password);
                if (attempt.exitCode() == 0) {
                    return new ExtractionResult(task.archive(), task.outputDirectory(), true, 0, "解压成功");
                }
                if (!isPasswordError(attempt.output()) || passwordProvider == null) {
                    return failedResult(task, attempt);
                }

                progress.accept("等待输入密码：" + task.archive().getFileName());
                var suppliedPassword = passwordProvider.requestPassword(
                        task.archive(), password, attempt.output());
                clearPassword(password);
                password = null;
                if (suppliedPassword.isEmpty()) {
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
                                      char[] password) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(createCommand(settings, task, password))
                .redirectErrorStream(true)
                .start();
        String output = readOutputTail(process.getInputStream());
        int exitCode = process.waitFor();
        return new ProcessAttempt(exitCode, sanitizeOutput(output, password));
    }

    private ExtractionResult failedResult(ExtractionTask task, ProcessAttempt attempt) {
        String message = attempt.output().isBlank()
                ? "bz.exe 返回错误代码 " + attempt.exitCode()
                : attempt.output();
        return new ExtractionResult(task.archive(), task.outputDirectory(), false,
                attempt.exitCode(), message);
    }

    boolean isPasswordError(String output) {
        String lower = output == null ? "" : output.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("0xa0000020")
                || lower.contains("0xa0000021")
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

    List<String> createCommand(ExtractionSettings settings, ExtractionTask task, char[] password) {
        List<String> command = new ArrayList<>();
        command.add(settings.bzExecutable().toString());
        command.add("x");
        command.add("-consolemode:utf8");
        command.add("-aou");
        command.add("-y");
        command.add("-o:" + task.outputDirectory());
        if (password != null && password.length > 0) {
            command.add("-p:" + new String(password));
        }
        command.add(task.archive().toString());
        return command;
    }

    private void validateSettings(ExtractionSettings settings) throws IOException {
        if (!Files.isRegularFile(settings.bzExecutable())) {
            throw new IOException("找不到 bz.exe：" + settings.bzExecutable());
        }
        if (!settings.bzExecutable().getFileName().toString().equalsIgnoreCase("bz.exe")) {
            throw new IOException("请选择 Bandizip 安装目录中的 bz.exe");
        }
        if (Files.exists(settings.outputRoot()) && !Files.isDirectory(settings.outputRoot())) {
            throw new IOException("解压根目录不是文件夹：" + settings.outputRoot());
        }
    }

    private String readOutputTail(InputStream inputStream) throws IOException {
        StringBuilder tail = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                tail.append(line).append(System.lineSeparator());
                if (tail.length() > MAX_MESSAGE_LENGTH * 2) {
                    tail.delete(0, tail.length() - MAX_MESSAGE_LENGTH);
                }
            }
        }
        return tail.toString();
    }

    private String sanitizeOutput(String output, char[] password) {
        String result = output == null ? "" : output.strip();
        if (password != null && password.length > 0) {
            result = result.replace(new String(password), "******");
        }
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
}
