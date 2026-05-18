package com.stealadeal.service;

import com.stealadeal.domain.Appointment;
import com.stealadeal.domain.AppointmentStatus;
import com.stealadeal.domain.AppointmentType;
import com.stealadeal.domain.Dealer;
import com.stealadeal.domain.Lead;
import com.stealadeal.domain.LeadStatus;
import com.stealadeal.domain.UserRole;
import com.stealadeal.domain.Vehicle;
import com.stealadeal.domain.VehicleStatus;
import com.stealadeal.repository.AppointmentRepository;
import com.stealadeal.repository.DealerRepository;
import com.stealadeal.repository.LeadRepository;
import com.stealadeal.repository.VehicleRepository;
import com.stealadeal.config.VehicleImageStorageProperties;
import com.stealadeal.security.AuthenticatedUser;
import com.stealadeal.service.storage.VehicleImageStorageService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class InventoryService {

    public record DashboardMetrics(
            long dealerCount,
            long vehicleCount,
            long liveVehicleCount,
            long newLeadCount,
            long requestedAppointmentCount
    ) {
    }

    public enum InventoryUploadMode {
        CREATE_ONLY,
        UPSERT
    }

    public enum InventoryUploadRowStatus {
        CREATED,
        UPDATED,
        REJECTED
    }

    public record VehicleClassification(
            com.stealadeal.domain.BodyType bodyType,
            com.stealadeal.domain.FuelType fuelType,
            Integer combinedMpg,
            Long marketValueCents
    ) {
        public static VehicleClassification empty() {
            return new VehicleClassification(null, null, null, null);
        }
    }

    public record InventoryUploadVehicle(
            String vin,
            int modelYear,
            String make,
            String model,
            String trim,
            List<String> imageUrls,
            int mileage,
            BigDecimal price,
            VehicleStatus status,
            VehicleClassification classification
    ) {
    }

    public record InventoryUploadRowResult(
            int rowNumber,
            String vin,
            InventoryUploadRowStatus status,
            Long vehicleId,
            String message
    ) {
    }

    public record InventoryUploadResult(
            Long dealerId,
            String dealerName,
            InventoryUploadMode mode,
            int totalRows,
            int createdCount,
            int updatedCount,
            int rejectedCount,
            List<InventoryUploadRowResult> rows
    ) {
    }

    public static final int MAX_IMAGES_PER_LISTING = 10;

    public record VehicleImageDownload(InputStream content, String contentType) {
    }

    private final DealerRepository dealerRepository;
    private final VehicleRepository vehicleRepository;
    private final LeadRepository leadRepository;
    private final AppointmentRepository appointmentRepository;
    private final VehicleImageStorageService vehicleImageStorageService;
    private final VehicleImageStorageProperties vehicleImageStorageProperties;
    private final org.springframework.beans.factory.ObjectProvider<com.stealadeal.service.buyer.PriceDropWatcher>
            priceDropWatcher;

    public InventoryService(
            DealerRepository dealerRepository,
            VehicleRepository vehicleRepository,
            LeadRepository leadRepository,
            AppointmentRepository appointmentRepository,
            VehicleImageStorageService vehicleImageStorageService,
            VehicleImageStorageProperties vehicleImageStorageProperties,
            org.springframework.beans.factory.ObjectProvider<com.stealadeal.service.buyer.PriceDropWatcher>
                    priceDropWatcher
    ) {
        this.dealerRepository = dealerRepository;
        this.vehicleRepository = vehicleRepository;
        this.leadRepository = leadRepository;
        this.appointmentRepository = appointmentRepository;
        this.vehicleImageStorageService = vehicleImageStorageService;
        this.vehicleImageStorageProperties = vehicleImageStorageProperties;
        this.priceDropWatcher = priceDropWatcher;
    }

    private int maxImagesPerListing() {
        return vehicleImageStorageProperties.maxPerListing() == null
                ? MAX_IMAGES_PER_LISTING
                : vehicleImageStorageProperties.maxPerListing();
    }

    public Vehicle addVehicleImages(Long dealerId, Long vehicleId, List<MultipartFile> files) {
        Dealer dealer = findApprovedDealer(dealerId);
        Vehicle vehicle = findVehicle(vehicleId);
        if (!vehicle.getDealer().getId().equals(dealer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vehicle does not belong to this dealer");
        }
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one image is required");
        }
        int max = maxImagesPerListing();
        List<String> urls = new ArrayList<>(vehicle.getImageUrls());
        if (urls.size() + files.size() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A listing can have at most " + max + " photos (current "
                            + urls.size() + ", uploading " + files.size() + ")");
        }
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty image file");
            }
            if (file.getSize() > vehicleImageStorageProperties.maxBytes()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Image exceeds max size of " + vehicleImageStorageProperties.maxBytes() + " bytes");
            }
            String contentType = file.getContentType();
            if (contentType == null || !vehicleImageStorageProperties.allowedContentTypes().contains(contentType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported image content type: " + contentType);
            }
            try (InputStream in = file.getInputStream()) {
                VehicleImageStorageService.StoredObject stored = vehicleImageStorageService.store(
                        new VehicleImageStorageService.StoreRequest(contentType, file.getSize(), in));
                urls.add("/api/vehicles/" + vehicleId + "/photos/" + stored.storageKey());
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to store image");
            }
        }
        vehicle.setImageUrls(urls);
        return vehicleRepository.save(vehicle);
    }

    @Transactional(readOnly = true)
    public VehicleImageDownload getVehicleImage(Long vehicleId, String storageKey) {
        Vehicle vehicle = findVehicle(vehicleId);
        String suffix = "/api/vehicles/" + vehicleId + "/photos/" + storageKey;
        boolean owned = vehicle.getImageUrls().stream().anyMatch(u -> u.endsWith(suffix) || u.endsWith("/photos/" + storageKey));
        if (!owned) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found for this listing");
        }
        try {
            return new VehicleImageDownload(
                    vehicleImageStorageService.open(storageKey),
                    contentTypeForKey(storageKey));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
    }

    public Vehicle deleteVehicleImage(Long dealerId, Long vehicleId, String storageKey) {
        Dealer dealer = findApprovedDealer(dealerId);
        Vehicle vehicle = findVehicle(vehicleId);
        if (!vehicle.getDealer().getId().equals(dealer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vehicle does not belong to this dealer");
        }
        List<String> urls = new ArrayList<>(vehicle.getImageUrls());
        boolean removed = urls.removeIf(u -> u.endsWith("/photos/" + storageKey));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found for this listing");
        }
        vehicle.setImageUrls(urls);
        Vehicle saved = vehicleRepository.save(vehicle);
        try {
            vehicleImageStorageService.delete(storageKey);
        } catch (IOException ignored) {
            // best-effort: row already updated; orphaned blob can be reaped later
        }
        return saved;
    }

    private String contentTypeForKey(String key) {
        if (key.endsWith(".png")) {
            return "image/png";
        }
        if (key.endsWith(".webp")) {
            return "image/webp";
        }
        if (key.endsWith(".heic")) {
            return "image/heic";
        }
        return "image/jpeg";
    }

    @Transactional(readOnly = true)
    public List<Vehicle> getVehicles(Long dealerId, String make, String model, BigDecimal minPrice, BigDecimal maxPrice, VehicleStatus status) {
        List<VehicleStatus> statuses = status == null ? Arrays.asList(VehicleStatus.values()) : List.of(status);
        return dealerId == null
                ? vehicleRepository.findByStatusInAndMakeContainingIgnoreCaseAndModelContainingIgnoreCaseAndPriceBetween(
                statuses, make, model, minPrice, maxPrice)
                : vehicleRepository.findByStatusInAndDealerIdAndMakeContainingIgnoreCaseAndModelContainingIgnoreCaseAndPriceBetween(
                statuses, dealerId, make, model, minPrice, maxPrice);
    }

    @Transactional(readOnly = true)
    public Vehicle getVehicle(Long vehicleId) {
        return findVehicle(vehicleId);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> getDealerInventory(Long dealerId) {
        findDealer(dealerId);
        return vehicleRepository.findByDealerIdOrderByIdDesc(dealerId);
    }

    public Vehicle createVehicle(
            Long dealerId,
            String vin,
            int modelYear,
            String make,
            String model,
            String trim,
            List<String> imageUrls,
            int mileage,
            BigDecimal price,
            VehicleStatus status
    ) {
        return createVehicle(dealerId, vin, modelYear, make, model, trim, imageUrls, mileage, price, status,
                VehicleClassification.empty());
    }

    public Vehicle createVehicle(
            Long dealerId,
            String vin,
            int modelYear,
            String make,
            String model,
            String trim,
            List<String> imageUrls,
            int mileage,
            BigDecimal price,
            VehicleStatus status,
            VehicleClassification classification
    ) {
        Dealer dealer = findApprovedDealer(dealerId);
        Vehicle vehicle = new Vehicle();
        applyVehicle(vehicle, dealer, vin, modelYear, make, model, trim, imageUrls, mileage, price, status,
                classification);
        return vehicleRepository.save(vehicle);
    }

    public InventoryUploadResult uploadInventory(
            Long dealerId,
            InventoryUploadMode mode,
            List<InventoryUploadVehicle> vehicles
    ) {
        Dealer dealer = findApprovedDealer(dealerId);
        return uploadInventory(dealer, mode, vehicles);
    }

    public InventoryUploadResult uploadInventoryCsv(
            Long dealerId,
            InventoryUploadMode mode,
            MultipartFile file
    ) {
        Dealer dealer = findApprovedDealer(dealerId);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is required");
        }
        try {
            return parseCsvAndUpsert(dealer, mode, file.getInputStream());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read CSV file");
        }
    }

    /**
     * Ingest CSV content from any source (uploaded file or an automated
     * feed). Reuses the same validation, dedupe, and upsert pipeline.
     */
    public InventoryUploadResult ingestCsvStream(
            Long dealerId,
            InventoryUploadMode mode,
            java.io.InputStream content
    ) {
        Dealer dealer = findApprovedDealer(dealerId);
        return parseCsvAndUpsert(dealer, mode, content);
    }

    private InventoryUploadResult parseCsvAndUpsert(
            Dealer dealer,
            InventoryUploadMode mode,
            java.io.InputStream csvInput
    ) {
        List<InventoryUploadRowResult> rows = new ArrayList<>();
        Set<String> seenVins = new HashSet<>();
        int createdCount = 0;
        int updatedCount = 0;
        int rejectedCount = 0;

        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(csvInput, StandardCharsets.UTF_8));
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreEmptyLines(true)
                        .setTrim(true)
                        .build()
                        .parse(reader)
        ) {
            validateCsvHeaders(parser);
            for (CSVRecord record : parser) {
                int rowNumber = (int) record.getRecordNumber() + 1;
                String vin = normalizeVin(record.get("vin"));
                if (!seenVins.add(vin)) {
                    rows.add(new InventoryUploadRowResult(
                            rowNumber,
                            vin,
                            InventoryUploadRowStatus.REJECTED,
                            null,
                            "Duplicate VIN in upload payload"
                    ));
                    rejectedCount++;
                    continue;
                }

                try {
                    InventoryUploadVehicle vehicle = new InventoryUploadVehicle(
                            vin,
                            Integer.parseInt(record.get("modelYear")),
                            requiredValue(record, "make"),
                            requiredValue(record, "model"),
                            requiredValue(record, "trim"),
                            parseImageUrls(requiredValue(record, "imageUrls")),
                            Integer.parseInt(record.get("mileage")),
                            new BigDecimal(record.get("price")),
                            VehicleStatus.valueOf(requiredValue(record, "status").toUpperCase()),
                            csvClassification(record)
                    );
                    InventoryUploadRowResult rowResult = processUploadRow(dealer, mode, rowNumber, vehicle);
                    rows.add(rowResult);
                    if (rowResult.status() == InventoryUploadRowStatus.CREATED) {
                        createdCount++;
                    } else if (rowResult.status() == InventoryUploadRowStatus.UPDATED) {
                        updatedCount++;
                    } else {
                        rejectedCount++;
                    }
                } catch (IllegalArgumentException exception) {
                    rows.add(new InventoryUploadRowResult(
                            rowNumber,
                            vin,
                            InventoryUploadRowStatus.REJECTED,
                            null,
                            exception.getMessage()
                    ));
                    rejectedCount++;
                }
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read CSV file");
        }

        return new InventoryUploadResult(
                dealer.getId(),
                dealer.getName(),
                mode,
                rows.size(),
                createdCount,
                updatedCount,
                rejectedCount,
                rows
        );
    }

    private InventoryUploadResult uploadInventory(
            Dealer dealer,
            InventoryUploadMode mode,
            List<InventoryUploadVehicle> vehicles
    ) {
        List<InventoryUploadRowResult> rows = new ArrayList<>();
        Set<String> seenVins = new HashSet<>();
        int createdCount = 0;
        int updatedCount = 0;
        int rejectedCount = 0;

        for (int index = 0; index < vehicles.size(); index++) {
            InventoryUploadVehicle requestVehicle = vehicles.get(index);
            String normalizedVin = normalizeVin(requestVehicle.vin());
            if (!seenVins.add(normalizedVin)) {
                rows.add(new InventoryUploadRowResult(
                        index + 1,
                        normalizedVin,
                        InventoryUploadRowStatus.REJECTED,
                        null,
                        "Duplicate VIN in upload payload"
                ));
                rejectedCount++;
                continue;
            }

            InventoryUploadRowResult rowResult = processUploadRow(
                    dealer,
                    mode,
                    index + 1,
                    new InventoryUploadVehicle(
                            normalizedVin,
                            requestVehicle.modelYear(),
                            requestVehicle.make(),
                            requestVehicle.model(),
                            requestVehicle.trim(),
                            requestVehicle.imageUrls(),
                            requestVehicle.mileage(),
                            requestVehicle.price(),
                            requestVehicle.status(),
                            requestVehicle.classification()
                    )
            );
            rows.add(rowResult);
            if (rowResult.status() == InventoryUploadRowStatus.CREATED) {
                createdCount++;
            } else if (rowResult.status() == InventoryUploadRowStatus.UPDATED) {
                updatedCount++;
            } else {
                rejectedCount++;
            }
        }

        return new InventoryUploadResult(
                dealer.getId(),
                dealer.getName(),
                mode,
                vehicles.size(),
                createdCount,
                updatedCount,
                rejectedCount,
                rows
        );
    }

    private InventoryUploadRowResult processUploadRow(
            Dealer dealer,
            InventoryUploadMode mode,
            int rowNumber,
            InventoryUploadVehicle requestVehicle
    ) {
        String normalizedVin = normalizeVin(requestVehicle.vin());
        Vehicle existingVehicle = vehicleRepository.findByVinIgnoreCase(normalizedVin).orElse(null);
        if (existingVehicle != null && !existingVehicle.getDealer().getId().equals(dealer.getId())) {
            return new InventoryUploadRowResult(
                    rowNumber,
                    normalizedVin,
                    InventoryUploadRowStatus.REJECTED,
                    existingVehicle.getId(),
                    "VIN already belongs to another dealer"
            );
        }

        if (existingVehicle != null && mode == InventoryUploadMode.CREATE_ONLY) {
            return new InventoryUploadRowResult(
                    rowNumber,
                    normalizedVin,
                    InventoryUploadRowStatus.REJECTED,
                    existingVehicle.getId(),
                    "VIN already exists for this dealer"
            );
        }

        Vehicle targetVehicle = existingVehicle == null ? new Vehicle() : existingVehicle;
        applyVehicle(
                targetVehicle,
                dealer,
                normalizedVin,
                requestVehicle.modelYear(),
                requestVehicle.make(),
                requestVehicle.model(),
                requestVehicle.trim(),
                requestVehicle.imageUrls(),
                requestVehicle.mileage(),
                requestVehicle.price(),
                requestVehicle.status(),
                requestVehicle.classification()
        );
        Vehicle savedVehicle = vehicleRepository.save(targetVehicle);
        return new InventoryUploadRowResult(
                rowNumber,
                normalizedVin,
                existingVehicle == null ? InventoryUploadRowStatus.CREATED : InventoryUploadRowStatus.UPDATED,
                savedVehicle.getId(),
                existingVehicle == null ? "Vehicle created" : "Vehicle updated"
        );
    }

    public Vehicle updateVehicle(
            Long vehicleId,
            Long dealerId,
            String vin,
            int modelYear,
            String make,
            String model,
            String trim,
            List<String> imageUrls,
            int mileage,
            BigDecimal price,
            VehicleStatus status
    ) {
        return updateVehicle(vehicleId, dealerId, vin, modelYear, make, model, trim, imageUrls, mileage, price,
                status, VehicleClassification.empty());
    }

    public Vehicle updateVehicle(
            Long vehicleId,
            Long dealerId,
            String vin,
            int modelYear,
            String make,
            String model,
            String trim,
            List<String> imageUrls,
            int mileage,
            BigDecimal price,
            VehicleStatus status,
            VehicleClassification classification
    ) {
        Vehicle vehicle = findVehicle(vehicleId);
        Dealer dealer = findApprovedDealer(dealerId);
        applyVehicle(vehicle, dealer, vin, modelYear, make, model, trim, imageUrls, mileage, price, status,
                classification);
        return vehicleRepository.save(vehicle);
    }

    public Lead createLead(Long vehicleId, String buyerName, String buyerEmail, String buyerPhone, String message) {
        Vehicle vehicle = findVehicle(vehicleId);
        Lead lead = new Lead();
        lead.setVehicle(vehicle);
        lead.setBuyerName(buyerName);
        lead.setBuyerEmail(buyerEmail);
        lead.setBuyerPhone(buyerPhone);
        lead.setMessage(message);
        lead.setStatus(LeadStatus.NEW);
        lead.setCreatedAt(OffsetDateTime.now());
        return leadRepository.save(lead);
    }

    @Transactional(readOnly = true)
    public List<Lead> getLeads(Long vehicleId, LeadStatus status) {
        if (vehicleId != null && status != null) {
            return leadRepository.findByStatusAndVehicleId(status, vehicleId);
        }
        if (vehicleId != null) {
            return leadRepository.findByVehicleId(vehicleId);
        }
        if (status != null) {
            return leadRepository.findByStatus(status);
        }
        return leadRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Lead> getLeadsForPrincipal(AuthenticatedUser user, Long vehicleId, LeadStatus status) {
        if (user == null) {
            return List.of();
        }
        if (user.role() == UserRole.ADMIN) {
            return getLeads(vehicleId, status);
        }
        if (user.role() == UserRole.DEALER && user.dealerId() != null) {
            return leadRepository.findByVehicleDealerId(user.dealerId()).stream()
                    .filter(lead -> vehicleId == null || lead.getVehicle().getId().equals(vehicleId))
                    .filter(lead -> status == null || lead.getStatus() == status)
                    .toList();
        }
        if (user.role() == UserRole.BUYER) {
            return getLeads(vehicleId, status).stream()
                    .filter(lead -> lead.getBuyerEmail().equalsIgnoreCase(user.email()))
                    .toList();
        }
        return List.of();
    }

    public Lead updateLeadStatus(Long leadId, LeadStatus status) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
        lead.setStatus(status);
        return leadRepository.save(lead);
    }

    public Appointment createAppointment(Long vehicleId, String buyerName, String buyerEmail, AppointmentType type, OffsetDateTime scheduledAt) {
        Vehicle vehicle = findVehicle(vehicleId);
        Appointment appointment = new Appointment();
        appointment.setVehicle(vehicle);
        appointment.setBuyerName(buyerName);
        appointment.setBuyerEmail(buyerEmail);
        appointment.setType(type);
        appointment.setStatus(AppointmentStatus.REQUESTED);
        appointment.setScheduledAt(scheduledAt);
        appointment.setCreatedAt(OffsetDateTime.now());
        return appointmentRepository.save(appointment);
    }

    @Transactional(readOnly = true)
    public List<Appointment> getAppointments(Long vehicleId, AppointmentStatus status) {
        if (vehicleId != null && status != null) {
            return appointmentRepository.findByStatusAndVehicleId(status, vehicleId);
        }
        if (vehicleId != null) {
            return appointmentRepository.findByVehicleId(vehicleId);
        }
        if (status != null) {
            return appointmentRepository.findByStatus(status);
        }
        return appointmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentsForPrincipal(AuthenticatedUser user, Long vehicleId, AppointmentStatus status) {
        if (user == null) {
            return List.of();
        }
        if (user.role() == UserRole.ADMIN) {
            return getAppointments(vehicleId, status);
        }
        if (user.role() == UserRole.DEALER && user.dealerId() != null) {
            return appointmentRepository.findByVehicleDealerId(user.dealerId()).stream()
                    .filter(appointment -> vehicleId == null || appointment.getVehicle().getId().equals(vehicleId))
                    .filter(appointment -> status == null || appointment.getStatus() == status)
                    .toList();
        }
        if (user.role() == UserRole.BUYER) {
            return getAppointments(vehicleId, status).stream()
                    .filter(appointment -> appointment.getBuyerEmail().equalsIgnoreCase(user.email()))
                    .toList();
        }
        return List.of();
    }

    public Appointment updateAppointmentStatus(Long appointmentId, AppointmentStatus status) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        appointment.setStatus(status);
        return appointmentRepository.save(appointment);
    }

    @Transactional(readOnly = true)
    public DashboardMetrics getDashboardMetrics() {
        long dealerCount = dealerRepository.count();
        long vehicleCount = vehicleRepository.count();
        long liveVehicleCount = vehicleRepository.findByStatusInAndMakeContainingIgnoreCaseAndModelContainingIgnoreCaseAndPriceBetween(
                List.of(VehicleStatus.LIVE), "", "", BigDecimal.ZERO, new BigDecimal("9999999")
        ).size();
        long newLeadCount = leadRepository.findByStatus(LeadStatus.NEW).size();
        long requestedAppointmentCount = appointmentRepository.findByStatus(AppointmentStatus.REQUESTED).size();
        return new DashboardMetrics(dealerCount, vehicleCount, liveVehicleCount, newLeadCount, requestedAppointmentCount);
    }

    private Dealer findApprovedDealer(Long dealerId) {
        Dealer dealer = findDealer(dealerId);
        if (!dealer.isApproved()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dealer must be approved before publishing inventory");
        }
        return dealer;
    }

    private Dealer findDealer(Long dealerId) {
        return dealerRepository.findById(dealerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealer not found"));
    }

    private Vehicle findVehicle(Long vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
    }

    private void applyVehicle(
            Vehicle vehicle,
            Dealer dealer,
            String vin,
            int modelYear,
            String make,
            String model,
            String trim,
            List<String> imageUrls,
            int mileage,
            BigDecimal price,
            VehicleStatus status,
            VehicleClassification classification
    ) {
        if (imageUrls != null && imageUrls.size() > maxImagesPerListing()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A listing can have at most " + maxImagesPerListing() + " photos");
        }
        BigDecimal previousPrice = vehicle.getId() != null ? vehicle.getPrice() : null;
        VehicleStatus previousStatus = vehicle.getId() != null ? vehicle.getStatus() : null;
        vehicle.setDealer(dealer);
        vehicle.setVin(vin.toUpperCase());
        vehicle.setModelYear(modelYear);
        vehicle.setMake(make);
        vehicle.setModel(model);
        vehicle.setTrim(trim);
        vehicle.setImageUrls(imageUrls);
        vehicle.setMileage(mileage);
        vehicle.setPrice(price);
        vehicle.setStatus(status);
        vehicle.setLastSeenAt(OffsetDateTime.now());
        if (classification != null) {
            vehicle.setBodyType(classification.bodyType());
            vehicle.setFuelType(classification.fuelType());
            vehicle.setCombinedMpg(classification.combinedMpg());
            vehicle.setMarketValueCents(classification.marketValueCents());
        }
        if (previousPrice != null
                && price != null
                && price.compareTo(previousPrice) < 0
                && status == VehicleStatus.LIVE) {
            final BigDecimal from = previousPrice;
            final BigDecimal to = price;
            priceDropWatcher.ifAvailable(w -> w.onPriceDrop(vehicle, from, to));
        }
    }

    private VehicleClassification csvClassification(CSVRecord record) {
        return new VehicleClassification(
                parseEnum(csvOptional(record, "bodyType"), com.stealadeal.domain.BodyType.class),
                parseEnum(csvOptional(record, "fuelType"), com.stealadeal.domain.FuelType.class),
                parseInteger(csvOptional(record, "combinedMpg")),
                parseLong(csvOptional(record, "marketValueCents"))
        );
    }

    private String csvOptional(CSVRecord record, String column) {
        if (!record.isMapped(column) || !record.isSet(column)) {
            return null;
        }
        String value = record.get(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void validateCsvHeaders(CSVParser parser) {
        List<String> requiredHeaders = List.of("vin", "modelYear", "make", "model", "trim", "imageUrls", "mileage", "price", "status");
        for (String header : requiredHeaders) {
            if (!parser.getHeaderMap().containsKey(header)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV header missing required column: " + header);
            }
        }
    }

    private String requiredValue(CSVRecord record, String columnName) {
        String value = record.get(columnName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required value for " + columnName);
        }
        return value.trim();
    }

    private String normalizeVin(String vin) {
        if (vin == null || vin.isBlank()) {
            throw new IllegalArgumentException("Missing required value for vin");
        }
        return vin.trim().toUpperCase();
    }

    private List<String> parseImageUrls(String rawImageUrls) {
        List<String> imageUrls = Arrays.stream(rawImageUrls.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (imageUrls.isEmpty()) {
            throw new IllegalArgumentException("Missing required value for imageUrls");
        }
        return imageUrls;
    }
}
