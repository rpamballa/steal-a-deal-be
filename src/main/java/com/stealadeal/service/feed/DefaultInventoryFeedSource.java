package com.stealadeal.service.feed;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Default feed source: HTTP(S) for syndication exports, plus file: and
 * classpath: for local/dev/test feeds.
 */
@Component
@ConditionalOnMissingBean(name = "inventoryFeedSource")
public class DefaultInventoryFeedSource implements InventoryFeedSource {

    private final RestClient restClient = RestClient.create();

    @Override
    public String name() {
        return "default";
    }

    @Override
    public boolean supports(String location) {
        if (location == null) {
            return false;
        }
        return location.startsWith("http://")
                || location.startsWith("https://")
                || location.startsWith("file:")
                || location.startsWith("classpath:");
    }

    @Override
    public InputStream open(String location) throws IOException {
        if (location.startsWith("classpath:")) {
            return new ClassPathResource(location.substring("classpath:".length())).getInputStream();
        }
        if (location.startsWith("file:")) {
            return Files.newInputStream(Path.of(URI.create(location)));
        }
        byte[] body = restClient.get().uri(location).retrieve().body(byte[].class);
        if (body == null) {
            throw new IOException("Empty response from feed: " + location);
        }
        return new java.io.ByteArrayInputStream(body);
    }
}
