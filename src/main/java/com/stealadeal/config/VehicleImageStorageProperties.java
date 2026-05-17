package com.stealadeal.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.vehicle-images")
public record VehicleImageStorageProperties(
        String provider,
        String root,
        long maxBytes,
        Integer maxPerListing,
        List<String> allowedContentTypes
) {

    public VehicleImageStorageProperties {
        if (provider == null || provider.isBlank()) {
            provider = "local";
        }
        if (root == null || root.isBlank()) {
            root = "var/vehicle-images";
        }
        if (maxBytes <= 0) {
            maxBytes = 8L * 1024 * 1024;
        }
        if (maxPerListing == null || maxPerListing < 1) {
            maxPerListing = 10;
        }
        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            allowedContentTypes = List.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp",
                    "image/heic"
            );
        }
    }
}
