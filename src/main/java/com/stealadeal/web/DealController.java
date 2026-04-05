package com.stealadeal.web;

import com.stealadeal.domain.Deal;
import com.stealadeal.domain.DealActivity;
import com.stealadeal.domain.DealDocument;
import com.stealadeal.domain.DealStage;
import com.stealadeal.domain.DocumentStatus;
import com.stealadeal.domain.DocumentType;
import com.stealadeal.domain.FulfillmentStatus;
import com.stealadeal.domain.FulfillmentType;
import com.stealadeal.domain.Vehicle;
import com.stealadeal.domain.VehicleStatus;
import com.stealadeal.repository.DealActivityRepository;
import com.stealadeal.repository.DealDocumentRepository;
import com.stealadeal.repository.DealRepository;
import com.stealadeal.repository.VehicleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@Validated
public class DealController {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.0825");
    private static final BigDecimal REGISTRATION_FEE = new BigDecimal("425.00");
    private static final BigDecimal DOCUMENTATION_FEE = new BigDecimal("199.00");
    private static final BigDecimal DEFAULT_DEPOSIT = new BigDecimal("500.00");

    private final DealRepository dealRepository;
    private final DealActivityRepository dealActivityRepository;
    private final DealDocumentRepository dealDocumentRepository;
    private final VehicleRepository vehicleRepository;

    public DealController(
            DealRepository dealRepository,
            DealActivityRepository dealActivityRepository,
            DealDocumentRepository dealDocumentRepository,
            VehicleRepository vehicleRepository
    ) {
        this.dealRepository = dealRepository;
        this.dealActivityRepository = dealActivityRepository;
        this.dealDocumentRepository = dealDocumentRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @GetMapping("/deals")
    public List<DealResponse> getDeals(
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) DealStage stage
    ) {
        List<Deal> deals;
        if (vehicleId != null) {
            deals = dealRepository.findByVehicleId(vehicleId);
            if (stage != null) {
                deals = deals.stream().filter(deal -> deal.getStage() == stage).toList();
            }
        } else if (stage != null) {
            deals = dealRepository.findByStage(stage);
        } else {
            deals = dealRepository.findAll();
        }
        return deals.stream().map(DealResponse::from).toList();
    }

    @GetMapping("/deals/{dealId}")
    public DealResponse getDeal(@PathVariable Long dealId) {
        return DealResponse.from(findDeal(dealId));
    }

    @PostMapping("/deals")
    @ResponseStatus(HttpStatus.CREATED)
    public DealResponse createDeal(@Valid @RequestBody CreateDealRequest request) {
        Vehicle vehicle = findVehicle(request.vehicleId());
        if (vehicle.getStatus() == VehicleStatus.SOLD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle is already sold");
        }
        if (request.tradeIn() && (request.tradeInVin() == null || request.tradeInMileage() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trade-in VIN and mileage are required when tradeIn is true");
        }

        Deal deal = new Deal();
        deal.setVehicle(vehicle);
        deal.setBuyerName(request.buyerName());
        deal.setBuyerEmail(request.buyerEmail());
        deal.setBuyerPhone(request.buyerPhone());
        deal.setBuyerAddressLine1(request.buyerAddressLine1());
        deal.setBuyerAddressLine2(blankToNull(request.buyerAddressLine2()));
        deal.setBuyerCity(request.buyerCity());
        deal.setBuyerState(request.buyerState().toUpperCase());
        deal.setBuyerPostalCode(request.buyerPostalCode());
        deal.setFulfillmentType(request.fulfillmentType());
        deal.setFulfillmentStatus(FulfillmentStatus.UNSCHEDULED);
        deal.setFulfillmentScheduledAt(null);
        deal.setFulfillmentLocation(null);
        deal.setFulfillmentNotes(null);
        deal.setTradeIn(request.tradeIn());
        deal.setTradeInVin(request.tradeIn() ? request.tradeInVin().toUpperCase() : null);
        deal.setTradeInMileage(request.tradeIn() ? request.tradeInMileage() : null);
        deal.setTradeInOffer(request.tradeIn()
                ? request.tradeInOffer().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        deal.setVehiclePrice(vehicle.getPrice());
        deal.setTaxAmount(vehicle.getPrice().multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP));
        deal.setRegistrationFee(REGISTRATION_FEE);
        deal.setDocumentationFee(DOCUMENTATION_FEE);
        deal.setDeliveryFee(request.fulfillmentType() == FulfillmentType.HOME_DELIVERY
                ? request.deliveryFee().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        deal.setDiscountAmount(request.discountAmount().setScale(2, RoundingMode.HALF_UP));
        deal.setDepositAmount(DEFAULT_DEPOSIT);
        deal.setDepositPaid(false);
        deal.setTotalAmount(calculateTotal(deal));
        deal.setStage(DealStage.INITIATED);
        deal.setCreatedAt(OffsetDateTime.now());
        deal.setUpdatedAt(OffsetDateTime.now());
        Deal savedDeal = dealRepository.save(deal);

        createDocument(savedDeal, DocumentType.BUYER_AGREEMENT, "buyer-agreement.pdf", DocumentStatus.REQUESTED);
        createDocument(savedDeal, DocumentType.DRIVER_LICENSE, "driver-license-upload", DocumentStatus.REQUESTED);
        createDocument(savedDeal, DocumentType.INSURANCE_PROOF, "insurance-proof-upload", DocumentStatus.REQUESTED);
        recordActivity(savedDeal, "DEAL_CREATED", "Deal created for " + savedDeal.getBuyerName());

        return DealResponse.from(savedDeal);
    }

    @PatchMapping("/deals/{dealId}/stage")
    public DealResponse updateDealStage(@PathVariable Long dealId, @Valid @RequestBody UpdateDealStageRequest request) {
        Deal deal = findDeal(dealId);
        validateStageTransition(deal, request.stage());
        DealStage previousStage = deal.getStage();
        deal.setStage(request.stage());
        deal.setUpdatedAt(OffsetDateTime.now());

        if (request.stage() == DealStage.COMPLETED) {
            Vehicle vehicle = deal.getVehicle();
            vehicle.setStatus(VehicleStatus.SOLD);
            vehicleRepository.save(vehicle);
            deal.setFulfillmentStatus(FulfillmentStatus.COMPLETED);
        }
        if (request.stage() == DealStage.CANCELED) {
            Vehicle vehicle = deal.getVehicle();
            if (vehicle.getStatus() == VehicleStatus.RESERVED) {
                vehicle.setStatus(VehicleStatus.LIVE);
                vehicleRepository.save(vehicle);
            }
            deal.setFulfillmentStatus(FulfillmentStatus.CANCELED);
        }
        Deal savedDeal = dealRepository.save(deal);
        recordActivity(savedDeal, "STAGE_CHANGED", "Deal stage changed from " + previousStage + " to " + savedDeal.getStage());
        return DealResponse.from(savedDeal);
    }

    @PostMapping("/deals/{dealId}/deposit")
    public DealResponse payDeposit(@PathVariable Long dealId, @Valid @RequestBody PayDepositRequest request) {
        Deal deal = findDeal(dealId);
        if (deal.isDepositPaid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposit has already been paid");
        }
        if (request.amount().compareTo(deal.getDepositAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposit amount is below required minimum");
        }

        deal.setDepositPaid(true);
        deal.setStage(DealStage.DEPOSIT_PAID);
        deal.setUpdatedAt(OffsetDateTime.now());

        Vehicle vehicle = deal.getVehicle();
        vehicle.setStatus(VehicleStatus.RESERVED);
        vehicleRepository.save(vehicle);

        Deal savedDeal = dealRepository.save(deal);
        recordActivity(savedDeal, "DEPOSIT_PAID", "Deposit recorded in the amount of " + request.amount().setScale(2, RoundingMode.HALF_UP));
        return DealResponse.from(savedDeal);
    }

    @GetMapping("/deals/{dealId}/documents")
    public List<DealDocumentResponse> getDealDocuments(
            @PathVariable Long dealId,
            @RequestParam(required = false) DocumentStatus status
    ) {
        findDeal(dealId);
        List<DealDocument> documents = status == null
                ? dealDocumentRepository.findByDealId(dealId)
                : dealDocumentRepository.findByDealIdAndStatus(dealId, status);
        return documents.stream().map(DealDocumentResponse::from).toList();
    }

    @PostMapping("/deals/{dealId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DealDocumentResponse createDealDocument(@PathVariable Long dealId, @Valid @RequestBody CreateDealDocumentRequest request) {
        Deal deal = findDeal(dealId);
        DealDocument document = createDocument(deal, request.type(), request.fileName(), DocumentStatus.UPLOADED);
        deal.setUpdatedAt(OffsetDateTime.now());
        if (deal.getStage() == DealStage.DEPOSIT_PAID || deal.getStage() == DealStage.INITIATED) {
            deal.setStage(DealStage.DOCUMENTS_PENDING);
            dealRepository.save(deal);
        }
        recordActivity(deal, "DOCUMENT_UPLOADED", request.type() + " uploaded as " + request.fileName());
        return DealDocumentResponse.from(document);
    }

    @PatchMapping("/deals/{dealId}/documents/{documentId}/status")
    public DealDocumentResponse updateDocumentStatus(
            @PathVariable Long dealId,
            @PathVariable Long documentId,
            @Valid @RequestBody UpdateDealDocumentStatusRequest request
    ) {
        findDeal(dealId);
        DealDocument document = dealDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!document.getDeal().getId().equals(dealId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document does not belong to this deal");
        }
        document.setStatus(request.status());
        document.setUpdatedAt(OffsetDateTime.now());
        DealDocument savedDocument = dealDocumentRepository.save(document);
        recordActivity(savedDocument.getDeal(), "DOCUMENT_STATUS_UPDATED", document.getType() + " marked as " + document.getStatus());
        return DealDocumentResponse.from(savedDocument);
    }

    @PatchMapping("/deals/{dealId}/fulfillment")
    public DealResponse updateFulfillment(@PathVariable Long dealId, @Valid @RequestBody UpdateFulfillmentRequest request) {
        Deal deal = findDeal(dealId);
        if (deal.getStage() == DealStage.CANCELED || deal.getStage() == DealStage.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot update fulfillment for a closed deal");
        }

        deal.setFulfillmentScheduledAt(request.scheduledAt());
        deal.setFulfillmentLocation(request.location());
        deal.setFulfillmentNotes(blankToNull(request.notes()));
        deal.setFulfillmentStatus(request.status());
        deal.setUpdatedAt(OffsetDateTime.now());
        if (request.status() == FulfillmentStatus.READY && deal.getStage() == DealStage.DOCUMENTS_PENDING) {
            deal.setStage(DealStage.READY_FOR_HANDOFF);
        }
        Deal savedDeal = dealRepository.save(deal);
        recordActivity(savedDeal, "FULFILLMENT_UPDATED", "Fulfillment set to " + request.status() + " at " + request.location());
        return DealResponse.from(savedDeal);
    }

    @GetMapping("/deals/{dealId}/activity")
    public List<DealActivityResponse> getDealActivity(@PathVariable Long dealId) {
        findDeal(dealId);
        return dealActivityRepository.findByDealIdOrderByCreatedAtAsc(dealId).stream()
                .map(DealActivityResponse::from)
                .toList();
    }

    @GetMapping("/deals/{dealId}/readiness")
    public DealReadinessResponse getDealReadiness(@PathVariable Long dealId) {
        Deal deal = findDeal(dealId);
        List<String> blockers = new ArrayList<>();
        if (!deal.isDepositPaid()) {
            blockers.add("Deposit has not been paid");
        }
        if (hasPendingDocuments(deal.getId())) {
            blockers.add("Required deal documents are still pending approval");
        }
        if (deal.getFulfillmentScheduledAt() == null || deal.getFulfillmentStatus() == FulfillmentStatus.UNSCHEDULED) {
            blockers.add("Fulfillment has not been scheduled");
        }
        if (deal.getStage() != DealStage.READY_FOR_HANDOFF && deal.getStage() != DealStage.COMPLETED) {
            blockers.add("Deal has not reached the handoff stage");
        }
        boolean readyForHandoff = blockers.isEmpty();
        return new DealReadinessResponse(
                deal.getId(),
                readyForHandoff,
                readyForHandoff && deal.getFulfillmentStatus() != FulfillmentStatus.COMPLETED,
                readyForHandoff && deal.getStage() == DealStage.COMPLETED,
                blockers
        );
    }

    private Deal findDeal(Long dealId) {
        return dealRepository.findById(dealId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal not found"));
    }

    private Vehicle findVehicle(Long vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
    }

    private BigDecimal calculateTotal(Deal deal) {
        return deal.getVehiclePrice()
                .add(deal.getTaxAmount())
                .add(deal.getRegistrationFee())
                .add(deal.getDocumentationFee())
                .add(deal.getDeliveryFee())
                .subtract(deal.getTradeInOffer())
                .subtract(deal.getDiscountAmount())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validateStageTransition(Deal deal, DealStage nextStage) {
        if (deal.getStage() == nextStage) {
            return;
        }
        List<DealStage> allowedStages = switch (deal.getStage()) {
            case INITIATED -> List.of(DealStage.OFFER_SENT, DealStage.CANCELED);
            case OFFER_SENT -> List.of(DealStage.BUYER_CONFIRMED, DealStage.CANCELED);
            case BUYER_CONFIRMED -> List.of(DealStage.DEPOSIT_PAID, DealStage.CANCELED);
            case DEPOSIT_PAID -> List.of(DealStage.DOCUMENTS_PENDING, DealStage.CANCELED);
            case DOCUMENTS_PENDING -> List.of(DealStage.READY_FOR_HANDOFF, DealStage.CANCELED);
            case READY_FOR_HANDOFF -> List.of(DealStage.COMPLETED, DealStage.CANCELED);
            case COMPLETED, CANCELED -> List.of();
        };
        if (!allowedStages.contains(nextStage)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid stage transition from " + deal.getStage() + " to " + nextStage
            );
        }
        if (nextStage == DealStage.DEPOSIT_PAID && !deal.isDepositPaid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposit must be paid before moving to DEPOSIT_PAID");
        }
        if (nextStage == DealStage.READY_FOR_HANDOFF && hasPendingDocuments(deal.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All deal documents must be approved before handoff");
        }
    }

    private boolean hasPendingDocuments(Long dealId) {
        return dealDocumentRepository.findByDealId(dealId).stream()
                .anyMatch(document -> document.getStatus() != DocumentStatus.APPROVED);
    }

    private void recordActivity(Deal deal, String eventType, String message) {
        DealActivity activity = new DealActivity();
        activity.setDeal(deal);
        activity.setEventType(eventType);
        activity.setMessage(message);
        activity.setCreatedAt(OffsetDateTime.now());
        dealActivityRepository.save(activity);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private DealDocument createDocument(Deal deal, DocumentType type, String fileName, DocumentStatus status) {
        DealDocument document = new DealDocument();
        document.setDeal(deal);
        document.setType(type);
        document.setFileName(fileName);
        document.setStatus(status);
        document.setCreatedAt(OffsetDateTime.now());
        document.setUpdatedAt(OffsetDateTime.now());
        return dealDocumentRepository.save(document);
    }

    public record CreateDealRequest(
            @NotNull Long vehicleId,
            @NotBlank String buyerName,
            @Email @NotBlank String buyerEmail,
            @NotBlank String buyerPhone,
            @NotBlank String buyerAddressLine1,
            String buyerAddressLine2,
            @NotBlank String buyerCity,
            @Pattern(regexp = "^[A-Za-z]{2}$") String buyerState,
            @Pattern(regexp = "^[0-9]{5}(-[0-9]{4})?$") String buyerPostalCode,
            @NotNull FulfillmentType fulfillmentType,
            boolean tradeIn,
            @Pattern(regexp = "^[A-HJ-NPR-Z0-9]{17}$") String tradeInVin,
            @Min(0) Integer tradeInMileage,
            @NotNull @DecimalMin("0.0") BigDecimal tradeInOffer,
            @NotNull @DecimalMin("0.0") BigDecimal deliveryFee,
            @NotNull @DecimalMin("0.0") BigDecimal discountAmount
    ) {
    }

    public record UpdateDealStageRequest(@NotNull DealStage stage) {
    }

    public record PayDepositRequest(@NotNull @DecimalMin("0.0") BigDecimal amount) {
    }

    public record CreateDealDocumentRequest(@NotNull DocumentType type, @NotBlank String fileName) {
    }

    public record UpdateDealDocumentStatusRequest(@NotNull DocumentStatus status) {
    }

    public record UpdateFulfillmentRequest(
            @NotNull FulfillmentStatus status,
            @NotNull OffsetDateTime scheduledAt,
            @NotBlank String location,
            String notes
    ) {
    }

    public record DealResponse(
            Long id,
            Long vehicleId,
            String vehicleTitle,
            String buyerName,
            String buyerEmail,
            String buyerPhone,
            String buyerAddressLine1,
            String buyerAddressLine2,
            String buyerCity,
            String buyerState,
            String buyerPostalCode,
            FulfillmentType fulfillmentType,
            FulfillmentStatus fulfillmentStatus,
            OffsetDateTime fulfillmentScheduledAt,
            String fulfillmentLocation,
            String fulfillmentNotes,
            boolean tradeIn,
            String tradeInVin,
            Integer tradeInMileage,
            BigDecimal tradeInOffer,
            BigDecimal vehiclePrice,
            BigDecimal taxAmount,
            BigDecimal registrationFee,
            BigDecimal documentationFee,
            BigDecimal deliveryFee,
            BigDecimal discountAmount,
            BigDecimal depositAmount,
            boolean depositPaid,
            BigDecimal totalAmount,
            DealStage stage,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {

        static DealResponse from(Deal deal) {
            return new DealResponse(
                    deal.getId(),
                    deal.getVehicle().getId(),
                    deal.getVehicle().getModelYear() + " " + deal.getVehicle().getMake() + " " + deal.getVehicle().getModel(),
                    deal.getBuyerName(),
                    deal.getBuyerEmail(),
                    deal.getBuyerPhone(),
                    deal.getBuyerAddressLine1(),
                    deal.getBuyerAddressLine2(),
                    deal.getBuyerCity(),
                    deal.getBuyerState(),
                    deal.getBuyerPostalCode(),
                    deal.getFulfillmentType(),
                    deal.getFulfillmentStatus(),
                    deal.getFulfillmentScheduledAt(),
                    deal.getFulfillmentLocation(),
                    deal.getFulfillmentNotes(),
                    deal.isTradeIn(),
                    deal.getTradeInVin(),
                    deal.getTradeInMileage(),
                    deal.getTradeInOffer(),
                    deal.getVehiclePrice(),
                    deal.getTaxAmount(),
                    deal.getRegistrationFee(),
                    deal.getDocumentationFee(),
                    deal.getDeliveryFee(),
                    deal.getDiscountAmount(),
                    deal.getDepositAmount(),
                    deal.isDepositPaid(),
                    deal.getTotalAmount(),
                    deal.getStage(),
                    deal.getCreatedAt(),
                    deal.getUpdatedAt()
            );
        }
    }

    public record DealActivityResponse(
            Long id,
            Long dealId,
            String eventType,
            String message,
            OffsetDateTime createdAt
    ) {

        static DealActivityResponse from(DealActivity activity) {
            return new DealActivityResponse(
                    activity.getId(),
                    activity.getDeal().getId(),
                    activity.getEventType(),
                    activity.getMessage(),
                    activity.getCreatedAt()
            );
        }
    }

    public record DealReadinessResponse(
            Long dealId,
            boolean readyForHandoff,
            boolean readyForCompletion,
            boolean completed,
            List<String> blockers
    ) {
    }

    public record DealDocumentResponse(
            Long id,
            Long dealId,
            DocumentType type,
            DocumentStatus status,
            String fileName,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {

        static DealDocumentResponse from(DealDocument document) {
            return new DealDocumentResponse(
                    document.getId(),
                    document.getDeal().getId(),
                    document.getType(),
                    document.getStatus(),
                    document.getFileName(),
                    document.getCreatedAt(),
                    document.getUpdatedAt()
            );
        }
    }
}
