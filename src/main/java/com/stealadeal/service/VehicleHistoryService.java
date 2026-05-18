package com.stealadeal.service;

import com.stealadeal.domain.HistoryReportSource;
import com.stealadeal.domain.Vehicle;
import com.stealadeal.domain.VehicleHistoryReport;
import com.stealadeal.repository.VehicleHistoryReportRepository;
import com.stealadeal.repository.VehicleRepository;
import com.stealadeal.service.storage.DocumentStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Buyer-facing vehicle history. The contract is intentionally honest:
 * when no report is on file the endpoint returns {@code available:false}
 * (HTTP 200) so the UI can render the "ask the dealer" state without an
 * error. Reports are dealer-uploaded PDFs today; the same shape supports
 * a third-party VIN provider when one is integrated.
 */
@Service
@Transactional
public class VehicleHistoryService {

    /** Hard cap for an uploaded history PDF (~15MB). */
    static final long MAX_REPORT_BYTES = 15L * 1024 * 1024;
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final VehicleRepository vehicleRepository;
    private final VehicleHistoryReportRepository historyReportRepository;
    private final DocumentStorageService documentStorageService;

    public VehicleHistoryService(
            VehicleRepository vehicleRepository,
            VehicleHistoryReportRepository historyReportRepository,
            DocumentStorageService documentStorageService
    ) {
        this.vehicleRepository = vehicleRepository;
        this.historyReportRepository = historyReportRepository;
        this.documentStorageService = documentStorageService;
    }

    public record Summary(
            Integer ownerCount,
            Integer accidentCount,
            String titleBrand,
            Integer lastReportedOdometer,
            Boolean odometerRollbackSuspected,
            Integer openRecallCount,
            Integer serviceRecordCount
    ) {
    }

    public record ReportView(
            long vehicleId,
            boolean available,
            HistoryReportSource source,
            String providerName,
            String reportUrl,
            OffsetDateTime generatedAt,
            Summary summary
    ) {
    }

    public record ReportDownload(InputStream content, String contentType) {
    }

    @Transactional(readOnly = true)
    public ReportView getReport(Long vehicleId) {
        // 404 ONLY when the vehicle itself is unknown.
        findVehicle(vehicleId);
        return historyReportRepository.findByVehicleId(vehicleId)
                .map(report -> new ReportView(
                        vehicleId,
                        true,
                        report.getSource(),
                        report.getProviderName(),
                        reportUrl(report),
                        report.getGeneratedAt(),
                        new Summary(
                                report.getOwnerCount(),
                                report.getAccidentCount(),
                                report.getTitleBrand(),
                                report.getLastReportedOdometer(),
                                report.getOdometerRollbackSuspected(),
                                report.getOpenRecallCount(),
                                report.getServiceRecordCount()
                        )
                ))
                .orElseGet(() -> new ReportView(vehicleId, false, null, null, null, null, null));
    }

    public ReportView uploadDealerReport(Long dealerId, Long vehicleId, MultipartFile file) {
        Vehicle vehicle = findVehicle(vehicleId);
        if (!vehicle.getDealer().getId().equals(dealerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vehicle does not belong to this dealer");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report file is required");
        }
        if (file.getSize() > MAX_REPORT_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Report exceeds maximum allowed size of " + MAX_REPORT_BYTES + " bytes");
        }
        String contentType = file.getContentType();
        if (contentType == null || !PDF_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "History report must be a PDF (got " + contentType + ")");
        }

        DocumentStorageService.StoredObject stored;
        try (InputStream content = file.getInputStream()) {
            stored = documentStorageService.store(new DocumentStorageService.StoreRequest(
                    PDF_CONTENT_TYPE, file.getSize(), content));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store history report");
        }

        VehicleHistoryReport report = historyReportRepository.findByVehicleId(vehicleId)
                .orElseGet(() -> {
                    VehicleHistoryReport created = new VehicleHistoryReport();
                    created.setVehicle(vehicle);
                    return created;
                });
        String oldStorageKey = report.getStorageKey();
        report.setSource(HistoryReportSource.DEALER_UPLOAD);
        report.setProviderName(null);
        report.setStorageKey(stored.storageKey());
        report.setExternalReportUrl(null);
        report.setGeneratedAt(OffsetDateTime.now());
        VehicleHistoryReport saved = historyReportRepository.save(report);

        if (oldStorageKey != null && !oldStorageKey.equals(stored.storageKey())) {
            try {
                documentStorageService.delete(oldStorageKey);
            } catch (IOException ignored) {
                // best-effort cleanup of the superseded copy
            }
        }

        return new ReportView(
                vehicleId,
                true,
                saved.getSource(),
                saved.getProviderName(),
                reportUrl(saved),
                saved.getGeneratedAt(),
                new Summary(
                        saved.getOwnerCount(),
                        saved.getAccidentCount(),
                        saved.getTitleBrand(),
                        saved.getLastReportedOdometer(),
                        saved.getOdometerRollbackSuspected(),
                        saved.getOpenRecallCount(),
                        saved.getServiceRecordCount()
                )
        );
    }

    @Transactional(readOnly = true)
    public ReportDownload downloadReport(Long vehicleId) {
        findVehicle(vehicleId);
        VehicleHistoryReport report = historyReportRepository.findByVehicleId(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No history report on file for this vehicle"));
        if (report.getStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "History report has no downloadable document");
        }
        try {
            return new ReportDownload(documentStorageService.open(report.getStorageKey()), PDF_CONTENT_TYPE);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "History report document not found");
        }
    }

    private String reportUrl(VehicleHistoryReport report) {
        if (report.getStorageKey() != null) {
            return "/api/vehicles/" + report.getVehicle().getId() + "/history/report";
        }
        return report.getExternalReportUrl();
    }

    private Vehicle findVehicle(Long vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
    }
}
