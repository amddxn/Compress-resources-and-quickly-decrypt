package app.archiverecovery.service;

import java.nio.file.Path;

public final class ExtensionServiceTest {
    private ExtensionServiceTest() {
    }

    public static void main(String[] args) {
        assertTarget("demo.txt", "zip", "demo.zip");
        assertTarget("demo.z1ip", "zip", "demo.zip");
        assertTarget("demo.rar1", "zip", "demo.zip");
        assertTarget("demo.71z", "zip", "demo.zip");
        assertTarget("demo.7zip", "rar", "demo.rar");
        assertTarget("demo.7z1", "7z", "demo.7z");
        assertTarget("archive.tar.gz", "7z", "archive.tar.7z");
        assertTarget("README", "rar", "README.rar");
        assertTarget(".gitignore", "zip", ".gitignore.zip");
        assertTarget("name.", "zip", "name.zip");

        assertUnchanged("demo.zip", "rar");
        assertUnchanged("demo.ZIP", "7z");
        assertUnchanged("demo.7z", "zip");
        assertUnchanged("demo.7Z", "rar");
        assertUnchanged("demo.rar", "zip");
        assertUnchanged("demo.RAR", "7z");

        System.out.println("ExtensionService tests passed.");
    }

    private static void assertTarget(String sourceName, String extension, String expectedName) {
        Path source = Path.of(sourceName);
        Path actual = ExtensionService.targetPath(source, extension);
        if (!actual.getFileName().toString().equals(expectedName)) {
            throw new AssertionError(sourceName + " expected " + expectedName + " but was " + actual);
        }
    }

    private static void assertUnchanged(String sourceName, String extension) {
        Path source = Path.of(sourceName);
        Path actual = ExtensionService.targetPath(source, extension);
        if (!actual.equals(source)) {
            throw new AssertionError(sourceName + " should remain unchanged but was " + actual);
        }
    }
}
