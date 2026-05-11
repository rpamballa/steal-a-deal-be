package com.stealadeal.repository;

import com.stealadeal.domain.DealActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealActivityRepository extends JpaRepository<DealActivity, Long> {

    List<DealActivity> findByDealIdOrderByCreatedAtAsc(Long dealId);

    List<DealActivity> findByDealIdInOrderByCreatedAtDesc(List<Long> dealIds);
}
