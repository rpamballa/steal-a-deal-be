package com.stealadeal.repository;

import com.stealadeal.domain.Deal;
import com.stealadeal.domain.DealStage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealRepository extends JpaRepository<Deal, Long> {

    List<Deal> findByStage(DealStage stage);

    List<Deal> findByVehicleId(Long vehicleId);

    List<Deal> findByBuyerEmailOrderByUpdatedAtDesc(String buyerEmail);

    List<Deal> findByVehicleDealerIdOrderByUpdatedAtDesc(Long dealerId);
}
