package com.stealadeal.web;

import com.stealadeal.domain.Deal;
import com.stealadeal.domain.DealActivity;
import com.stealadeal.domain.DealDocument;
import com.stealadeal.domain.DealStage;
import com.stealadeal.domain.DocumentStatus;
import com.stealadeal.domain.DocumentType;
import com.stealadeal.domain.FulfillmentStatus;
import com.stealadeal.domain.FulfillmentType;
import com.stealadeal.security.AuthenticatedUser;
import com.stealadeal.service.DealService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

@RestController
@RequestMapping("/api")
@Validated
public class DealController {

    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    @GetMapping("/deals")
    @PreAuthorize("@accessControl.isAuthenticated(authentication)")
    public List<DealResponse> getDeals(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) DealStage stage
    ) {
        return dealService.getDealsForPrincipal(user, vehicleId, stage).stream().map(DealResponse::from).toList();
    }

    @GetMapping("/deals/{dealId}")
    @PreAuthorize("@accessControl.canAccessDeal(authentication, #dealId)")
    public DealResponse getDeal(@PathVariable Long dealId) {
        return DealResponse.from(dealService.getDeal(dealId));
    }

    @PostMapping("/deals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accessControl.canCreateDeal(authentication, #request.vehicleId(), #request.buyerEmail())")
    public DealResponse createDeal(@Valid @RequestBody CreateDealRequest request) {
        return DealResponse.from(dealService.createDeal(new DealService.CreateDealCommand(
                request.vehicleId(),
                request.buyerName(),
                request.buyerEmail(),
                request.buyerPhone(),
                request.buyerAddressLine1(),
                request.buyerAddressLine2(),
                request.buyerCity(),
                request.buyerState(),
                request.buyerPostalCode(),
                request.fulfillmentType(),
                request.tradeIn(),
                request.tradeInVin(),
                request.tradeInMileage(),
                request.tradeInOffer(),
                request.deliveryFee(),
                request.discountAmount()
        )));
    }

    @PatchMapping("/deals/{dealId}/stage")
    @PreAuthorize("@accessControl.canAccessDeal(authentication, #dealId)")
    public DealResponse updateDealStage(@PathVariable Long dealId, @Valid @RequestBody UpdateDealStageRequest request) {
        return DealResponse.from(dealService.updateDealStage(dealId, request.stage()));
    }

    @PostMapping("/deals/{dealId}/deposit")
    @PreAuthorize("@accessControl.canAccessDeal(authentication, #dealId)")
    public DealResponse payDeposit(@PathVariable Long dealId, @Valid @RequestBody PayDepositRequest request) {
        return DealResponse.from(dealService.payDeposit(dealId, request.amount()));
    }

    @GetMapping("/deals/{dealId}/documents")
    @PreAuthorize("@accessControl.canAccessDeal(authentication, #dealId)")
    public List<DealDocumentResponse> getDealDocuments(
            @PathVariable Long dealId,
            @RequestParam(required = false) DocumentStatus status
    ) {
        return dealService.getDealDocuments(dealId, status).stream().map(DealDocumentResponse::from).toList();
    }

    @PostMapping("/deals/{dealId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accessControl.canAccessDeal(authentication, #dealId)")
    public DealDocumentResponse createDealDocument(@PathVariable Long dealId, @Valid @RequestBody CreateDealDocumentRequest request) {
        return DealDocumentResponse.from(dealService.createDealDocument(dealId, request.type(), request.fileName()));
    }

    @PatchMapping("/deals/{dealId}/documents/{documentId}/status")
    @PreAuthorize("@accessControl.canAccessDeal(authentication, #dealId)")
    public DealDocumentResponse updateDocumentStatus(
            @PathVariable Long dealId,
            @PathVariable Long documentId,
            @Valid @RequestBody UpdateDealDocumentStatusRequest request
    ) {
        return DealDocumentResponse.from(dealService.updateDocumentStatus(dealId, documentId, request.status()));
    }

    @PatchMapping("/deals/{dealId}/fulfillment")
    @PreAuthorize("@accessControl.canAccessDeal(authentication, #dealId)")
    public DealResponse updateFulfillment(@PathVariable Long dealId, @Valid @RequestBody UpdateFulfillmentRequest request) {
        return DealResponse.from(dealService.updateFulfillment(
                dealId,
                new DealService.UpdateFulfillmentCommand(
                        request.status(),
                        request.scheduledAt(),
                        request.location(),
                        request.notes()
                )
        ));
    }

    @GetMapping("/deals/{dealId}/activity")
    @PreAuthorize("@accessControl.canAccessDeal(authentication, #dealId)")
    public List<DealActivityResponse> getDealActivity(@PathVariable Long dealId) {
        return dealService.getDealActivity(dealId).stream().map(DealActivityResponse::from).toList();
    }

    @GetMapping("/deals/{dealId}/readiness")
    @PreAuthorize("@accessControl.canAccessDeal(authentication, #dealId)")
    public DealReadinessResponse getDealReadiness(@PathVariable Long dealId) {
        DealService.DealReadiness readiness = dealService.getDealReadiness(dealId);
        return new DealReadinessResponse(
                readiness.dealId(),
                readiness.readyForHandoff(),
                readiness.readyForCompletion(),
                readiness.completed(),
                readiness.blockers()
        );
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
}
