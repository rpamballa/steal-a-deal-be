package com.stealadeal.service.buyer;

import com.stealadeal.domain.SavedSearch;
import com.stealadeal.repository.SavedSearchRepository;
import com.stealadeal.repository.VehicleRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class SavedSearchService {

    private final SavedSearchRepository savedSearchRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleSearchMatcher matcher;

    public SavedSearchService(SavedSearchRepository savedSearchRepository,
                              VehicleRepository vehicleRepository,
                              VehicleSearchMatcher matcher) {
        this.savedSearchRepository = savedSearchRepository;
        this.vehicleRepository = vehicleRepository;
        this.matcher = matcher;
    }

    @Transactional(readOnly = true)
    public List<SavedSearch> list(Long userId) {
        return savedSearchRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public SavedSearch create(Long userId, String name, BuyerSearchQuery query, boolean alertOnPriceDrop) {
        SavedSearch s = new SavedSearch();
        s.setUserId(userId);
        s.setCreatedAt(OffsetDateTime.now());
        apply(s, name, query, alertOnPriceDrop);
        return savedSearchRepository.save(s);
    }

    public SavedSearch update(Long userId, Long id, String name, BuyerSearchQuery query, boolean alertOnPriceDrop) {
        SavedSearch s = savedSearchRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved search not found"));
        apply(s, name, query, alertOnPriceDrop);
        return savedSearchRepository.save(s);
    }

    public void delete(Long userId, Long id) {
        savedSearchRepository.findByIdAndUserId(id, userId)
                .ifPresent(savedSearchRepository::delete);
    }

    public BuyerSearchQuery toQuery(SavedSearch s) {
        return new BuyerSearchQuery(s.getQ(), s.getMake(), s.getModel(), s.getMinPrice(),
                s.getMaxPrice(), s.getMinYear(), s.getMaxMileage(), s.getStatus());
    }

    private void apply(SavedSearch s, String name, BuyerSearchQuery q, boolean alertOnPriceDrop) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        s.setName(name.trim());
        s.setQ(blankToNull(q == null ? null : q.q()));
        s.setMake(blankToNull(q == null ? null : q.make()));
        s.setModel(blankToNull(q == null ? null : q.model()));
        s.setMinPrice(q == null ? null : q.minPrice());
        s.setMaxPrice(q == null ? null : q.maxPrice());
        s.setMinYear(q == null ? null : q.minYear());
        s.setMaxMileage(q == null ? null : q.maxMileage());
        s.setStatus(q == null ? null : q.status());
        s.setAlertOnPriceDrop(alertOnPriceDrop);
        s.setLastMatchedCount((int) matcher.count(vehicleRepository.findAll(), toQuery(s)));
    }

    private String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
