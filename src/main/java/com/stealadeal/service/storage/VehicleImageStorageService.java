package com.stealadeal.service.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * SPI for vehicle listing photo storage. The default
 * {@link LocalFilesystemVehicleImageStorage} writes to a configured
 * root directory; an S3/CDN-backed implementation is added by
 * registering a bean with a different {@link #name()} and selecting it
 * via {@code app.storage.vehicle-images.provider}. Separate from
 * document storage so image size/type/count limits are tuned
 * independently.
 */
public interface VehicleImageStorageService {

    record StoreRequest(String contentType, long sizeBytes, InputStream content) {
    }

    record StoredObject(String storageKey, String contentType, long sizeBytes) {
    }

    String name();

    StoredObject store(StoreRequest request) throws IOException;

    InputStream open(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
