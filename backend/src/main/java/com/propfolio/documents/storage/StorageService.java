package com.propfolio.documents.storage;

import java.io.InputStream;

/**
 * Where uploaded bill files live. Keys are relative, forward-slash paths such as
 * {@code <landlordId>/2026/10/<billId>.pdf}. MVP1 stores them on the local disk;
 * a cloud implementation (Supabase Storage, Cloudflare R2) can replace this later.
 */
public interface StorageService {

    /** Saves the content under the key, creating folders as needed. */
    void store(String key, InputStream content);

    /** Opens the stored content. The caller closes the stream. 404 if it doesn't exist. */
    InputStream open(String key);

    /** Deletes the content. Does nothing if it doesn't exist. */
    void delete(String key);
}
