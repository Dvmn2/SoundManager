package net.dvmn2.soundmanager.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Atomic file writes: the content goes to a temporary file next to the target, which then replaces it. */
public final class AtomicFiles {

    private static final String TEMP_SUFFIX = ".tmp";

    private AtomicFiles() {
    }

    /**
     * Writes the text as UTF-8, replacing the target atomically where the file system allows it.
     *
     * @throws IOException if the file cannot be written
     */
    public static void writeString(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
