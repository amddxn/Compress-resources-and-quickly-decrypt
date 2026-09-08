package app.archiverecovery.extract;

import java.nio.file.Path;
import java.util.Optional;

@FunctionalInterface
public interface PasswordProvider {
    Optional<char[]> requestPassword(Path archive, char[] rejectedPassword, String errorMessage);
}
