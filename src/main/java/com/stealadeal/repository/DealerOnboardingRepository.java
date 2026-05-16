package com.stealadeal.repository;

import com.stealadeal.domain.DealerOnboarding;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealerOnboardingRepository extends JpaRepository<DealerOnboarding, Long> {

    Optional<DealerOnboarding> findByDealerId(Long dealerId);
}
