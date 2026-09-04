package com.example.service;

import com.example.model.RenameItem;
import com.example.model.RenameStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class FileRenameServiceTest {
    private FileRenameServiceTest() {
    }

    public static void main(String[] args) throws IOException {
        Path testDirectory = Files.createTempDirectory("extension-renamer-test-");
        try {
            Path disguised = Files.writeString(testDirectory.resolve("demo.71z"), "original-content");
            Path protectedFile = Files.writeString(testDirectory.resolve("keep.7z"), "keep-content");
            Path conflictSource = Files.writeString(testDirectory.resolve("clash.txt"), "source-content");
            Path conflictTarget = Files.writeString(testDirectory.resolve("clash.zip"), "target-content");

            FileRenameService service = new FileRenameService();
            List<RenameItem> plan = service.createPlan(
                    List.of(disguised, protectedFile, conflictSource, conflictTarget), "zip");

            assertStatus(plan, "demo.71z", RenameStatus.READY);
            assertStatus(plan, "keep.7z", RenameStatus.UNCHANGED);
            assertStatus(plan, "clash.txt", RenameStatus.CONFLICT);
            assertStatus(plan, "clash.zip", RenameStatus.UNCHANGED);

            service.execute(plan);

            Path renamed = testDirectory.resolve("demo.zip");
            assertTrue(Files.exists(renamed), ".71z 文件应该被改名为 .zip");
            assertTrue(!Files.exists(disguised), "原 .71z 文件名不应该继续存在");
            assertEquals("original-content", Files.readString(renamed), "文件内容不应改变");
            assertTrue(Files.exists(protectedFile), "合法 .7z 文件应该保持不变");
            assertTrue(Files.exists(conflictSource), "发生冲突的源文件不应被改名");
            assertEquals("target-content", Files.readString(conflictTarget), "已有目标文件不应被覆盖");

            System.out.println("FileRenameService tests passed.");
        } finally {
            deleteTestDirectory(testDirectory);
        }
    }

    private static void assertStatus(List<RenameItem> items, String fileName, RenameStatus expected) {
        RenameStatus actual = items.stream()
                .filter(item -> item.source().getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing plan item: " + fileName))
                .status();
        if (actual != expected) {
            throw new AssertionError(fileName + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void deleteTestDirectory(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
