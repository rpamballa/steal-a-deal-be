package com.stealadeal.service.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * SPI for binary document storage. The default
 * {@link LocalFilesystemDocumentStorage} writes to a configured root
 * directory and is suitable for dev, CI, and pilot deployments. An
 * S3-backed implementation is added by registering a bean with a
 * different {@link #name()} and selecting it via
 * {@code app.storage.documents.provider}.
 */
public interface DocumentStorageService {

    record StoreRequest(String contentType, long sizeBytes, InputStream content) {
    }

    record StoredObject(String storageKey, String contentType, long sizeBytes) {
    }

    String name();

    StoredObject store(StoreRequest request) throws IOException;

    InputStream open(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
