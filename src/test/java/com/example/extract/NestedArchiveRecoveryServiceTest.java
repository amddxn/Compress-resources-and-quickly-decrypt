package com.example.extract;

import com.example.service.RenameMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class NestedArchiveRecoveryServiceTest {
    private NestedArchiveRecoveryServiceTest() {
    }

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("nested-recovery-test-");
        try {
            NestedArchiveRecoveryService service = new NestedArchiveRecoveryService();

            Path disguisedNormal = Files.writeString(directory.resolve("内层资料.pdf"), "archive");
            NestedArchiveRecoveryService.RecoveryResult normal = service.recover(
                    directory, List.of(disguisedNormal), RenameMode.SEVEN_ZIP);
            assertEquals(1, normal.renamedCount(), "普通伪装压缩包必须恢复后缀");
            assertEquals(0, normal.movedCount(), "普通压缩包不应迁移目录");
            assertTrue(Files.exists(directory.resolve("内层资料.7z")),
                    "普通伪装压缩包目标名称错误");

            Path folderA = Files.createDirectories(directory.resolve("A"));
            Path folderB = Files.createDirectories(directory.resolve("B"));
            Path partOne = Files.writeString(folderA.resolve("分散包.001"), "one");
            Path partTwo = Files.writeString(folderB.resolve("分散包.002"), "two");
            NestedArchiveRecoveryService.RecoveryResult multipart = service.recover(
                    directory, List.of(partOne, partTwo), RenameMode.SEVEN_ZIP_MULTIPART);
            assertEquals(2, multipart.renamedCount(), "分卷伪装后缀必须全部恢复");
            assertEquals(2, multipart.movedCount(), "跨文件夹伪装分卷必须先集中迁移");
            assertTrue(multipart.warnings().isEmpty(), "正常恢复不应产生警告");
            Path recoveredOne = multipart.finalPaths().stream()
                    .filter(path -> path.getFileName().toString().equals("分散包.7z.001"))
                    .findFirst().orElseThrow(() -> new AssertionError("缺少恢复后的 7Z 首卷"));
            Path recoveredTwo = multipart.finalPaths().stream()
                    .filter(path -> path.getFileName().toString().equals("分散包.7z.002"))
                    .findFirst().orElseThrow(() -> new AssertionError("缺少恢复后的 7Z 第二卷"));
            assertEquals(recoveredOne.getParent(), recoveredTwo.getParent(),
                    "恢复后的分卷必须集中在同一个目录");
            assertTrue(!Files.exists(partOne) && !Files.exists(partTwo),
                    "迁移后原位置不应残留分卷");

            Path screenshotA = Files.createDirectories(directory.resolve("截图A"));
            Path screenshotB = Files.createDirectories(directory.resolve("截图B"));
            Path disguisedOne = Files.writeString(screenshotA.resolve("2.mp3"), "x".repeat(200));
            Path disguisedTwo = Files.writeString(screenshotB.resolve("2.7z.pdf"), "last");
            NestedArchiveRecoveryService.RecoveryResult screenshotCase = service.recover(
                    directory, List.of(disguisedOne, disguisedTwo),
                    RenameMode.SEVEN_ZIP_MULTIPART);
            assertEquals(2, screenshotCase.renamedCount(),
                    "无显式卷号的同组文件必须分别获得唯一卷号");
            List<String> screenshotTargets = screenshotCase.finalPaths().stream()
                    .map(path -> path.getFileName().toString())
                    .sorted().toList();
            assertEquals(List.of("2.7z.001", "2.7z.002"), screenshotTargets,
                    "同组基础名必须统一，不能生成重复 7z 或两个 001");
            assertTrue(screenshotTargets.stream().noneMatch(name -> name.contains(".7z.7z.")),
                    "目标名称不能重复添加 7z 后缀");
            Path recoveredScreenshotOne = screenshotCase.finalPaths().stream()
                    .filter(path -> path.getFileName().toString().equals("2.7z.001"))
                    .findFirst().orElseThrow();
            assertEquals("x".repeat(200), Files.readString(recoveredScreenshotOne),
                    "没有卷号线索时，较大的常规分卷应排在较小末卷之前");

            Path zipA = Files.createDirectories(directory.resolve("ZIP截图A"));
            Path zipB = Files.createDirectories(directory.resolve("ZIP截图B"));
            Path zipC = Files.createDirectories(directory.resolve("ZIP截图C"));
            Path misleadingRar = Files.writeString(zipA.resolve("7月21.rar"), "a".repeat(300));
            Path disturbedZipTwo = Files.writeString(zipB.resolve("7月21.z0啊啊2"), "b".repeat(300));
            Path disturbedZipLast = Files.writeString(zipC.resolve("7月21.zissp"), "c".repeat(100));
            NestedArchiveRecoveryService.RecoveryResult zipOverride = service.recover(
                    directory, List.of(misleadingRar, disturbedZipTwo, disturbedZipLast),
                    RenameMode.ZIP_MULTIPART);
            assertEquals(3, zipOverride.renamedCount(),
                    "选择 ZIP 分卷时必须覆盖看似正常的 RAR 后缀");
            assertEquals(List.of("7月21.z01", "7月21.z02", "7月21.zip"),
                    zipOverride.finalPaths().stream()
                            .map(path -> path.getFileName().toString()).sorted().toList(),
                    "所选文件必须统一恢复为 ZIP 官方分卷格式");
            Path zipEntry = findByName(zipOverride.finalPaths(), "7月21.zip");
            Path zipVolumeTwo = findByName(zipOverride.finalPaths(), "7月21.z01");
            Path zipVolumeThree = findByName(zipOverride.finalPaths(), "7月21.z02");
            assertEquals("c".repeat(100), Files.readString(zipEntry),
                    "zissp 必须优先识别为受干扰的 .zip 入口卷");
            assertEquals("a".repeat(300), Files.readString(zipVolumeTwo),
                    "其他卷已占用时，RAR 伪装文件必须补入缺失的 .z01");
            assertEquals("b".repeat(300), Files.readString(zipVolumeThree),
                    "z0啊啊2 必须识别为受干扰的 .z02");

            Path misleadingNormal = Files.writeString(directory.resolve("普通伪装.rar"), "zip-data");
            NestedArchiveRecoveryService.RecoveryResult normalOverride = service.recover(
                    directory, List.of(misleadingNormal), RenameMode.ZIP);
            assertEquals(1, normalOverride.renamedCount(),
                    "普通模式也必须允许用户覆盖其他标准压缩后缀");
            assertTrue(Files.exists(directory.resolve("普通伪装.zip")),
                    "RAR 外观文件选择 ZIP 后必须改为 .zip");

            Path outside = Files.createTempFile("outside-recovery-test-", ".pdf");
            try {
                NestedArchiveRecoveryService.RecoveryResult rejected = service.recover(
                        directory, List.of(outside), RenameMode.ZIP);
                assertEquals(0, rejected.renamedCount(), "不能修改当前解压目录之外的文件");
                assertTrue(!rejected.warnings().isEmpty(), "越界选择必须给出警告");
                assertTrue(Files.exists(outside), "越界文件必须保持原样");
            } finally {
                Files.deleteIfExists(outside);
            }

            System.out.println("NestedArchiveRecoveryService tests passed.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static Path findByName(List<Path> paths, String fileName) {
        return paths.stream()
                .filter(path -> path.getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing file: " + fileName));
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }
}
