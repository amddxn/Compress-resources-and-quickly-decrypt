package app.archiverecovery.extract;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SevenZipExtractionServiceTest {
    private SevenZipExtractionServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("sevenzip-extraction-test-");
        try {
            Path zip = Files.writeString(directory.resolve("album.zip"), "zip-main");
            Path z01 = Files.writeString(directory.resolve("album.z01"), "zip-part");
            Path sevenZipOne = Files.writeString(directory.resolve("pack.7z.001"), "7z-one");
            Path sevenZipTwo = Files.writeString(directory.resolve("pack.7z.002"), "7z-two");
            Path rarOne = Files.writeString(directory.resolve("set.part1.rar"), "rar-one");
            Path rarTwo = Files.writeString(directory.resolve("set.part2.rar"), "rar-two");

            Path outputRoot = directory.resolve("output");
            ArchiveExtractionPlanner planner = new ArchiveExtractionPlanner();
            List<ExtractionTask> tasks = planner.createTasks(
                    List.of(zip, z01, sevenZipOne, sevenZipTwo, rarOne, rarTwo), outputRoot);

            assertEquals(3, tasks.size(), "每个分卷组只能生成一个解压任务");
            assertTask(tasks, "album.zip", "album");
            assertTask(tasks, "pack.7z.001", "pack");
            assertTask(tasks, "set.part1.rar", "set");

            Path nestedRoot = Files.createDirectories(directory.resolve("nested"));
            Path innerZip = Files.writeString(nestedRoot.resolve("inner.zip"), "zip-main");
            Files.writeString(nestedRoot.resolve("inner.z01"), "zip-volume");
            Path inner7z = Files.writeString(nestedRoot.resolve("deep.7z.001"), "7z-one");
            Files.writeString(nestedRoot.resolve("deep.7z.002"), "7z-two");
            Path innerRar = Files.writeString(nestedRoot.resolve("more.part1.rar"), "rar-one");
            Files.writeString(nestedRoot.resolve("more.part2.rar"), "rar-two");
            Files.writeString(nestedRoot.resolve("not-an-archive.pdf"), "ignored");
            List<ExtractionTask> nestedTasks = planner.createNestedTasks(List.of(nestedRoot), 1);
            assertEquals(3, nestedTasks.size(), "嵌套扫描必须识别普通包和分卷入口");
            assertTask(nestedTasks, innerZip.getFileName().toString(), "inner");
            assertTask(nestedTasks, inner7z.getFileName().toString(), "deep");
            assertTask(nestedTasks, innerRar.getFileName().toString(), "more");
            assertTrue(nestedTasks.stream().allMatch(item -> item.nestedDepth() == 1),
                    "嵌套任务必须记录正确层数");

            Path scatteredRoot = Files.createDirectories(directory.resolve("scattered"));
            Path folderA = Files.createDirectories(scatteredRoot.resolve("文件夹A"));
            Path folderB = Files.createDirectories(scatteredRoot.resolve("文件夹B"));
            Path folderC = Files.createDirectories(scatteredRoot.resolve("文件夹C"));
            Path spread7zOne = Files.write(folderA.resolve("跨目录.7z.001"),
                    new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C, 0x00});
            Path spread7zThree = Files.writeString(folderB.resolve("跨目录.7z.003"), "volume-three");
            Path spreadZipOne = Files.write(folderA.resolve("照片.z01"),
                    new byte[]{0x50, 0x4B, 0x07, 0x08, 0x00});
            Path spreadZipEntry = Files.write(folderC.resolve("照片.zip"),
                    new byte[]{0x01, 0x02, 0x50, 0x4B, 0x05, 0x06});
            Path spreadRarOne = Files.write(folderB.resolve("资料.part1.rar"),
                    new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00});
            Path spreadRarFour = Files.writeString(folderC.resolve("资料.part4.rar"), "volume-four");

            ArchiveExtractionPlanner.NestedArchivePlan scatteredPlan =
                    planner.createNestedPlan(List.of(scatteredRoot), 2);
            assertEquals(3, scatteredPlan.consolidatedGroupCount(),
                    "跨文件夹的 ZIP、7Z、RAR 分卷都应完成归组");
            assertEquals(6, scatteredPlan.movedVolumeCount(), "必须迁移每一组的所有分卷");
            assertEquals(3, scatteredPlan.tasks().size(), "整理后每组只能生成一个解压任务");
            assertTrue(scatteredPlan.warnings().isEmpty(), "有效分卷组不应产生警告");
            assertTrue(!Files.exists(spread7zOne) && !Files.exists(spread7zThree)
                            && !Files.exists(spreadZipOne) && !Files.exists(spreadZipEntry)
                            && !Files.exists(spreadRarOne) && !Files.exists(spreadRarFour),
                    "有效分卷必须从原子文件夹迁移出去");
            assertTrue(scatteredPlan.tasks().stream().allMatch(task ->
                            task.archive().getParent().getParent().getFileName().toString()
                                    .equals("_分卷整理")),
                    "整理后的入口卷必须位于独立的分卷整理目录");

            Path suspiciousRoot = Files.createDirectories(directory.resolve("suspicious"));
            Path suspiciousA = Files.createDirectories(suspiciousRoot.resolve("A"));
            Path suspiciousB = Files.createDirectories(suspiciousRoot.resolve("B"));
            Path fakeFirst = Files.writeString(suspiciousA.resolve("并非压缩包.7z.001"), "fake");
            Path fakeSecond = Files.writeString(suspiciousB.resolve("并非压缩包.7z.002"), "fake");
            ArchiveExtractionPlanner.NestedArchivePlan suspiciousPlan =
                    planner.createNestedPlan(List.of(suspiciousRoot), 2);
            assertEquals(0, suspiciousPlan.consolidatedGroupCount(),
                    "只有文件名、没有压缩格式特征的文件不能迁移");
            assertEquals(0, suspiciousPlan.movedVolumeCount(), "可疑文件不能被移动");
            assertTrue(!suspiciousPlan.warnings().isEmpty(), "拒绝迁移时必须给出警告");
            assertTrue(Files.exists(fakeFirst) && Files.exists(fakeSecond),
                    "拒绝迁移后原文件必须留在原处");

            Path duplicateRoot = Files.createDirectories(directory.resolve("duplicate"));
            Path duplicateA = Files.createDirectories(duplicateRoot.resolve("A"));
            Path duplicateB = Files.createDirectories(duplicateRoot.resolve("B"));
            Path duplicateOneA = Files.write(duplicateA.resolve("重号.7z.001"),
                    new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C});
            Path duplicateOneB = Files.write(duplicateB.resolve("重号.7z.001"),
                    new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C});
            ArchiveExtractionPlanner.NestedArchivePlan duplicatePlan =
                    planner.createNestedPlan(List.of(duplicateRoot), 2);
            assertEquals(0, duplicatePlan.movedVolumeCount(), "存在重复编号时不能迁移");
            assertTrue(!duplicatePlan.warnings().isEmpty(), "重复编号必须给出警告");
            assertTrue(Files.exists(duplicateOneA) && Files.exists(duplicateOneB),
                    "重复编号文件必须保留在原处");

            BundledSevenZip bundledSevenZip = new BundledSevenZip(directory.resolve("engine"));
            SevenZipExtractionService service = new SevenZipExtractionService(bundledSevenZip);
            ExtractionSettings settings = new ExtractionSettings(outputRoot, 3, 12);
            assertEquals(12, settings.maxNestedDepth(), "最大嵌套层数设置必须保留");
            ExtractionTask task = new ExtractionTask(zip, outputRoot.resolve("album"));
            ExtractionProgress halfway = new ExtractionProgress(
                    ExtractionProgress.Phase.EXTRACTING, 0, 2, 4, zip, "正在解压");
            assertEquals(50, halfway.percent(), "结构化解压进度必须计算正确百分比");
            ExtractionProgress exactProgress = new ExtractionProgress(
                    ExtractionProgress.Phase.EXTRACTING, 0, 0, 1, zip,
                    "正在解压", 37);
            assertTrue(exactProgress.hasExactPercentage(), "7-Zip 百分比必须标记为真实进度");
            assertEquals(37, exactProgress.percent(), "真实解压百分比必须优先显示");
            assertEquals(37, service.parseProgressPercent("Extracting  37%  demo.bin").orElse(-1),
                    "必须解析 7-Zip 的行进度");
            assertEquals(100, service.parseProgressPercent("[100%] done").orElse(-1),
                    "必须解析完成进度");
            assertTrue(service.parseProgressPercent("report100%.txt").isEmpty(),
                    "文件名中的百分号不能误报为解压进度");
            List<SevenZipExtractionService.SevenZipProcessProgress> streamedProgress =
                    new ArrayList<>();
            service.readOutputTail(new ByteArrayInputStream(
                            " 1%\r- first.bin\r 37%\r- second.bin\r 100%\r"
                                    .getBytes(StandardCharsets.UTF_8)),
                    streamedProgress::add);
            assertTrue(streamedProgress.stream().anyMatch(item -> item.percent() == 37),
                    "同一输出块中的实时百分比必须上报");
            assertTrue(streamedProgress.stream().anyMatch(item -> item.currentEntry().equals("second.bin")),
                    "必须上报 7-Zip 正在处理的文件名");
            List<String> command = service.createCommand(settings, task);
            assertTrue(command.contains("x"), "命令必须使用解压操作");
            assertTrue(command.contains("-aou"), "默认必须避免覆盖同名文件");
            assertTrue(command.contains("-bsp1"), "必须开启 7-Zip 的真实进度输出");
            assertTrue(command.contains("-bb1"), "必须开启当前文件输出");
            assertTrue(command.contains("-sccUTF-8"), "控制台输出必须使用 UTF-8");
            assertTrue(command.stream().noneMatch(argument -> argument.contains("秘密")),
                    "密码不能出现在进程命令行中");
            assertTrue(command.contains("-o" + task.outputDirectory()), "必须传递输出目录");
            assertEquals(zip.toString(), command.get(command.size() - 1), "最后一个参数必须是入口压缩包");
            assertTrue(service.isPasswordError("Enter password (will not be echoed):"),
                    "需要密码错误必须触发密码输入");
            assertTrue(service.isPasswordError("Cannot open encrypted archive. Wrong password?"),
                    "密码错误必须允许重新输入");
            assertTrue(!service.isPasswordError("CRC error"),
                    "普通解压错误不能误触发密码输入");

            verifyBundledEngineAndNestedExtraction(directory, bundledSevenZip);
            verifyPasswordIsSentThroughStandardInput(directory, bundledSevenZip);

            System.out.println("SevenZipExtractionService tests passed.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyBundledEngineAndNestedExtraction(
            Path directory, BundledSevenZip bundledSevenZip) throws Exception {
        Path executable = bundledSevenZip.executable();
        assertTrue(Files.isRegularFile(executable), "内置 7z.exe 必须可以释放到本地");
        assertTrue(Files.isRegularFile(executable.resolveSibling("7z.dll")),
                "内置 7z.dll 必须与可执行文件一起释放");
        assertTrue(Files.isRegularFile(bundledSevenZip.licenseFile()),
                "第三方许可文件必须随内置组件释放");
        assertTrue(Files.isRegularFile(bundledSevenZip.noticeFile()),
                "第三方声明必须随内置组件释放");

        Path source = Files.createDirectories(directory.resolve("integration-source"));
        Files.writeString(source.resolve("payload.txt"), "nested extraction works",
                StandardCharsets.UTF_8);
        runSevenZip(executable, source, "a", "-tzip", "inner.zip", "payload.txt");
        runSevenZip(executable, source, "a", "-t7z", "outer.7z", "inner.zip");

        Path extractionRoot = directory.resolve("integration-output");
        SevenZipExtractionService service = new SevenZipExtractionService(bundledSevenZip);
        List<ExtractionProgress> progressEvents = new ArrayList<>();
        ExtractionBatchResult result = service.extract(
                List.of(source.resolve("outer.7z")),
                new ExtractionSettings(extractionRoot, 1, 5),
                null, null, ignored -> {
                }, progressEvents::add);

        assertEquals(2L, result.successCount(), "首层与嵌套压缩包都必须解压成功");
        assertEquals(1, result.nestedArchiveCount(), "必须统计实际解压的嵌套压缩包");
        assertTrue(progressEvents.stream().anyMatch(event ->
                        event.phase() == ExtractionProgress.Phase.EXTRACTING
                                && event.nestedDepth() == 1),
                "嵌套层解压必须产生可见的结构化进度");
        assertTrue(progressEvents.stream().anyMatch(event -> event.archivePercent() == 100),
                "解压成功时必须明确上报 100% 进度");
        assertTrue(progressEvents.stream().anyMatch(event -> !event.currentEntry().isBlank()),
                "7-Zip 输出的当前文件名必须进入结构化进度");
        assertEquals("nested extraction works",
                Files.readString(extractionRoot.resolve("outer/inner/payload.txt"),
                        StandardCharsets.UTF_8),
                "嵌套压缩包内容必须实际写入下一层输出目录");
    }

    private static void verifyPasswordIsSentThroughStandardInput(
            Path directory, BundledSevenZip bundledSevenZip) throws Exception {
        Path source = Files.createDirectories(directory.resolve("password-source"));
        Files.writeString(source.resolve("secret.txt"), "private content", StandardCharsets.UTF_8);
        Path executable = bundledSevenZip.executable();
        runSevenZip(executable, source, "a", "-t7z", "-ptest-password", "-mhe=on",
                "encrypted.7z", "secret.txt");

        int[] passwordRequests = {0};
        ExtractionBatchResult result = new SevenZipExtractionService(bundledSevenZip).extract(
                List.of(source.resolve("encrypted.7z")),
                new ExtractionSettings(directory.resolve("password-output"), 1, 3),
                (archive, rejectedPassword, errorMessage) -> {
                    passwordRequests[0]++;
                    return java.util.Optional.of("test-password".toCharArray());
                }, ignored -> {
                });

        assertEquals(1L, result.successCount(), "通过标准输入提供密码后必须解压成功");
        assertEquals(1, passwordRequests[0], "加密压缩包应只提示一次正确密码");
        assertEquals("private content",
                Files.readString(directory.resolve("password-output/encrypted/secret.txt"),
                        StandardCharsets.UTF_8),
                "加密压缩包的文件内容必须正确");
    }

    private static void runSevenZip(Path executable, Path workingDirectory, String... arguments)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError("7-Zip 测试命令失败（" + exitCode + "）：" + output);
        }
    }

    private static void assertTask(List<ExtractionTask> tasks, String archiveName, String outputName) {
        ExtractionTask task = tasks.stream()
                .filter(item -> item.archive().getFileName().toString().equals(archiveName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing extraction task: " + archiveName));
        assertEquals(outputName, task.outputDirectory().getFileName().toString(),
                archiveName + " 的输出目录不正确");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }
}
