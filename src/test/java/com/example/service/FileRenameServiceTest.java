package com.example.service;

import com.example.model.RenameItem;
import com.example.model.RenameStatus;

import java.io.IOException;
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
            Path volumeOne = Files.writeString(testDirectory.resolve("volume.001"), "volume-one");
            Path volumeTwo = Files.writeString(testDirectory.resolve("volume.003"), "volume-three");

            FileRenameService service = new FileRenameService();
            List<RenameItem> plan = service.createPlan(
                    List.of(disguised, protectedFile, conflictSource, conflictTarget, volumeOne, volumeTwo), "zip");

            assertStatus(plan, "demo.71z", RenameStatus.READY);
            assertStatus(plan, "keep.7z", RenameStatus.UNCHANGED);
            assertStatus(plan, "clash.txt", RenameStatus.CONFLICT);
            assertStatus(plan, "clash.zip", RenameStatus.UNCHANGED);
            assertStatus(plan, "volume.001", RenameStatus.READY);
            assertStatus(plan, "volume.003", RenameStatus.READY);

            service.execute(plan);

            Path renamed = testDirectory.resolve("demo.zip");
            assertTrue(Files.exists(renamed), ".71z 文件应该被改名为 .zip");
            assertTrue(!Files.exists(disguised), "原 .71z 文件名不应该继续存在");
            assertEquals("original-content", Files.readString(renamed), "文件内容不应改变");
            assertTrue(Files.exists(protectedFile), "合法 .7z 文件应该保持不变");
            assertTrue(Files.exists(conflictSource), "发生冲突的源文件不应被改名");
            assertEquals("target-content", Files.readString(conflictTarget), "已有目标文件不应被覆盖");
            assertEquals("volume-one", Files.readString(testDirectory.resolve("volume.zip.001")),
                    "第一卷应保留编号并修改格式名称");
            assertEquals("volume-three", Files.readString(testDirectory.resolve("volume.zip.003")),
                    "不连续分卷应保留原编号并修改格式名称");

            verifyExplicitMultipartModes(testDirectory, service);
            verifyOrdinaryArchivesRemainUnchanged(testDirectory, service);

            System.out.println("FileRenameService tests passed.");
        } finally {
            deleteTestDirectory(testDirectory);
        }
    }

    private static void verifyOrdinaryArchivesRemainUnchanged(Path directory,
                                                              FileRenameService service) throws IOException {
        Path ordinaryDirectory = Files.createDirectories(directory.resolve("ordinary-archives"));
        Path zip = Files.writeString(ordinaryDirectory.resolve("normal.zip"), "zip");
        Path sevenZip = Files.writeString(ordinaryDirectory.resolve("normal.7z"), "7z");
        Path rar = Files.writeString(ordinaryDirectory.resolve("normal.rar"), "rar");

        assertStatus(service.createPlan(List.of(zip), RenameMode.ZIP_MULTIPART),
                "normal.zip", RenameStatus.UNCHANGED);
        assertStatus(service.createPlan(List.of(sevenZip), RenameMode.SEVEN_ZIP_MULTIPART),
                "normal.7z", RenameStatus.UNCHANGED);
        assertStatus(service.createPlan(List.of(rar), RenameMode.RAR_MULTIPART),
                "normal.rar", RenameStatus.UNCHANGED);
    }

    private static void verifyExplicitMultipartModes(Path directory, FileRenameService service) throws IOException {
        Path screenshotSource = Files.writeString(directory.resolve("6月新补.MP3"), "first-volume");
        Path screenshotVolumeTwo = Files.writeString(directory.resolve("6月新补.7z.002"), "second-volume");
        List<RenameItem> sevenZipPlan = service.createPlan(
                List.of(screenshotSource, screenshotVolumeTwo), RenameMode.SEVEN_ZIP_MULTIPART);
        assertTarget(sevenZipPlan, "6月新补.MP3", "6月新补.7z.001");
        assertStatus(sevenZipPlan, "6月新补.7z.002", RenameStatus.UNCHANGED);
        service.execute(sevenZipPlan);
        assertEquals("first-volume", Files.readString(directory.resolve("6月新补.7z.001")),
                "截图场景应恢复为 7z.001");

        Path previouslyRenamed = Files.writeString(directory.resolve("旧版结果.7z"), "old-first-volume");
        Path previousVolumeTwo = Files.writeString(directory.resolve("旧版结果.7z.002"), "old-second-volume");
        List<RenameItem> correctionPlan = service.createPlan(
                List.of(previouslyRenamed, previousVolumeTwo), RenameMode.SEVEN_ZIP_MULTIPART);
        assertTarget(correctionPlan, "旧版结果.7z", "旧版结果.7z.001");

        Path zipSource = Files.writeString(directory.resolve("photos.bin"), "zip-missing-volume");
        Path zipOne = Files.writeString(directory.resolve("photos.zip.001"), "zip-one");
        Path zipThree = Files.writeString(directory.resolve("photos.zip.003"), "zip-three");
        List<RenameItem> zipPlan = service.createPlan(
                List.of(zipSource, zipOne, zipThree), RenameMode.ZIP_MULTIPART);
        assertTarget(zipPlan, "photos.bin", "photos.zip.002");

        Path rarSource = Files.writeString(directory.resolve("package.data"), "rar-missing-volume");
        Path rarOne = Files.writeString(directory.resolve("package.part001.rar"), "rar-one");
        Path rarFive = Files.writeString(directory.resolve("package.part005.rar"), "rar-five");
        List<RenameItem> rarPlan = service.createPlan(
                List.of(rarSource, rarOne, rarFive), RenameMode.RAR_MULTIPART);
        assertTarget(rarPlan, "package.data", "package.part002.rar");

        verifyMultipartBaseNameResolution(directory, service);
    }

    private static void verifyMultipartBaseNameResolution(Path directory,
                                                          FileRenameService service) throws IOException {
        Path caseOne = Files.createDirectories(directory.resolve("case-one"));
        Path embeddedFormat = Files.writeString(caseOne.resolve("2025.06.7z.mp3"), "first");
        Path embeddedFormatVolume = Files.writeString(caseOne.resolve("2025.06.7z.002"), "second");
        List<RenameItem> embeddedFormatPlan = service.createPlan(
                List.of(embeddedFormat, embeddedFormatVolume), RenameMode.SEVEN_ZIP_MULTIPART);
        assertTarget(embeddedFormatPlan, "2025.06.7z.mp3", "2025.06.7z.001");

        Path caseTwo = Files.createDirectories(directory.resolve("case-two"));
        Path noExtension = Files.writeString(caseTwo.resolve("2025.06"), "no-extension");
        Path sameBaseVolume = Files.writeString(caseTwo.resolve("2025.06.7z.002"), "second");
        List<RenameItem> sameBasePlan = service.createPlan(
                List.of(noExtension, sameBaseVolume), RenameMode.SEVEN_ZIP_MULTIPART);
        assertTarget(sameBasePlan, "2025.06", "2025.06.7z.001");

        Path caseThree = Files.createDirectories(directory.resolve("case-three"));
        Path realExtension = Files.writeString(caseThree.resolve("2025.06"), "has-extension");
        Path differentBaseVolume = Files.writeString(caseThree.resolve("2025.05.7z.002"), "different");
        List<RenameItem> differentBasePlan = service.createPlan(
                List.of(realExtension, differentBaseVolume), RenameMode.SEVEN_ZIP_MULTIPART);
        assertTarget(differentBasePlan, "2025.06", "2025.7z.001");

        Path caseFour = Files.createDirectories(directory.resolve("case-four"));
        Path thirdVolume = Files.writeString(caseFour.resolve("1"), "third");
        Path firstVolume = Files.writeString(caseFour.resolve("1.7z.001"), "first");
        Path secondVolume = Files.writeString(caseFour.resolve("1.7z.002"), "second");
        List<RenameItem> occupiedPlan = service.createPlan(
                List.of(firstVolume, secondVolume, thirdVolume), RenameMode.SEVEN_ZIP_MULTIPART);
        assertTarget(occupiedPlan, "1", "1.7z.003");

        verifyZipMultipartRules(directory, service);
        verifyRarMultipartRules(directory, service);
    }

    private static void verifyZipMultipartRules(Path directory, FileRenameService service) throws IOException {
        Path zipCase = Files.createDirectories(directory.resolve("zip-case"));
        Path fake = Files.writeString(zipCase.resolve("2025.06.zip.mp3"), "first");
        Path second = Files.writeString(zipCase.resolve("2025.06.zip.002"), "second");
        List<RenameItem> plan = service.createPlan(List.of(fake, second), RenameMode.ZIP_MULTIPART);
        assertTarget(plan, "2025.06.zip.mp3", "2025.06.zip.001");

        Path occupiedCase = Files.createDirectories(directory.resolve("zip-occupied"));
        Path unnumbered = Files.writeString(occupiedCase.resolve("1"), "third");
        Path first = Files.writeString(occupiedCase.resolve("1.zip.001"), "first");
        Path secondPart = Files.writeString(occupiedCase.resolve("1.zip.002"), "second");
        List<RenameItem> occupiedPlan = service.createPlan(
                List.of(first, secondPart, unnumbered), RenameMode.ZIP_MULTIPART);
        assertTarget(occupiedPlan, "1", "1.zip.003");

        Path legacyCase = Files.createDirectories(directory.resolve("zip-legacy"));
        Path legacyFake = Files.writeString(legacyCase.resolve("legacy.bin"), "missing");
        Path legacySecond = Files.writeString(legacyCase.resolve("legacy.z02"), "second");
        Path legacyLast = Files.writeString(legacyCase.resolve("legacy.zip"), "last");
        List<RenameItem> legacyPlan = service.createPlan(
                List.of(legacyFake, legacySecond, legacyLast), RenameMode.ZIP_MULTIPART);
        assertTarget(legacyPlan, "legacy.bin", "legacy.z01");
    }

    private static void verifyRarMultipartRules(Path directory, FileRenameService service) throws IOException {
        Path rarCase = Files.createDirectories(directory.resolve("rar-case"));
        Path fake = Files.writeString(rarCase.resolve("2025.06.rar.mp3"), "first");
        Path second = Files.writeString(rarCase.resolve("2025.06.part002.rar"), "second");
        List<RenameItem> plan = service.createPlan(List.of(fake, second), RenameMode.RAR_MULTIPART);
        assertTarget(plan, "2025.06.rar.mp3", "2025.06.part001.rar");

        Path occupiedCase = Files.createDirectories(directory.resolve("rar-occupied"));
        Path unnumbered = Files.writeString(occupiedCase.resolve("1"), "third");
        Path first = Files.writeString(occupiedCase.resolve("1.part001.rar"), "first");
        Path secondPart = Files.writeString(occupiedCase.resolve("1.part002.rar"), "second");
        List<RenameItem> occupiedPlan = service.createPlan(
                List.of(first, secondPart, unnumbered), RenameMode.RAR_MULTIPART);
        assertTarget(occupiedPlan, "1", "1.part003.rar");

        Path legacyCase = Files.createDirectories(directory.resolve("rar-legacy"));
        Path legacyFake = Files.writeString(legacyCase.resolve("legacy.bin"), "missing");
        Path legacyFirst = Files.writeString(legacyCase.resolve("legacy.rar"), "first");
        Path legacySecond = Files.writeString(legacyCase.resolve("legacy.r00"), "second");
        List<RenameItem> legacyPlan = service.createPlan(
                List.of(legacyFake, legacyFirst, legacySecond), RenameMode.RAR_MULTIPART);
        assertTarget(legacyPlan, "legacy.bin", "legacy.r01");
    }

    private static void assertTarget(List<RenameItem> items, String sourceName, String expectedTargetName) {
        String actual = items.stream()
                .filter(item -> item.source().getFileName().toString().equals(sourceName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing plan item: " + sourceName))
                .target().getFileName().toString();
        if (!actual.equals(expectedTargetName)) {
            throw new AssertionError(sourceName + " expected target " + expectedTargetName + " but was " + actual);
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
