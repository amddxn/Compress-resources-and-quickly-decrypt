package app.archiverecovery.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Extracts and verifies the unmodified 7-Zip binaries embedded in the application. */
public final class BundledSevenZip {
    public static final String VERSION = "26.03";
    private static final List<EmbeddedFile> FILES = List.of(
            new EmbeddedFile("7z.exe",
                    "6EE3C0ED0B27663C1B948AE85A7C0BB073AED1498983182F3F0DF1F6A8C30B2F"),
            new EmbeddedFile("7z.dll",
                    "65E4C1F855F9EF6E8F0F5DF8E3F27D9EB5F07311408639DA0A1CA0B8F4871B0D"),
            new EmbeddedFile("LICENSE.txt",
                    "519AC0A4BDED9C18EA02E0AFB71F663D8C47373BD9FACD3AC96A79F51D77765D"),
            new EmbeddedFile("NOTICE.txt",
                    "48E8923CD29E1A30419615CAA934FD5783E2E976357A5CAC159D6F555939FC09")
    );

    private final Path storageDirectory;
    private boolean prepared;

    public BundledSevenZip() {
        this(defaultStorageDirectory());
    }

    BundledSevenZip(Path storageDirectory) {
        this.storageDirectory = storageDirectory.toAbsolutePath().normalize();
    }

    public synchronized Path executable() throws IOException {
        prepare();
        return storageDirectory.resolve("7z.exe");
    }

    public synchronized Path licenseFile() throws IOException {
        prepare();
        return storageDirectory.resolve("LICENSE.txt");
    }

    public synchronized Path noticeFile() throws IOException {
        prepare();
        return storageDirectory.resolve("NOTICE.txt");
    }

    public Path storageDirectory() {
        return storageDirectory;
    }

    synchronized void prepare() throws IOException {
        if (prepared && allFilesValid()) {
            return;
        }
        Files.createDirectories(storageDirectory);
        for (EmbeddedFile file : FILES) {
            Path destination = storageDirectory.resolve(file.name());
            if (!matchesHash(destination, file.sha256())) {
                extractAndVerify(file, destination);
            }
        }
        prepared = true;
    }

    private boolean allFilesValid() throws IOException {
        for (EmbeddedFile file : FILES) {
            if (!matchesHash(storageDirectory.resolve(file.name()), file.sha256())) {
                return false;
            }
        }
        return true;
    }

    private void extractAndVerify(EmbeddedFile file, Path destination) throws IOException {
        Path temporary = Files.createTempFile(storageDirectory, file.name() + ".", ".tmp");
        try (InputStream source = BundledSevenZip.class.getResourceAsStream("/sevenzip/" + file.name())) {
            if (source == null) {
                throw new IOException("程序中缺少内置 7-Zip 文件：" + file.name());
            }
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (!matchesHash(temporary, file.sha256())) {
                throw new IOException("内置 7-Zip 文件校验失败：" + file.name());
            }
            moveIntoPlace(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void moveIntoPlace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean matchesHash(Path path, String expectedHash) throws IOException {
        return Files.isRegularFile(path) && sha256(path).equalsIgnoreCase(expectedHash);
    }

    private String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, length);
            }
        }
        return HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    private static Path defaultStorageDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path root;
        if (localAppData != null && !localAppData.isBlank()) {
            root = Path.of(localAppData);
        } else {
            String userHome = System.getProperty("user.home", System.getProperty("java.io.tmpdir"));
            root = Path.of(userHome, "AppData", "Local");
        }
        return root.resolve("ArchiveRecoveryAssistant")
                .resolve("engine")
                .resolve("7zip-" + VERSION);
    }

    private record EmbeddedFile(String name, String sha256) {
    }
}
