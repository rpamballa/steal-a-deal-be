package com.stealadeal.service.vin;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Decodes VINs via NHTSA vPIC (free, unauthenticated public API).
 * Selected with {@code app.vin.provider=nhtsa}. Network/parse failures
 * return empty so callers fall back to caller-provided values rather
 * than failing the ingestion.
 */
@Component("vinDecoder")
@ConditionalOnProperty(name = "app.vin.provider", havingValue = "nhtsa")
public class NhtsaVinDecoder implements VinDecoder {

    private static final Logger log = LoggerFactory.getLogger(NhtsaVinDecoder.class);
    private static final String BASE_URL =
            "https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/";

    private final RestClient restClient = RestClient.create();

    @Override
    public String name() {
        return "nhtsa";
    }

    @Override
    public Optional<DecodedVin> decode(String vin) {
        if (vin == null || vin.length() != 17) {
            return Optional.empty();
        }
        try {
            JsonNode root = restClient.get()
                    .uri(BASE_URL + vin + "?format=json")
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                return Optional.empty();
            }
            JsonNode results = root.path("Results");
            if (!results.isArray() || results.isEmpty()) {
                return Optional.empty();
            }
            JsonNode r = results.get(0);
            Integer year = parseYear(text(r, "ModelYear"));
            String make = blankToNull(text(r, "Make"));
            String model = blankToNull(text(r, "Model"));
            String trim = blankToNull(text(r, "Trim"));
            if (make == null && model == null && year == null) {
                return Optional.empty();
            }
            return Optional.of(new DecodedVin(year, make, model, trim));
        } catch (RuntimeException exception) {
            log.warn("[vin/nhtsa] decode failed for {}: {}", vin, exception.getMessage());
            return Optional.empty();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Integer parseYear(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
