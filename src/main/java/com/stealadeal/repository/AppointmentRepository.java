package com.stealadeal.repository;

import com.stealadeal.domain.Appointment;
import com.stealadeal.domain.AppointmentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByStatusAndVehicleId(AppointmentStatus status, Long vehicleId);

    List<Appointment> findByStatus(AppointmentStatus status);

    List<Appointment> findByVehicleId(Long vehicleId);

    List<Appointment> findByVehicleDealerId(Long dealerId);
}
