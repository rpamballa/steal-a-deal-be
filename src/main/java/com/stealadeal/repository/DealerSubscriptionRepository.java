package com.stealadeal.repository;

import com.stealadeal.domain.DealerSubscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealerSubscriptionRepository extends JpaRepository<DealerSubscription, Long> {

    Optional<DealerSubscription> findByDealerId(Long dealerId);
}
