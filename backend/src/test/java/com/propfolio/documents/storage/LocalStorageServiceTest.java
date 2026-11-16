package com.propfolio.documents.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.propfolio.common.error.NotFoundException;

class LocalStorageServiceTest {

    @TempDir
    Path root;

    private LocalStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalStorageService(new StorageProperties(root));
    }

    @Test
    void storesOpensAndDeletesCreatingFoldersAsNeeded() throws Exception {
        String key = "landlord-1/2026/10/bill-1.pdf";

        storage.store(key, stream("hello"));

        assertThat(root.resolve("landlord-1/2026/10/bill-1.pdf")).hasContent("hello");
        try (InputStream in = storage.open(key)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
        }

        storage.delete(key);
        assertThat(root.resolve(key)).doesNotExist();
    }

    @Test
    void leavesNoTempFilesBehind() throws Exception {
        storage.store("a/b/c.pdf", stream("x"));
        try (Stream<Path> files = Files.list(root.resolve("a/b"))) {
            assertThat(files).extracting(p -> p.getFileName().toString()).containsExactly("c.pdf");
        }
    }

    @Test
    void openingAMissingFileIsNotFound() {
        assertThatThrownBy(() -> storage.open("a/missing.pdf")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletingAMissingFileIsFine() {
        storage.delete("a/missing.pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../outside.pdf",
            "a/../../outside.pdf",
            "a/./b.pdf",
            "/etc/passwd",
            "a\\..\\b.pdf",
            "a//b.pdf",
            "a/b.pdf/",
            "",
            " ",
            "a/b c.pdf",
            "a/%2e%2e/b.pdf",
            ".."
    })
    void unsafeKeysAreRefused(String key) {
        assertThatThrownBy(() -> storage.store(key, stream("x"))).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.open(key)).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(StorageException.class);
    }

    @Test
    void nothingIsWrittenOutsideTheRoot() throws Exception {
        try {
            storage.store("../escape.pdf", stream("x"));
        } catch (StorageException expected) {
            // refused
        }
        assertThat(root.getParent().resolve("escape.pdf")).doesNotExist();
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
