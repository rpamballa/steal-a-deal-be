package com.stealadeal.repository;

import com.stealadeal.domain.DealerInventoryFeed;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealerInventoryFeedRepository extends JpaRepository<DealerInventoryFeed, Long> {

    Optional<DealerInventoryFeed> findByDealerId(Long dealerId);

    List<DealerInventoryFeed> findByEnabledTrue();
}
