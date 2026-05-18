package com.stealadeal.repository;

import com.stealadeal.domain.SavedSearch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {
    List<SavedSearch> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<SavedSearch> findByIdAndUserId(Long id, Long userId);
    List<SavedSearch> findByAlertOnPriceDropTrue();
}
