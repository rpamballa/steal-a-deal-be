package com.stealadeal.service.storage;

import com.stealadeal.config.VehicleImageStorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(name = "vehicleImageStorageService")
@ConditionalOnProperty(name = "app.storage.vehicle-images.provider", havingValue = "local", matchIfMissing = true)
public class LocalFilesystemVehicleImageStorage implements VehicleImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFilesystemVehicleImageStorage.class);

    private final Path rootDir;

    public LocalFilesystemVehicleImageStorage(VehicleImageStorageProperties properties) throws IOException {
        this.rootDir = Paths.get(properties.root()).toAbsolutePath().normalize();
        Files.createDirectories(this.rootDir);
        log.info("[storage/local] vehicle image root = {}", rootDir);
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public StoredObject store(StoreRequest request) throws IOException {
        String storageKey = UUID.randomUUID().toString().replace("-", "") + extensionFor(request.contentType());
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        try (InputStream source = request.content()) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        long actualSize = Files.size(target);
        log.debug("[storage/local] stored image key={} bytes={} contentType={}",
                storageKey, actualSize, request.contentType());
        return new StoredObject(storageKey, request.contentType(), actualSize);
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        Path target = resolve(storageKey);
        if (!Files.exists(target)) {
            throw new IOException("Image key not found: " + storageKey);
        }
        return Files.newInputStream(target);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    private String extensionFor(String contentType) {
        if (contentType == null) {
            return "";
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic" -> ".heic";
            default -> "";
        };
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.contains("/") || storageKey.contains("..")) {
            throw new IllegalArgumentException("Illegal storage key");
        }
        String shard = storageKey.length() >= 2 ? storageKey.substring(0, 2) : "_";
        Path resolved = rootDir.resolve(shard).resolve(storageKey).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new IllegalArgumentException("Illegal storage key: " + storageKey);
        }
        return resolved;
    }
}
