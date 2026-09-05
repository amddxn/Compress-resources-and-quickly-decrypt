package com.example.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ArchiveNameDetectorTest {
    private ArchiveNameDetectorTest() {
    }

    public static void main(String[] args) {
        List<Path> paths = List.of(
                Path.of("movie.7z.001"), Path.of("movie.7z.002"),
                Path.of("backup.zip.001"), Path.of("backup.zip.002"),
                Path.of("photos.z01"), Path.of("photos.z02"), Path.of("photos.zip"),
                Path.of("data.part001.rar"), Path.of("data.part002.rar"),
                Path.of("part1.rar"), Path.of("part2.rar"),
                Path.of("legacy.rar"), Path.of("legacy.r00"), Path.of("legacy.r01"),
                Path.of("restore.001"), Path.of("restore.003"), Path.of("restore.005"),
                Path.of("nostart.003"), Path.of("nostart.005"),
                Path.of("lonely.001"),
                Path.of("normal.zip"), Path.of("normal.7z"), Path.of("normal.rar"),
                Path.of("wrong.71z.001"), Path.of("wrong.z1"), Path.of("wrong.r1"),
                Path.of("wrong.part1.rar1"), Path.of("wrong.7z1")
        );

        Map<Path, ArchiveNameDetector.Detection> detections = new ArchiveNameDetector().detect(paths);
        assertMultipart(detections, "movie.7z.001", "7Z 分卷");
        assertMultipart(detections, "backup.zip.002", "ZIP 数字分卷");
        assertMultipart(detections, "photos.z01", "ZIP 分卷");
        assertMultipart(detections, "photos.zip", "ZIP 分卷（末卷）");
        assertMultipart(detections, "data.part001.rar", "RAR 分卷");
        assertMultipart(detections, "part2.rar", "RAR 分卷");
        assertMultipart(detections, "legacy.r00", "RAR 旧式分卷");
        assertMultipart(detections, "legacy.rar", "RAR 旧式分卷（首卷）");
        assertConvertibleMultipart(detections, "restore.001", "restore.7z.001", "7z");
        assertConvertibleMultipart(detections, "restore.003", "restore.zip.003", "zip");
        assertConvertibleMultipart(detections, "restore.005", "restore.part5.rar", "rar");
        assertConvertibleMultipart(detections, "nostart.003", "nostart.7z.003", "7z");
        assertConvertibleMultipart(detections, "nostart.005", "nostart.zip.005", "zip");

        assertStandard(detections, "normal.zip", "普通 ZIP");
        assertStandard(detections, "normal.7z", "普通 7Z");
        assertStandard(detections, "normal.rar", "普通 RAR");

        assertInvalid(detections, "wrong.71z.001");
        assertInvalid(detections, "wrong.z1");
        assertInvalid(detections, "wrong.r1");
        assertInvalid(detections, "wrong.part1.rar1");
        assertInvalid(detections, "wrong.7z1");
        assertInvalid(detections, "lonely.001");
        System.out.println("ArchiveNameDetector tests passed.");
    }

    private static void assertMultipart(Map<Path, ArchiveNameDetector.Detection> detections,
                                        String name, String expectedText) {
        ArchiveNameDetector.Detection detection = get(detections, name);
        if (!detection.protectedName() || !detection.multipart()
                || !detection.description().contains(expectedText)) {
            throw new AssertionError(name + " should be detected as " + expectedText + ": " + detection);
        }
    }

    private static void assertStandard(Map<Path, ArchiveNameDetector.Detection> detections,
                                       String name, String expectedText) {
        ArchiveNameDetector.Detection detection = get(detections, name);
        if (!detection.protectedName() || detection.multipart()
                || !detection.description().contains(expectedText)) {
            throw new AssertionError(name + " should be detected as " + expectedText + ": " + detection);
        }
    }

    private static void assertConvertibleMultipart(Map<Path, ArchiveNameDetector.Detection> detections,
                                                   String name, String expectedTarget, String targetExtension) {
        ArchiveNameDetector.Detection detection = get(detections, name);
        Path source = Path.of(name).toAbsolutePath().normalize();
        Path actualTarget = detection.targetPath(source, targetExtension);
        if (detection.protectedName() || !detection.multipart()
                || !actualTarget.getFileName().toString().equals(expectedTarget)) {
            throw new AssertionError(name + " should become " + expectedTarget + ": " + detection);
        }
    }

    private static void assertInvalid(Map<Path, ArchiveNameDetector.Detection> detections, String name) {
        ArchiveNameDetector.Detection detection = get(detections, name);
        if (detection.protectedName() || detection.multipart()) {
            throw new AssertionError(name + " must not be detected as a valid archive name: " + detection);
        }
    }

    private static ArchiveNameDetector.Detection get(
            Map<Path, ArchiveNameDetector.Detection> detections, String name) {
        return detections.get(Path.of(name).toAbsolutePath().normalize());
    }
}
