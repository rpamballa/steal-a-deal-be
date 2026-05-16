package com.stealadeal.service.vin;

import java.util.Optional;

/**
 * SPI for VIN decoding. The default {@link StubVinDecoder} returns a
 * deterministic synthetic decode (offline, safe for CI). Production
 * registers {@link NhtsaVinDecoder} via {@code app.vin.provider=nhtsa},
 * which calls the free public NHTSA vPIC API. A commercial decoder
 * (DataOne, Vehicle Databases) drops in behind the same interface.
 */
public interface VinDecoder {

    record DecodedVin(Integer modelYear, String make, String model, String trim) {
    }

    String name();

    Optional<DecodedVin> decode(String vin);
}
