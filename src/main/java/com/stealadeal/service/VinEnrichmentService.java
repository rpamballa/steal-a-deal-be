package com.stealadeal.service;

import com.stealadeal.service.vin.VinDecoder;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fills missing vehicle attributes from a VIN decode. Caller-provided
 * values always win; the decoder only fills blanks. Used by the
 * VIN-only create endpoint and by automated feed ingestion so the same
 * enrichment runs regardless of how inventory enters the system.
 */
@Service
public class VinEnrichmentService {

    public record EnrichedVehicle(int modelYear, String make, String model, String trim, boolean decoded) {
    }

    private final VinDecoder vinDecoder;

    public VinEnrichmentService(VinDecoder vinDecoder) {
        this.vinDecoder = vinDecoder;
    }

    public EnrichedVehicle enrich(
            String vin,
            Integer providedYear,
            String providedMake,
            String providedModel,
            String providedTrim
    ) {
        Integer year = providedYear != null && providedYear > 0 ? providedYear : null;
        String make = blankToNull(providedMake);
        String model = blankToNull(providedModel);
        String trim = blankToNull(providedTrim);

        boolean decoded = false;
        if (year == null || make == null || model == null || trim == null) {
            Optional<VinDecoder.DecodedVin> result = vinDecoder.decode(vin);
            if (result.isPresent()) {
                VinDecoder.DecodedVin d = result.get();
                if (year == null && d.modelYear() != null) {
                    year = d.modelYear();
                }
                if (make == null) {
                    make = blankToNull(d.make());
                }
                if (model == null) {
                    model = blankToNull(d.model());
                }
                if (trim == null) {
                    trim = blankToNull(d.trim());
                }
                decoded = true;
            }
        }

        if (year == null || make == null || model == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not resolve year/make/model for VIN " + vin
                            + " — decode failed and values were not supplied"
            );
        }
        if (trim == null) {
            trim = "Base";
        }
        return new EnrichedVehicle(year, make, model, trim, decoded);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
