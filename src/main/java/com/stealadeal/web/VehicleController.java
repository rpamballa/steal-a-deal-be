package com.stealadeal.web;

import com.stealadeal.domain.Appointment;
import com.stealadeal.domain.AppointmentStatus;
import com.stealadeal.domain.AppointmentType;
import com.stealadeal.domain.Lead;
import com.stealadeal.domain.LeadStatus;
import com.stealadeal.domain.Vehicle;
import com.stealadeal.domain.VehicleStatus;
import com.stealadeal.security.AuthenticatedUser;
import com.stealadeal.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Validated
public class VehicleController {

    private final InventoryService inventoryService;

    public VehicleController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
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
        return inventoryService.getVehicles(dealerId, make, model, minPrice, maxPrice, status)
                .stream()
                .map(VehicleResponse::from)
                .toList();
    }

    @GetMapping("/vehicles/{vehicleId}")
    public VehicleResponse getVehicle(@PathVariable Long vehicleId) {
        return VehicleResponse.from(inventoryService.getVehicle(vehicleId));
    }

    @GetMapping("/dealers/{dealerId}/inventory")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public List<VehicleResponse> getDealerInventory(@PathVariable Long dealerId) {
        return inventoryService.getDealerInventory(dealerId).stream().map(VehicleResponse::from).toList();
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #request.dealerId)")
    public VehicleResponse createVehicle(@Valid @RequestBody CreateVehicleRequest request) {
        return VehicleResponse.from(inventoryService.createVehicle(
                request.dealerId(),
                request.vin(),
                request.modelYear(),
                request.make(),
                request.model(),
                request.trim(),
                request.imageUrls(),
                request.mileage(),
                request.price(),
                request.status()
        ));
    }

    @PostMapping("/dealers/{dealerId}/inventory-upload")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public InventoryUploadResponse uploadInventory(
            @PathVariable Long dealerId,
            @Valid @RequestBody InventoryUploadRequest request
    ) {
        return InventoryUploadResponse.from(inventoryService.uploadInventory(
                dealerId,
                request.mode(),
                request.vehicles().stream()
                        .map(vehicle -> new InventoryService.InventoryUploadVehicle(
                                vehicle.vin(),
                                vehicle.modelYear(),
                                vehicle.make(),
                                vehicle.model(),
                                vehicle.trim(),
                                vehicle.imageUrls(),
                                vehicle.mileage(),
                                vehicle.price(),
                                vehicle.status()
                        ))
                        .toList()
        ));
    }

    @PostMapping(value = "/dealers/{dealerId}/inventory-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public InventoryUploadResponse uploadInventoryCsv(
            @PathVariable Long dealerId,
            @RequestParam InventoryService.InventoryUploadMode mode,
            @RequestParam("file") MultipartFile file
    ) {
        return InventoryUploadResponse.from(inventoryService.uploadInventoryCsv(dealerId, mode, file));
    }

    @PutMapping("/vehicles/{vehicleId}")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #request.dealerId)")
    public VehicleResponse updateVehicle(@PathVariable Long vehicleId, @Valid @RequestBody CreateVehicleRequest request) {
        return VehicleResponse.from(inventoryService.updateVehicle(
                vehicleId,
                request.dealerId(),
                request.vin(),
                request.modelYear(),
                request.make(),
                request.model(),
                request.trim(),
                request.imageUrls(),
                request.mileage(),
                request.price(),
                request.status()
        ));
    }

    @PostMapping("/vehicles/{vehicleId}/leads")
    @ResponseStatus(HttpStatus.CREATED)
    public LeadResponse createLead(@PathVariable Long vehicleId, @Valid @RequestBody CreateLeadRequest request) {
        return LeadResponse.from(inventoryService.createLead(
                vehicleId,
                request.buyerName(),
                request.buyerEmail(),
                request.buyerPhone(),
                request.message()
        ));
    }

    @GetMapping("/leads")
    @PreAuthorize("@accessControl.isAuthenticated(authentication)")
    public List<LeadResponse> getLeads(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) LeadStatus status
    ) {
        return inventoryService.getLeadsForPrincipal(user, vehicleId, status).stream().map(LeadResponse::from).toList();
    }

    @PatchMapping("/leads/{leadId}/status")
    @PreAuthorize("@accessControl.isAuthenticated(authentication)")
    public LeadResponse updateLeadStatus(@PathVariable Long leadId, @Valid @RequestBody UpdateLeadStatusRequest request) {
        return LeadResponse.from(inventoryService.updateLeadStatus(leadId, request.status()));
    }

    @PostMapping("/vehicles/{vehicleId}/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse createAppointment(
            @PathVariable Long vehicleId,
            @Valid @RequestBody CreateAppointmentRequest request
    ) {
        return AppointmentResponse.from(inventoryService.createAppointment(
                vehicleId,
                request.buyerName(),
                request.buyerEmail(),
                request.type(),
                request.scheduledAt()
        ));
    }

    @GetMapping("/appointments")
    @PreAuthorize("@accessControl.isAuthenticated(authentication)")
    public List<AppointmentResponse> getAppointments(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) AppointmentStatus status
    ) {
        return inventoryService.getAppointmentsForPrincipal(user, vehicleId, status).stream().map(AppointmentResponse::from).toList();
    }

    @PatchMapping("/appointments/{appointmentId}/status")
    @PreAuthorize("@accessControl.isAuthenticated(authentication)")
    public AppointmentResponse updateAppointmentStatus(
            @PathVariable Long appointmentId,
            @Valid @RequestBody UpdateAppointmentStatusRequest request
    ) {
        return AppointmentResponse.from(inventoryService.updateAppointmentStatus(appointmentId, request.status()));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("@accessControl.isAuthenticated(authentication)")
    public DashboardResponse getDashboard() {
        InventoryService.DashboardMetrics metrics = inventoryService.getDashboardMetrics();
        return new DashboardResponse(
                metrics.dealerCount(),
                metrics.vehicleCount(),
                metrics.liveVehicleCount(),
                metrics.newLeadCount(),
                metrics.requestedAppointmentCount()
        );
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

    public record InventoryUploadRequest(
            @NotNull InventoryService.InventoryUploadMode mode,
            @NotNull List<@Valid InventoryUploadVehicleRequest> vehicles
    ) {
    }

    public record InventoryUploadVehicleRequest(
            @NotBlank @Pattern(regexp = "^[A-HJ-NPR-Z0-9]{17}$") String vin,
            @Min(1990) int modelYear,
            @NotBlank String make,
            @NotBlank String model,
            @NotBlank String trim,
            @NotNull List<@NotBlank String> imageUrls,
            @Min(0) int mileage,
            @NotNull @DecimalMin("0.0") BigDecimal price,
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

    public record InventoryUploadResponse(
            Long dealerId,
            String dealerName,
            InventoryService.InventoryUploadMode mode,
            int totalRows,
            int createdCount,
            int updatedCount,
            int rejectedCount,
            List<InventoryUploadRowResponse> rows
    ) {

        static InventoryUploadResponse from(InventoryService.InventoryUploadResult result) {
            return new InventoryUploadResponse(
                    result.dealerId(),
                    result.dealerName(),
                    result.mode(),
                    result.totalRows(),
                    result.createdCount(),
                    result.updatedCount(),
                    result.rejectedCount(),
                    result.rows().stream().map(InventoryUploadRowResponse::from).toList()
            );
        }
    }

    public record InventoryUploadRowResponse(
            int rowNumber,
            String vin,
            InventoryService.InventoryUploadRowStatus status,
            Long vehicleId,
            String message
    ) {

        static InventoryUploadRowResponse from(InventoryService.InventoryUploadRowResult row) {
            return new InventoryUploadRowResponse(
                    row.rowNumber(),
                    row.vin(),
                    row.status(),
                    row.vehicleId(),
                    row.message()
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
