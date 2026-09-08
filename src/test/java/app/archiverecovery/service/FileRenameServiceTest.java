package app.archiverecovery.service;

import app.archiverecovery.model.RenameItem;
import app.archiverecovery.model.RenameStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class FileRenameServiceTest {
    private FileRenameServiceTest() {
    }

    public static void main(String[] args) throws IOException {
        Path testDirectory = Files.createTempDirectory("archive-recovery-assistant-test-");
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
            verifyNormalAndMultipartModesAreSeparated(testDirectory, service);
            verifyNoisyMultipartSuffixRecovery(testDirectory, service);
            verifyDisturbedOrdinalsAndIndependentGroups(testDirectory, service);

            System.out.println("FileRenameService tests passed.");
        } finally {
            deleteTestDirectory(testDirectory);
        }
    }

    private static void verifyNoisyMultipartSuffixRecovery(Path directory,
                                                            FileRenameService service) throws IOException {
        Path sevenZipCase = Files.createDirectories(directory.resolve("noisy-7z"));
        Path noisy7zTwo = Files.writeString(sevenZipCase.resolve("2025.06.7z.321.002"), "two");
        Path noisy7zOne = Files.writeString(sevenZipCase.resolve("2025.06.71z.001"), "one");
        Path unnumbered7z = Files.writeString(sevenZipCase.resolve("2025.06.7z"), "three");
        List<RenameItem> sevenZipPlan = service.createPlan(
                List.of(noisy7zTwo, noisy7zOne, unnumbered7z), RenameMode.SEVEN_ZIP_MULTIPART);
        assertTarget(sevenZipPlan, "2025.06.7z.321.002", "2025.06.7z.002");
        assertTarget(sevenZipPlan, "2025.06.71z.001", "2025.06.7z.001");
        assertTarget(sevenZipPlan, "2025.06.7z", "2025.06.7z.003");

        Path zipCase = Files.createDirectories(directory.resolve("noisy-zip"));
        Path noisyZipTwo = Files.writeString(zipCase.resolve("2025.06.zip.321.002"), "two");
        Path noisyZipOne = Files.writeString(zipCase.resolve("2025.06.z1ip.001"), "one");
        Path unnumberedZip = Files.writeString(zipCase.resolve("2025.06.zip"), "three");
        List<RenameItem> zipPlan = service.createPlan(
                List.of(noisyZipTwo, noisyZipOne, unnumberedZip), RenameMode.ZIP_MULTIPART);
        assertTarget(zipPlan, "2025.06.zip.321.002", "2025.06.z01");
        assertTarget(zipPlan, "2025.06.z1ip.001", "2025.06.z02");
        assertStatus(zipPlan, "2025.06.zip", RenameStatus.UNCHANGED);

        Path rarCase = Files.createDirectories(directory.resolve("noisy-rar"));
        Path noisyRarOne = Files.writeString(rarCase.resolve("2025.06.part001.r1ar"), "one");
        Path noisyRarTwo = Files.writeString(rarCase.resolve("2025.06.part002.rar321"), "two");
        Path unnumberedRar = Files.writeString(rarCase.resolve("2025.06.rar"), "three");
        List<RenameItem> rarPlan = service.createPlan(
                List.of(noisyRarOne, noisyRarTwo, unnumberedRar), RenameMode.RAR_MULTIPART);
        assertTarget(rarPlan, "2025.06.part001.r1ar", "2025.06.part1.rar");
        assertTarget(rarPlan, "2025.06.part002.rar321", "2025.06.part2.rar");
        assertTarget(rarPlan, "2025.06.rar", "2025.06.part3.rar");
    }

    private static void verifyNormalAndMultipartModesAreSeparated(Path directory,
                                                                  FileRenameService service) throws IOException {
        Path ordinaryDirectory = Files.createDirectories(directory.resolve("ordinary-archives"));
        Path zip = Files.writeString(ordinaryDirectory.resolve("normal.zip"), "zip");
        Path sevenZip = Files.writeString(ordinaryDirectory.resolve("normal.7z"), "7z");
        Path rar = Files.writeString(ordinaryDirectory.resolve("normal.rar"), "rar");

        assertStatus(service.createPlan(List.of(zip), RenameMode.ZIP),
                "normal.zip", RenameStatus.UNCHANGED);
        assertStatus(service.createPlan(List.of(sevenZip), RenameMode.SEVEN_ZIP),
                "normal.7z", RenameStatus.UNCHANGED);
        assertStatus(service.createPlan(List.of(rar), RenameMode.RAR),
                "normal.rar", RenameStatus.UNCHANGED);

        assertStatus(service.createPlan(List.of(zip), RenameMode.ZIP_MULTIPART),
                "normal.zip", RenameStatus.UNCHANGED);
        assertTarget(service.createPlan(List.of(sevenZip), RenameMode.SEVEN_ZIP_MULTIPART),
                "normal.7z", "normal.7z.001");
        assertTarget(service.createPlan(List.of(rar), RenameMode.RAR_MULTIPART),
                "normal.rar", "normal.part1.rar");
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
        assertTarget(zipPlan, "photos.bin", "photos.z01");
        assertTarget(zipPlan, "photos.zip.001", "photos.zip");
        assertTarget(zipPlan, "photos.zip.003", "photos.z02");

        Path rarSource = Files.writeString(directory.resolve("package.data"), "rar-missing-volume");
        Path rarOne = Files.writeString(directory.resolve("package.part001.rar"), "rar-one");
        Path rarFive = Files.writeString(directory.resolve("package.part005.rar"), "rar-five");
        List<RenameItem> rarPlan = service.createPlan(
                List.of(rarSource, rarOne, rarFive), RenameMode.RAR_MULTIPART);
        assertTarget(rarPlan, "package.data", "package.part2.rar");

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
        assertTarget(plan, "2025.06.zip.mp3", "2025.06.zip");

        Path occupiedCase = Files.createDirectories(directory.resolve("zip-occupied"));
        Path unnumbered = Files.writeString(occupiedCase.resolve("1"), "third");
        Path first = Files.writeString(occupiedCase.resolve("1.zip.001"), "first");
        Path secondPart = Files.writeString(occupiedCase.resolve("1.zip.002"), "second");
        List<RenameItem> occupiedPlan = service.createPlan(
                List.of(first, secondPart, unnumbered), RenameMode.ZIP_MULTIPART);
        assertTarget(occupiedPlan, "1", "1.z02");

        Path legacyCase = Files.createDirectories(directory.resolve("zip-legacy"));
        Path legacyFake = Files.writeString(legacyCase.resolve("legacy.bin"), "missing");
        Path legacySecond = Files.writeString(legacyCase.resolve("legacy.z02"), "second");
        Path legacyLast = Files.writeString(legacyCase.resolve("legacy.zip"), "last");
        List<RenameItem> legacyPlan = service.createPlan(
                List.of(legacyFake, legacySecond, legacyLast), RenameMode.ZIP_MULTIPART);
        assertTarget(legacyPlan, "legacy.bin", "legacy.z01");
        assertStatus(legacyPlan, "legacy.zip", RenameStatus.UNCHANGED);
    }

    private static void verifyRarMultipartRules(Path directory, FileRenameService service) throws IOException {
        Path rarCase = Files.createDirectories(directory.resolve("rar-case"));
        Path fake = Files.writeString(rarCase.resolve("2025.06.rar.mp3"), "first");
        Path second = Files.writeString(rarCase.resolve("2025.06.part002.rar"), "second");
        List<RenameItem> plan = service.createPlan(List.of(fake, second), RenameMode.RAR_MULTIPART);
        assertTarget(plan, "2025.06.rar.mp3", "2025.06.part1.rar");

        Path occupiedCase = Files.createDirectories(directory.resolve("rar-occupied"));
        Path unnumbered = Files.writeString(occupiedCase.resolve("1"), "third");
        Path first = Files.writeString(occupiedCase.resolve("1.part001.rar"), "first");
        Path secondPart = Files.writeString(occupiedCase.resolve("1.part002.rar"), "second");
        List<RenameItem> occupiedPlan = service.createPlan(
                List.of(first, secondPart, unnumbered), RenameMode.RAR_MULTIPART);
        assertTarget(occupiedPlan, "1", "1.part3.rar");

        Path legacyCase = Files.createDirectories(directory.resolve("rar-legacy"));
        Path legacyFake = Files.writeString(legacyCase.resolve("legacy.bin"), "missing");
        Path legacyFirst = Files.writeString(legacyCase.resolve("legacy.rar"), "first");
        Path legacySecond = Files.writeString(legacyCase.resolve("legacy.r00"), "second");
        List<RenameItem> legacyPlan = service.createPlan(
                List.of(legacyFake, legacyFirst, legacySecond), RenameMode.RAR_MULTIPART);
        assertTarget(legacyPlan, "legacy.bin", "legacy.part1.rar");

        Path screenshotCase = Files.createDirectories(directory.resolve("rar-extra-suffix"));
        Path screenshotOne = Files.writeString(screenshotCase.resolve("6月新补.rar.001"), "one");
        Path screenshotTwo = Files.writeString(screenshotCase.resolve("6月新补.rar.002"), "two");
        List<RenameItem> screenshotPlan = service.createPlan(
                List.of(screenshotOne, screenshotTwo), RenameMode.RAR_MULTIPART);
        assertTarget(screenshotPlan, "6月新补.rar.001", "6月新补.part1.rar");
        assertTarget(screenshotPlan, "6月新补.rar.002", "6月新补.part2.rar");
    }

    private static void verifyDisturbedOrdinalsAndIndependentGroups(Path directory,
                                                                     FileRenameService service) throws IOException {
        Path screenshotCase = Files.createDirectories(directory.resolve("disturbed-rar-screenshot"));
        Path first = Files.writeString(screenshotCase.resolve("6月新补.ra1r.001"), "one");
        Path second = Files.writeString(screenshotCase.resolve("6月新补.1ra3r3.002"), "two");
        List<RenameItem> screenshotPlan = service.createPlan(
                List.of(first, second), RenameMode.RAR_MULTIPART);
        assertTarget(screenshotPlan, "6月新补.ra1r.001", "6月新补.part1.rar");
        assertTarget(screenshotPlan, "6月新补.1ra3r3.002", "6月新补.part2.rar");

        Path noisyCase = Files.createDirectories(directory.resolve("disturbed-ordinal-forms"));
        Path alphaOne = Files.writeString(noisyCase.resolve("alpha.0s0c1"), "one");
        Path alphaTwo = Files.writeString(noisyCase.resolve("alpha.0卡0啊s2"), "two");
        List<RenameItem> alphaPlan = service.createPlan(
                List.of(alphaOne, alphaTwo), RenameMode.SEVEN_ZIP_MULTIPART);
        assertTarget(alphaPlan, "alpha.0s0c1", "alpha.7z.001");
        assertTarget(alphaPlan, "alpha.0卡0啊s2", "alpha.7z.002");

        Path betaOne = Files.writeString(noisyCase.resolve("beta.12part1"), "one");
        Path betaTwo = Files.writeString(noisyCase.resolve("beta.part2sad"), "two");
        List<RenameItem> betaPlan = service.createPlan(
                List.of(betaOne, betaTwo), RenameMode.RAR_MULTIPART);
        assertTarget(betaPlan, "beta.12part1", "beta.part1.rar");
        assertTarget(betaPlan, "beta.part2sad", "beta.part2.rar");

        Path gammaOne = Files.writeString(noisyCase.resolve("gamma.z443101"), "one");
        Path gammaTwo = Files.writeString(noisyCase.resolve("gamma.zsdf02"), "two");
        List<RenameItem> gammaPlan = service.createPlan(
                List.of(gammaOne, gammaTwo), RenameMode.ZIP_MULTIPART);
        assertTarget(gammaPlan, "gamma.z443101", "gamma.z01");
        assertTarget(gammaPlan, "gamma.zsdf02", "gamma.z02");

        Path screenshotZip = Files.createDirectories(directory.resolve("zip-disturbed-priority"));
        Path disguisedSecond = Files.writeString(screenshotZip.resolve("7月21.rar"), "second");
        Path disturbedThird = Files.writeString(screenshotZip.resolve("7月21.z0啊啊2"), "third");
        Path disturbedEntry = Files.writeString(screenshotZip.resolve("7月21.zissp"), "first");
        List<RenameItem> disturbedZipPlan = service.createPlan(
                List.of(disguisedSecond, disturbedThird, disturbedEntry), RenameMode.ZIP_MULTIPART);
        assertTarget(disturbedZipPlan, "7月21.zissp", "7月21.zip");
        assertTarget(disturbedZipPlan, "7月21.rar", "7月21.z01");
        assertTarget(disturbedZipPlan, "7月21.z0啊啊2", "7月21.z02");

        Path groupsCase = Files.createDirectories(directory.resolve("independent-groups"));
        Path aOne = Files.writeString(groupsCase.resolve("A.foo001"), "a1");
        Path bOne = Files.writeString(groupsCase.resolve("B.foo001"), "b1");
        Path cOne = Files.writeString(groupsCase.resolve("C.foo001"), "c1");
        Path aTwo = Files.writeString(groupsCase.resolve("A.foo002"), "a2");
        Path bTwo = Files.writeString(groupsCase.resolve("B.foo002"), "b2");
        Path cTwo = Files.writeString(groupsCase.resolve("C.foo002"), "c2");
        List<RenameItem> groupsPlan = service.createPlan(
                List.of(aOne, bOne, cOne, aTwo, bTwo, cTwo), RenameMode.ZIP_MULTIPART);
        assertTarget(groupsPlan, "A.foo001", "A.zip");
        assertTarget(groupsPlan, "B.foo001", "B.zip");
        assertTarget(groupsPlan, "C.foo001", "C.zip");
        assertTarget(groupsPlan, "A.foo002", "A.z01");
        assertTarget(groupsPlan, "B.foo002", "B.z01");
        assertTarget(groupsPlan, "C.foo002", "C.z01");

        Path missingCase = Files.createDirectories(directory.resolve("missing-number"));
        Path knownTwo = Files.writeString(missingCase.resolve("delta.7z.002"), "two");
        Path knownThree = Files.writeString(missingCase.resolve("delta.7z.003"), "three");
        Path missingOne = Files.writeString(missingCase.resolve("delta.any"), "one");
        List<RenameItem> missingPlan = service.createPlan(
                List.of(knownTwo, knownThree, missingOne), RenameMode.SEVEN_ZIP_MULTIPART);
        assertTarget(missingPlan, "delta.any", "delta.7z.001");

        Path duplicateCase = Files.createDirectories(directory.resolve("duplicate-number"));
        Path duplicateOne = Files.writeString(duplicateCase.resolve("echo.noise001"), "one");
        Path duplicateTwo = Files.writeString(duplicateCase.resolve("echo.other001"), "two");
        List<RenameItem> duplicatePlan = service.createPlan(
                List.of(duplicateOne, duplicateTwo), RenameMode.SEVEN_ZIP_MULTIPART);
        assertTarget(duplicatePlan, "echo.noise001", "echo.7z.001");
        assertTarget(duplicatePlan, "echo.other001", "echo.7z.002");

        Path crossCase = Files.createDirectories(directory.resolve("selected-mode-wins"));
        Path crossOne = Files.writeString(crossCase.resolve("cross.7z.001"), "one");
        Path crossTwo = Files.writeString(crossCase.resolve("cross.part2.rar"), "two");
        List<RenameItem> crossPlan = service.createPlan(
                List.of(crossOne, crossTwo), RenameMode.ZIP_MULTIPART);
        assertTarget(crossPlan, "cross.7z.001", "cross.zip");
        assertTarget(crossPlan, "cross.part2.rar", "cross.z01");

        Path screenshotZipCase = Files.createDirectories(directory.resolve("zip-sequence-mapping"));
        Path screenshotPartTwo = Files.writeString(screenshotZipCase.resolve("6月新补.part2.rar"), "two");
        Path screenshotPartOne = Files.writeString(screenshotZipCase.resolve("6月新补.part1.rar"), "one");
        List<RenameItem> screenshotZipPlan = service.createPlan(
                List.of(screenshotPartTwo, screenshotPartOne), RenameMode.ZIP_MULTIPART);
        assertTarget(screenshotZipPlan, "6月新补.part1.rar", "6月新补.zip");
        assertTarget(screenshotZipPlan, "6月新补.part2.rar", "6月新补.z01");
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
