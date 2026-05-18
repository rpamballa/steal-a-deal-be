package com.stealadeal.repository;

import com.stealadeal.domain.PriceDropAlertLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceDropAlertLogRepository extends JpaRepository<PriceDropAlertLog, Long> {
    Optional<PriceDropAlertLog> findByUserIdAndVehicleId(Long userId, Long vehicleId);
}
