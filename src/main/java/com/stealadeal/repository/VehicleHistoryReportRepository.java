package com.stealadeal.repository;

import com.stealadeal.domain.VehicleHistoryReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleHistoryReportRepository extends JpaRepository<VehicleHistoryReport, Long> {
    Optional<VehicleHistoryReport> findByVehicleId(Long vehicleId);
}
