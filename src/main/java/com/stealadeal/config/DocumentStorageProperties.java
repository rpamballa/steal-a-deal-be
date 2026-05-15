package com.stealadeal.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.documents")
public record DocumentStorageProperties(
        String provider,
        String root,
        long maxBytes,
        List<String> allowedContentTypes
) {

    public DocumentStorageProperties {
        if (provider == null || provider.isBlank()) {
            provider = "local";
        }
        if (root == null || root.isBlank()) {
            root = "var/documents";
        }
        if (maxBytes <= 0) {
            maxBytes = 25L * 1024 * 1024;
        }
        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            allowedContentTypes = List.of(
                    "application/pdf",
                    "image/jpeg",
                    "image/png",
                    "image/heic"
            );
        }
    }
}
