package com.stealadeal.repository;

import com.stealadeal.domain.Lead;
import com.stealadeal.domain.LeadStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByStatusAndVehicleId(LeadStatus status, Long vehicleId);

    List<Lead> findByStatus(LeadStatus status);

    List<Lead> findByVehicleId(Long vehicleId);
}
