package com.stealadeal.service;

import com.stealadeal.domain.Deal;
import com.stealadeal.domain.DealActivity;
import com.stealadeal.domain.DealDocument;
import com.stealadeal.domain.DealStage;
import com.stealadeal.domain.DocumentStatus;
import com.stealadeal.domain.DocumentType;
import com.stealadeal.domain.FulfillmentStatus;
import com.stealadeal.domain.FulfillmentType;
import com.stealadeal.domain.ParticipantType;
import com.stealadeal.domain.UserRole;
import com.stealadeal.domain.Vehicle;
import com.stealadeal.domain.VehicleStatus;
import com.stealadeal.config.DocumentStorageProperties;
import com.stealadeal.repository.DealActivityRepository;
import com.stealadeal.repository.DealDocumentRepository;
import com.stealadeal.repository.DealRepository;
import com.stealadeal.repository.VehicleRepository;
import com.stealadeal.security.AuthenticatedUser;
import com.stealadeal.service.storage.DocumentStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class DealService {

    public record CreateDealCommand(
            Long vehicleId,
            String buyerName,
            String buyerEmail,
            String buyerPhone,
            String buyerAddressLine1,
            String buyerAddressLine2,
            String buyerCity,
            String buyerState,
            String buyerPostalCode,
            FulfillmentType fulfillmentType,
            boolean tradeIn,
            String tradeInVin,
            Integer tradeInMileage,
            BigDecimal tradeInOffer,
            BigDecimal deliveryFee,
            BigDecimal discountAmount
    ) {
    }

    public record UpdateFulfillmentCommand(
            FulfillmentStatus status,
            OffsetDateTime scheduledAt,
            String location,
            String notes
    ) {
    }

    public record DealReadiness(
            Long dealId,
            boolean readyForHandoff,
            boolean readyForCompletion,
            boolean completed,
            List<String> blockers
    ) {
    }

    private static final BigDecimal TAX_RATE = new BigDecimal("0.0825");
    private static final BigDecimal REGISTRATION_FEE = new BigDecimal("425.00");
    private static final BigDecimal DOCUMENTATION_FEE = new BigDecimal("199.00");
    private static final BigDecimal DEFAULT_DEPOSIT = new BigDecimal("500.00");

    private final DealRepository dealRepository;
    private final DealActivityRepository dealActivityRepository;
    private final DealDocumentRepository dealDocumentRepository;
    private final VehicleRepository vehicleRepository;
    private final TaskNotificationService taskNotificationService;
    private final DocumentStorageService documentStorageService;
    private final DocumentStorageProperties documentStorageProperties;

    public DealService(
            DealRepository dealRepository,
            DealActivityRepository dealActivityRepository,
            DealDocumentRepository dealDocumentRepository,
            VehicleRepository vehicleRepository,
            TaskNotificationService taskNotificationService,
            DocumentStorageService documentStorageService,
            DocumentStorageProperties documentStorageProperties
    ) {
        this.dealRepository = dealRepository;
        this.dealActivityRepository = dealActivityRepository;
        this.dealDocumentRepository = dealDocumentRepository;
        this.vehicleRepository = vehicleRepository;
        this.taskNotificationService = taskNotificationService;
        this.documentStorageService = documentStorageService;
        this.documentStorageProperties = documentStorageProperties;
    }

    public record DocumentDownload(DealDocument document, InputStream content) {
    }

    @Transactional(readOnly = true)
    public List<Deal> getDeals(Long vehicleId, DealStage stage) {
        if (vehicleId != null) {
            List<Deal> deals = dealRepository.findByVehicleId(vehicleId);
            return stage == null ? deals : deals.stream().filter(deal -> deal.getStage() == stage).toList();
        }
        if (stage != null) {
            return dealRepository.findByStage(stage);
        }
        return dealRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Deal> getDealsForPrincipal(AuthenticatedUser user, Long vehicleId, DealStage stage) {
        if (user == null) {
            return List.of();
        }
        if (user.role() == UserRole.ADMIN) {
            return getDeals(vehicleId, stage);
        }
        if (user.role() == UserRole.DEALER && user.dealerId() != null) {
            return dealRepository.findByVehicleDealerIdOrderByUpdatedAtDesc(user.dealerId()).stream()
                    .filter(deal -> vehicleId == null || deal.getVehicle().getId().equals(vehicleId))
                    .filter(deal -> stage == null || deal.getStage() == stage)
                    .toList();
        }
        if (user.role() == UserRole.BUYER) {
            return dealRepository.findByBuyerEmailOrderByUpdatedAtDesc(user.email()).stream()
                    .filter(deal -> vehicleId == null || deal.getVehicle().getId().equals(vehicleId))
                    .filter(deal -> stage == null || deal.getStage() == stage)
                    .toList();
        }
        return List.of();
    }

    @Transactional(readOnly = true)
    public Deal getDeal(Long dealId) {
        return findDeal(dealId);
    }

    public Deal createDeal(CreateDealCommand command) {
        Vehicle vehicle = findVehicle(command.vehicleId());
        if (vehicle.getStatus() == VehicleStatus.SOLD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle is already sold");
        }
        if (command.tradeIn() && (command.tradeInVin() == null || command.tradeInMileage() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trade-in VIN and mileage are required when tradeIn is true");
        }

        Deal deal = new Deal();
        deal.setVehicle(vehicle);
        deal.setBuyerName(command.buyerName());
        deal.setBuyerEmail(command.buyerEmail());
        deal.setBuyerPhone(command.buyerPhone());
        deal.setBuyerAddressLine1(command.buyerAddressLine1());
        deal.setBuyerAddressLine2(blankToNull(command.buyerAddressLine2()));
        deal.setBuyerCity(command.buyerCity());
        deal.setBuyerState(command.buyerState().toUpperCase());
        deal.setBuyerPostalCode(command.buyerPostalCode());
        deal.setFulfillmentType(command.fulfillmentType());
        deal.setFulfillmentStatus(FulfillmentStatus.UNSCHEDULED);
        deal.setTradeIn(command.tradeIn());
        deal.setTradeInVin(command.tradeIn() ? command.tradeInVin().toUpperCase() : null);
        deal.setTradeInMileage(command.tradeIn() ? command.tradeInMileage() : null);
        deal.setTradeInOffer(command.tradeIn()
                ? command.tradeInOffer().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        deal.setVehiclePrice(vehicle.getPrice());
        deal.setTaxAmount(vehicle.getPrice().multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP));
        deal.setRegistrationFee(REGISTRATION_FEE);
        deal.setDocumentationFee(DOCUMENTATION_FEE);
        deal.setDeliveryFee(command.fulfillmentType() == FulfillmentType.HOME_DELIVERY
                ? command.deliveryFee().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        deal.setDiscountAmount(command.discountAmount().setScale(2, RoundingMode.HALF_UP));
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
        taskNotificationService.createNotification(
                savedDeal,
                ParticipantType.DEALER,
                String.valueOf(savedDeal.getVehicle().getDealer().getId()),
                "New deal created",
                "A new deal has been started for " + savedDeal.getVehicle().getMake() + " " + savedDeal.getVehicle().getModel()
        );
        taskNotificationService.syncForDeal(savedDeal);
        return savedDeal;
    }

    public Deal updateDealStage(Long dealId, DealStage nextStage) {
        Deal deal = findDeal(dealId);
        if (deal.getStage() == nextStage) {
            return deal;
        }
        validateStageTransition(deal, nextStage);
        DealStage previousStage = deal.getStage();
        deal.setStage(nextStage);
        deal.setUpdatedAt(OffsetDateTime.now());

        if (nextStage == DealStage.COMPLETED) {
            Vehicle vehicle = deal.getVehicle();
            vehicle.setStatus(VehicleStatus.SOLD);
            vehicleRepository.save(vehicle);
            deal.setFulfillmentStatus(FulfillmentStatus.COMPLETED);
        } else if (nextStage == DealStage.CANCELED) {
            Vehicle vehicle = deal.getVehicle();
            if (vehicle.getStatus() == VehicleStatus.RESERVED) {
                vehicle.setStatus(VehicleStatus.LIVE);
                vehicleRepository.save(vehicle);
            }
            deal.setFulfillmentStatus(FulfillmentStatus.CANCELED);
        }
        Deal savedDeal = dealRepository.save(deal);
        recordActivity(savedDeal, "STAGE_CHANGED", "Deal stage changed from " + previousStage + " to " + savedDeal.getStage());
        taskNotificationService.createNotification(
                savedDeal,
                ParticipantType.BUYER,
                savedDeal.getBuyerEmail(),
                "Deal status updated",
                "Your deal moved from " + previousStage + " to " + savedDeal.getStage()
        );
        taskNotificationService.syncForDeal(savedDeal);
        return savedDeal;
    }

    public Deal payDeposit(Long dealId, BigDecimal amount) {
        Deal deal = findDeal(dealId);
        if (deal.isDepositPaid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposit has already been paid");
        }
        if (amount.compareTo(deal.getDepositAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposit amount is below required minimum");
        }
        deal.setDepositPaid(true);
        deal.setStage(DealStage.DEPOSIT_PAID);
        deal.setUpdatedAt(OffsetDateTime.now());
        Vehicle vehicle = deal.getVehicle();
        vehicle.setStatus(VehicleStatus.RESERVED);
        vehicleRepository.save(vehicle);
        Deal savedDeal = dealRepository.save(deal);
        recordActivity(savedDeal, "DEPOSIT_PAID", "Deposit recorded in the amount of " + amount.setScale(2, RoundingMode.HALF_UP));
        taskNotificationService.createNotification(
                savedDeal,
                ParticipantType.DEALER,
                String.valueOf(savedDeal.getVehicle().getDealer().getId()),
                "Deposit paid",
                "Buyer deposit has been recorded. Review the deal documents next."
        );
        taskNotificationService.syncForDeal(savedDeal);
        return savedDeal;
    }

    @Transactional(readOnly = true)
    public List<DealDocument> getDealDocuments(Long dealId, DocumentStatus status) {
        findDeal(dealId);
        return status == null
                ? dealDocumentRepository.findByDealId(dealId)
                : dealDocumentRepository.findByDealIdAndStatus(dealId, status);
    }

    public DealDocument createDealDocument(Long dealId, DocumentType type, String fileName) {
        Deal deal = findDeal(dealId);
        DealDocument document = dealDocumentRepository.findByDealIdAndType(dealId, type)
                .map(existingDocument -> {
                    existingDocument.setFileName(fileName);
                    existingDocument.setStatus(DocumentStatus.UPLOADED);
                    existingDocument.setUpdatedAt(OffsetDateTime.now());
                    return dealDocumentRepository.save(existingDocument);
                })
                .orElseGet(() -> createDocument(deal, type, fileName, DocumentStatus.UPLOADED));
        deal.setUpdatedAt(OffsetDateTime.now());
        if (deal.getStage() == DealStage.DEPOSIT_PAID || deal.getStage() == DealStage.INITIATED) {
            deal.setStage(DealStage.DOCUMENTS_PENDING);
            dealRepository.save(deal);
        }
        recordActivity(deal, "DOCUMENT_UPLOADED", type + " uploaded as " + fileName);
        taskNotificationService.createNotification(
                deal,
                ParticipantType.DEALER,
                String.valueOf(deal.getVehicle().getDealer().getId()),
                "Document uploaded",
                type + " was uploaded and is ready for review."
        );
        taskNotificationService.syncForDeal(deal);
        return document;
    }

    public DealDocument uploadDealDocumentContent(Long dealId, Long documentId, MultipartFile file) {
        Deal deal = findDeal(dealId);
        DealDocument document = dealDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!document.getDeal().getId().equals(dealId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document does not belong to this deal");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload file is required");
        }
        if (file.getSize() > documentStorageProperties.maxBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File exceeds maximum allowed size of " + documentStorageProperties.maxBytes() + " bytes");
        }
        String contentType = file.getContentType();
        if (contentType == null || !documentStorageProperties.allowedContentTypes().contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported content type: " + contentType);
        }

        DocumentStorageService.StoredObject stored;
        try (InputStream content = file.getInputStream()) {
            stored = documentStorageService.store(new DocumentStorageService.StoreRequest(
                    contentType,
                    file.getSize(),
                    content
            ));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store document");
        }

        String oldStorageKey = document.getStorageKey();
        document.setStorageKey(stored.storageKey());
        document.setContentType(stored.contentType());
        document.setSizeBytes(stored.sizeBytes());
        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.isBlank()) {
            document.setFileName(originalName);
        }
        document.setStatus(DocumentStatus.UPLOADED);
        document.setUpdatedAt(OffsetDateTime.now());
        DealDocument saved = dealDocumentRepository.save(document);

        if (oldStorageKey != null && !oldStorageKey.equals(stored.storageKey())) {
            try {
                documentStorageService.delete(oldStorageKey);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }

        if (deal.getStage() == DealStage.DEPOSIT_PAID || deal.getStage() == DealStage.INITIATED) {
            deal.setStage(DealStage.DOCUMENTS_PENDING);
            deal.setUpdatedAt(OffsetDateTime.now());
            dealRepository.save(deal);
        }
        recordActivity(deal, "DOCUMENT_UPLOADED", saved.getType() + " uploaded (" + saved.getSizeBytes() + " bytes)");
        taskNotificationService.createNotification(
                deal,
                ParticipantType.DEALER,
                String.valueOf(deal.getVehicle().getDealer().getId()),
                "Document uploaded",
                saved.getType() + " was uploaded and is ready for review."
        );
        taskNotificationService.syncForDeal(deal);
        return saved;
    }

    @Transactional(readOnly = true)
    public DocumentDownload downloadDealDocumentContent(Long dealId, Long documentId) {
        findDeal(dealId);
        DealDocument document = dealDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!document.getDeal().getId().equals(dealId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document does not belong to this deal");
        }
        if (document.getStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document has no stored content");
        }
        try {
            InputStream content = documentStorageService.open(document.getStorageKey());
            return new DocumentDownload(document, content);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to open document");
        }
    }

    public DealDocument updateDocumentStatus(Long dealId, Long documentId, DocumentStatus status) {
        findDeal(dealId);
        DealDocument document = dealDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!document.getDeal().getId().equals(dealId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document does not belong to this deal");
        }
        document.setStatus(status);
        document.setUpdatedAt(OffsetDateTime.now());
        DealDocument savedDocument = dealDocumentRepository.save(document);
        recordActivity(savedDocument.getDeal(), "DOCUMENT_STATUS_UPDATED", document.getType() + " marked as " + document.getStatus());
        taskNotificationService.createNotification(
                savedDocument.getDeal(),
                ParticipantType.BUYER,
                savedDocument.getDeal().getBuyerEmail(),
                "Document status updated",
                document.getType() + " is now " + document.getStatus()
        );
        taskNotificationService.syncForDeal(savedDocument.getDeal());
        return savedDocument;
    }

    public Deal updateFulfillment(Long dealId, UpdateFulfillmentCommand command) {
        Deal deal = findDeal(dealId);
        if (deal.getStage() == DealStage.CANCELED || deal.getStage() == DealStage.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot update fulfillment for a closed deal");
        }
        deal.setFulfillmentScheduledAt(command.scheduledAt());
        deal.setFulfillmentLocation(command.location());
        deal.setFulfillmentNotes(blankToNull(command.notes()));
        deal.setFulfillmentStatus(command.status());
        deal.setUpdatedAt(OffsetDateTime.now());
        if (command.status() == FulfillmentStatus.READY && deal.getStage() == DealStage.DOCUMENTS_PENDING) {
            deal.setStage(DealStage.READY_FOR_HANDOFF);
        }
        Deal savedDeal = dealRepository.save(deal);
        recordActivity(savedDeal, "FULFILLMENT_UPDATED", "Fulfillment set to " + command.status() + " at " + command.location());
        taskNotificationService.createNotification(
                savedDeal,
                ParticipantType.BUYER,
                savedDeal.getBuyerEmail(),
                "Fulfillment updated",
                "Your " + savedDeal.getFulfillmentType() + " is now " + command.status() + " for " + command.location()
        );
        taskNotificationService.syncForDeal(savedDeal);
        return savedDeal;
    }

    @Transactional(readOnly = true)
    public List<DealActivity> getDealActivity(Long dealId) {
        findDeal(dealId);
        return dealActivityRepository.findByDealIdOrderByCreatedAtAsc(dealId);
    }

    @Transactional(readOnly = true)
    public DealReadiness getDealReadiness(Long dealId) {
        Deal deal = findDeal(dealId);
        List<String> blockers = new ArrayList<>();
        if (!deal.isDepositPaid()) {
            blockers.add("Deposit has not been paid");
        }
        if (hasPendingDocuments(dealId)) {
            blockers.add("Required deal documents are still pending approval");
        }
        if (deal.getFulfillmentScheduledAt() == null || deal.getFulfillmentStatus() == FulfillmentStatus.UNSCHEDULED) {
            blockers.add("Fulfillment has not been scheduled");
        }
        if (deal.getStage() != DealStage.READY_FOR_HANDOFF && deal.getStage() != DealStage.COMPLETED) {
            blockers.add("Deal has not reached the handoff stage");
        }
        boolean readyForHandoff = blockers.isEmpty();
        return new DealReadiness(
                deal.getId(),
                readyForHandoff,
                readyForHandoff && deal.getFulfillmentStatus() != FulfillmentStatus.COMPLETED,
                readyForHandoff && deal.getStage() == DealStage.COMPLETED,
                blockers
        );
    }

    @Transactional(readOnly = true)
    public List<com.stealadeal.domain.DealTask> getDealTasks(Long dealId) {
        findDeal(dealId);
        return taskNotificationService.getDealTasks(dealId);
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stage transition from " + deal.getStage() + " to " + nextStage);
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
}
