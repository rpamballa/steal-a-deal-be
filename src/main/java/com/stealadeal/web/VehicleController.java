package com.stealadeal.web;

import com.stealadeal.domain.Appointment;
import com.stealadeal.domain.AppointmentStatus;
import com.stealadeal.domain.AppointmentType;
import com.stealadeal.domain.Dealer;
import com.stealadeal.domain.Lead;
import com.stealadeal.domain.LeadStatus;
import com.stealadeal.domain.Vehicle;
import com.stealadeal.domain.VehicleStatus;
import com.stealadeal.repository.AppointmentRepository;
import com.stealadeal.repository.DealerRepository;
import com.stealadeal.repository.LeadRepository;
import com.stealadeal.repository.VehicleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@Validated
public class VehicleController {

    private final DealerRepository dealerRepository;
    private final VehicleRepository vehicleRepository;
    private final LeadRepository leadRepository;
    private final AppointmentRepository appointmentRepository;

    public VehicleController(
            DealerRepository dealerRepository,
            VehicleRepository vehicleRepository,
            LeadRepository leadRepository,
            AppointmentRepository appointmentRepository
    ) {
        this.dealerRepository = dealerRepository;
        this.vehicleRepository = vehicleRepository;
        this.leadRepository = leadRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @GetMapping("/vehicles")
    public List<VehicleResponse> getVehicles(
            @RequestParam(required = false) Long dealerId,
            @RequestParam(defaultValue = "") String make,
            @RequestParam(defaultValue = "") String model,
            @RequestParam(defaultValue = "0") BigDecimal minPrice,
            @RequestParam(defaultValue = "9999999") BigDecimal maxPrice,
            @RequestParam(required = false) VehicleStatus status
    ) {
        List<VehicleStatus> statuses = status == null ? Arrays.asList(VehicleStatus.values()) : List.of(status);
        List<Vehicle> vehicles = dealerId == null
                ? vehicleRepository.findByStatusInAndMakeContainingIgnoreCaseAndModelContainingIgnoreCaseAndPriceBetween(
                        statuses, make, model, minPrice, maxPrice)
                : vehicleRepository.findByStatusInAndDealerIdAndMakeContainingIgnoreCaseAndModelContainingIgnoreCaseAndPriceBetween(
                        statuses, dealerId, make, model, minPrice, maxPrice);
        return vehicles.stream().map(VehicleResponse::from).toList();
    }

    @GetMapping("/vehicles/{vehicleId}")
    public VehicleResponse getVehicle(@PathVariable Long vehicleId) {
        Vehicle vehicle = findVehicle(vehicleId);
        return VehicleResponse.from(vehicle);
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse createVehicle(@Valid @RequestBody CreateVehicleRequest request) {
        Dealer dealer = findApprovedDealer(request.dealerId());
        Vehicle vehicle = new Vehicle();
        applyVehicleRequest(vehicle, request, dealer);
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @PutMapping("/vehicles/{vehicleId}")
    public VehicleResponse updateVehicle(@PathVariable Long vehicleId, @Valid @RequestBody CreateVehicleRequest request) {
        Vehicle vehicle = findVehicle(vehicleId);
        Dealer dealer = findApprovedDealer(request.dealerId());
        applyVehicleRequest(vehicle, request, dealer);
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @PostMapping("/vehicles/{vehicleId}/leads")
    @ResponseStatus(HttpStatus.CREATED)
    public LeadResponse createLead(@PathVariable Long vehicleId, @Valid @RequestBody CreateLeadRequest request) {
        Vehicle vehicle = findVehicle(vehicleId);

        Lead lead = new Lead();
        lead.setVehicle(vehicle);
        lead.setBuyerName(request.buyerName());
        lead.setBuyerEmail(request.buyerEmail());
        lead.setBuyerPhone(request.buyerPhone());
        lead.setMessage(request.message());
        lead.setStatus(LeadStatus.NEW);
        lead.setCreatedAt(OffsetDateTime.now());
        return LeadResponse.from(leadRepository.save(lead));
    }

    @GetMapping("/leads")
    public List<LeadResponse> getLeads(
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) LeadStatus status
    ) {
        List<Lead> leads;
        if (vehicleId != null && status != null) {
            leads = leadRepository.findByStatusAndVehicleId(status, vehicleId);
        } else if (vehicleId != null) {
            leads = leadRepository.findByVehicleId(vehicleId);
        } else if (status != null) {
            leads = leadRepository.findByStatus(status);
        } else {
            leads = leadRepository.findAll();
        }
        return leads.stream().map(LeadResponse::from).toList();
    }

    @PatchMapping("/leads/{leadId}/status")
    public LeadResponse updateLeadStatus(@PathVariable Long leadId, @Valid @RequestBody UpdateLeadStatusRequest request) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
        lead.setStatus(request.status());
        return LeadResponse.from(leadRepository.save(lead));
    }

    @PostMapping("/vehicles/{vehicleId}/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse createAppointment(
            @PathVariable Long vehicleId,
            @Valid @RequestBody CreateAppointmentRequest request
    ) {
        Vehicle vehicle = findVehicle(vehicleId);

        Appointment appointment = new Appointment();
        appointment.setVehicle(vehicle);
        appointment.setBuyerName(request.buyerName());
        appointment.setBuyerEmail(request.buyerEmail());
        appointment.setType(request.type());
        appointment.setStatus(AppointmentStatus.REQUESTED);
        appointment.setScheduledAt(request.scheduledAt());
        appointment.setCreatedAt(OffsetDateTime.now());
        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> getAppointments(
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) AppointmentStatus status
    ) {
        List<Appointment> appointments;
        if (vehicleId != null && status != null) {
            appointments = appointmentRepository.findByStatusAndVehicleId(status, vehicleId);
        } else if (vehicleId != null) {
            appointments = appointmentRepository.findByVehicleId(vehicleId);
        } else if (status != null) {
            appointments = appointmentRepository.findByStatus(status);
        } else {
            appointments = appointmentRepository.findAll();
        }
        return appointments.stream().map(AppointmentResponse::from).toList();
    }

    @PatchMapping("/appointments/{appointmentId}/status")
    public AppointmentResponse updateAppointmentStatus(
            @PathVariable Long appointmentId,
            @Valid @RequestBody UpdateAppointmentStatusRequest request
    ) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        appointment.setStatus(request.status());
        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    @GetMapping("/dashboard")
    public DashboardResponse getDashboard() {
        long dealerCount = dealerRepository.count();
        long vehicleCount = vehicleRepository.count();
        long liveVehicleCount = vehicleRepository.findByStatusInAndMakeContainingIgnoreCaseAndModelContainingIgnoreCaseAndPriceBetween(
                List.of(VehicleStatus.LIVE), "", "", BigDecimal.ZERO, new BigDecimal("9999999")
        ).size();
        long newLeadCount = leadRepository.findByStatus(LeadStatus.NEW).size();
        long requestedAppointmentCount = appointmentRepository.findByStatus(AppointmentStatus.REQUESTED).size();
        return new DashboardResponse(dealerCount, vehicleCount, liveVehicleCount, newLeadCount, requestedAppointmentCount);
    }

    private Dealer findApprovedDealer(Long dealerId) {
        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealer not found"));
        if (!dealer.isApproved()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dealer must be approved before publishing inventory");
        }
        return dealer;
    }

    private Vehicle findVehicle(Long vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
    }

    private void applyVehicleRequest(Vehicle vehicle, CreateVehicleRequest request, Dealer dealer) {
        vehicle.setDealer(dealer);
        vehicle.setVin(request.vin().toUpperCase());
        vehicle.setModelYear(request.modelYear());
        vehicle.setMake(request.make());
        vehicle.setModel(request.model());
        vehicle.setTrim(request.trim());
        vehicle.setImageUrls(request.imageUrls());
        vehicle.setMileage(request.mileage());
        vehicle.setPrice(request.price());
        vehicle.setStatus(request.status());
    }

    public record CreateVehicleRequest(
            @NotNull Long dealerId,
            @NotBlank @Pattern(regexp = "^[A-HJ-NPR-Z0-9]{17}$") String vin,
            @Min(1990) int modelYear,
            @NotBlank String make,
            @NotBlank String model,
            @NotBlank String trim,
            @NotNull List<@NotBlank String> imageUrls,
            @Min(0) int mileage,
            @DecimalMin("0.0") BigDecimal price,
            @NotNull VehicleStatus status
    ) {
    }

    public record VehicleResponse(
            Long id,
            Long dealerId,
            String dealerName,
            String vin,
            int modelYear,
            String make,
            String model,
            String trim,
            String primaryImageUrl,
            List<String> imageUrls,
            int mileage,
            BigDecimal price,
            VehicleStatus status
    ) {

        static VehicleResponse from(Vehicle vehicle) {
            return new VehicleResponse(
                    vehicle.getId(),
                    vehicle.getDealer().getId(),
                    vehicle.getDealer().getName(),
                    vehicle.getVin(),
                    vehicle.getModelYear(),
                    vehicle.getMake(),
                    vehicle.getModel(),
                    vehicle.getTrim(),
                    vehicle.getPrimaryImageUrl(),
                    vehicle.getImageUrls(),
                    vehicle.getMileage(),
                    vehicle.getPrice(),
                    vehicle.getStatus()
            );
        }
    }

    public record CreateLeadRequest(
            @NotBlank String buyerName,
            @Email @NotBlank String buyerEmail,
            @NotBlank String buyerPhone,
            @NotBlank String message
    ) {
    }

    public record LeadResponse(
            Long id,
            Long vehicleId,
            String buyerName,
            String buyerEmail,
            String buyerPhone,
            String message,
            LeadStatus status,
            OffsetDateTime createdAt
    ) {

        static LeadResponse from(Lead lead) {
            return new LeadResponse(
                    lead.getId(),
                    lead.getVehicle().getId(),
                    lead.getBuyerName(),
                    lead.getBuyerEmail(),
                    lead.getBuyerPhone(),
                    lead.getMessage(),
                    lead.getStatus(),
                    lead.getCreatedAt()
            );
        }
    }

    public record UpdateLeadStatusRequest(@NotNull LeadStatus status) {
    }

    public record CreateAppointmentRequest(
            @NotBlank String buyerName,
            @Email @NotBlank String buyerEmail,
            @NotNull AppointmentType type,
            @NotNull OffsetDateTime scheduledAt
    ) {
    }

    public record AppointmentResponse(
            Long id,
            Long vehicleId,
            String buyerName,
            String buyerEmail,
            AppointmentType type,
            AppointmentStatus status,
            OffsetDateTime scheduledAt,
            OffsetDateTime createdAt
    ) {

        static AppointmentResponse from(Appointment appointment) {
            return new AppointmentResponse(
                    appointment.getId(),
                    appointment.getVehicle().getId(),
                    appointment.getBuyerName(),
                    appointment.getBuyerEmail(),
                    appointment.getType(),
                    appointment.getStatus(),
                    appointment.getScheduledAt(),
                    appointment.getCreatedAt()
            );
        }
    }

    public record UpdateAppointmentStatusRequest(@NotNull AppointmentStatus status) {
    }

    public record DashboardResponse(
            long dealerCount,
            long vehicleCount,
            long liveVehicleCount,
            long newLeadCount,
            long requestedAppointmentCount
    ) {
    }
}
