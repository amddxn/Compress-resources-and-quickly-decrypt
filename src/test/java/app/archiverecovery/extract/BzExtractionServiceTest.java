package app.archiverecovery.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class BzExtractionServiceTest {
    private BzExtractionServiceTest() {
    }

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("bz-extraction-test-");
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

            BzExtractionService service = new BzExtractionService();
            ExtractionSettings settings = new ExtractionSettings(
                    directory.resolve("bz.exe"), outputRoot, 3, 12);
            assertEquals(12, settings.maxNestedDepth(), "最大嵌套层数设置必须保留");
            ExtractionTask task = new ExtractionTask(zip, outputRoot.resolve("album"));
            List<String> command = service.createCommand(settings, task, "秘密".toCharArray());
            assertTrue(command.contains("x"), "命令必须使用解压操作");
            assertTrue(command.contains("-consolemode:utf8"), "控制台输出必须使用 UTF-8");
            assertTrue(command.contains("-aou"), "默认必须避免覆盖同名文件");
            assertTrue(command.contains("-p:秘密"), "密码必须传给 bz.exe");
            assertTrue(command.contains("-o:" + task.outputDirectory()), "必须传递输出目录");
            assertEquals(zip.toString(), command.get(command.size() - 1), "最后一个参数必须是入口压缩包");
            assertTrue(service.isPasswordError("0xa0000020: Password is needed"),
                    "需要密码错误必须触发密码输入");
            assertTrue(service.isPasswordError("0xa0000021: Invalid Password"),
                    "密码错误必须允许重新输入");
            assertTrue(service.isPasswordError("ERROR: Password required"),
                    "新版 bz.exe 的密码提示必须触发密码输入");
            assertTrue(!service.isPasswordError("CRC error"),
                    "普通解压错误不能误触发密码输入");

            System.out.println("BzExtractionService tests passed.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
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
