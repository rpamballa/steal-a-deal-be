package com.stealadeal.service.vin;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Offline decoder used in dev/CI. Returns a deterministic synthetic
 * decode for any structurally valid 17-char VIN so the enrichment path
 * is exercised without a network call. Production uses NHTSA.
 */
@Component
@ConditionalOnMissingBean(name = "vinDecoder")
@ConditionalOnProperty(name = "app.vin.provider", havingValue = "stub", matchIfMissing = true)
public class StubVinDecoder implements VinDecoder {

    private static final Logger log = LoggerFactory.getLogger(StubVinDecoder.class);

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public Optional<DecodedVin> decode(String vin) {
        if (vin == null || vin.length() != 17) {
            return Optional.empty();
        }
        log.debug("[vin/stub] synthetic decode for {}", vin);
        return Optional.of(new DecodedVin(2022, "Honda", "Accord", "EX"));
    }
}
