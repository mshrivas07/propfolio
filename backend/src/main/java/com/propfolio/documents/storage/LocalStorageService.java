package com.propfolio.documents.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.propfolio.common.error.NotFoundException;

/**
 * Stores files under {@code app.storage.local-root} on the machine running the backend.
 *
 * <p>Keys are checked before use: only letters, digits, '.', '_' and '-' in each
 * forward-slash-separated segment, no "." or ".." segments, and the resolved path
 * must stay inside the root folder.
 */
@Service
public class LocalStorageService implements StorageService {

    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");

    private final Path root;

    public LocalStorageService(StorageProperties properties) {
        this.root = properties.localRoot().toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, InputStream content) {
        Path target = resolve(key);
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            // Write to a temp file first so a half-written upload never appears under the real key.
            temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new StorageException("Couldn't save the file", e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (NoSuchFileException e) {
            throw new NotFoundException("Stored file");
        } catch (IOException e) {
            throw new StorageException("Couldn't read the file", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Couldn't delete the file", e);
        }
    }

    /** Turns a key into a path inside the root, refusing anything that could escape it. */
    Path resolve(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("\\")) {
            throw new StorageException("Unsafe storage key");
        }
        for (String segment : key.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..") || !SAFE_SEGMENT.matcher(segment).matches()) {
                throw new StorageException("Unsafe storage key");
            }
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new StorageException("Unsafe storage key");
        }
        return path;
    }

    private static void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Best effort: a stray temp file is harmless.
            }
        }
    }
}
