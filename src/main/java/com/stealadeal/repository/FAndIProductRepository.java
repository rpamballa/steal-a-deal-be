package com.stealadeal.repository;

import com.stealadeal.domain.FAndIProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FAndIProductRepository extends JpaRepository<FAndIProduct, Long> {

    List<FAndIProduct> findByActiveOrderByIdAsc(boolean active);
}
