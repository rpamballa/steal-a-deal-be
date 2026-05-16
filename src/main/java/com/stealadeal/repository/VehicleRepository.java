package com.stealadeal.repository;

import com.stealadeal.domain.Vehicle;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByStatusInAndDealerIdAndMakeContainingIgnoreCaseAndModelContainingIgnoreCaseAndPriceBetween(
            List<com.stealadeal.domain.VehicleStatus> statuses,
            Long dealerId,
            String make,
            String model,
            BigDecimal minPrice,
            BigDecimal maxPrice
    );

    List<Vehicle> findByStatusInAndMakeContainingIgnoreCaseAndModelContainingIgnoreCaseAndPriceBetween(
            List<com.stealadeal.domain.VehicleStatus> statuses,
            String make,
            String model,
            BigDecimal minPrice,
            BigDecimal maxPrice
    );

    List<Vehicle> findByDealerIdOrderByIdDesc(Long dealerId);

    boolean existsByDealerIdAndStatus(Long dealerId, com.stealadeal.domain.VehicleStatus status);

    Optional<Vehicle> findByVinIgnoreCase(String vin);
}
