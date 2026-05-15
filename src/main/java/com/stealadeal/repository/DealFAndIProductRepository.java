package com.stealadeal.repository;

import com.stealadeal.domain.DealFAndIProduct;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealFAndIProductRepository extends JpaRepository<DealFAndIProduct, Long> {

    List<DealFAndIProduct> findByDealIdOrderByCreatedAtAsc(Long dealId);

    Optional<DealFAndIProduct> findByDealIdAndProductId(Long dealId, Long productId);
}
