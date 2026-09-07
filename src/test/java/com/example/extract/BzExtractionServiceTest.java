package com.example.extract;

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

            BzExtractionService service = new BzExtractionService();
            ExtractionSettings settings = new ExtractionSettings(
                    directory.resolve("bz.exe"), outputRoot, 3);
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
