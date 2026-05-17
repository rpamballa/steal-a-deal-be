package com.stealadeal.service.esign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stealadeal.config.ESignProperties;
import com.stealadeal.domain.SigningStatus;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Self-hosted DocuSeal-backed e-signature. Selected with
 * {@code app.esign.provider=docuseal}. Base URL and API token are read
 * only from configuration (env / secrets manager) — never embedded.
 *
 * Flow: create a DocuSeal template from the PDF bytes, then create a
 * submission for the signer; the submission id is our envelope id.
 * Webhook events map DocuSeal submission states onto SigningStatus.
 *
 * NOTE: built to DocuSeal's documented REST contract; endpoint shapes
 * must be verified against a live DocuSeal instance before go-live
 * (no instance available in this environment). Webhook parsing is
 * fully offline and unit-tested.
 */
@Component("eSignProvider")
@ConditionalOnProperty(name = "app.esign.provider", havingValue = "docuseal")
public class DocusealESignProvider implements ESignProvider {

    private static final Logger log = LoggerFactory.getLogger(DocusealESignProvider.class);

    private final String baseUrl;
    private final String apiToken;
    private final String webhookSecret;
    private final String templateId;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public DocusealESignProvider(ESignProperties properties) {
        if (properties.docusealBaseUrl() == null || properties.docusealBaseUrl().isBlank()
                || properties.docusealApiToken() == null || properties.docusealApiToken().isBlank()) {
            throw new IllegalStateException(
                    "app.esign.provider=docuseal but DOCUSEAL_BASE_URL / DOCUSEAL_API_TOKEN are not set "
                            + "(supply via the secrets manager — never in source)");
        }
        this.baseUrl = properties.docusealBaseUrl().replaceAll("/+$", "");
        this.apiToken = properties.docusealApiToken();
        this.webhookSecret = properties.docusealWebhookSecret();
        this.templateId = properties.docusealTemplateId();
    }

    @Override
    public String name() {
        return "docuseal";
    }

    @Override
    public EnvelopeRef createEnvelope(CreateEnvelopeRequest request) {
        if (templateId == null || templateId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "app.esign.docuseal-template-id is not set — build the buyer-agreement "
                            + "template in the DocuSeal UI and configure its id (free DocuSeal "
                            + "has no API to upload a per-deal PDF)");
        }
        try {
            var values = mapper.createObjectNode();
            if (request.fieldValues() != null) {
                request.fieldValues().forEach((k, v) -> values.put(k, v == null ? "" : v));
            }
            var submitter = mapper.createObjectNode()
                    .put("email", request.signerEmail())
                    .put("name", request.signerName());
            submitter.set("values", values);
            String submissionBody = mapper.createObjectNode()
                    .put("template_id", Long.parseLong(templateId.trim()))
                    .put("send_email", true)
                    .set("submitters", mapper.createArrayNode().add(submitter))
                    .toString();
            JsonNode submission = send("POST", "/submissions", submissionBody);

            String submissionId = extractSubmissionId(submission);
            if (submissionId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DocuSeal submission returned no id");
            }
            return new EnvelopeRef(submissionId, SigningStatus.SENT);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "app.esign.docuseal-template-id must be numeric");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DocuSeal createEnvelope failed");
        }
    }

    @Override
    public EnvelopeRef getEnvelopeStatus(String envelopeId) {
        try {
            JsonNode submission = send("GET", "/submissions/" + envelopeId, null);
            String status = submission.path("status").asText("");
            if (status.isBlank() && submission.path("submitters").isArray()
                    && !submission.path("submitters").isEmpty()) {
                status = submission.path("submitters").get(0).path("status").asText("");
            }
            return new EnvelopeRef(envelopeId, mapStatus(status));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DocuSeal getEnvelopeStatus failed");
        }
    }

    @Override
    public void cancelEnvelope(String envelopeId) {
        try {
            send("DELETE", "/submissions/" + envelopeId, null);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("[esign/docuseal] cancelEnvelope {} failed: {}", envelopeId, e.getMessage());
        }
    }

    @Override
    public EnvelopeWebhook parseWebhookEvent(String signatureHeader, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (signatureHeader == null || !webhookSecret.equals(signatureHeader)) {
                log.warn("[esign/docuseal] webhook secret mismatch — rejecting event");
                return null;
            }
        }
        try {
            JsonNode root = mapper.readTree(rawBody);
            String eventType = root.path("event_type").asText("");
            JsonNode data = root.path("data");
            String submissionId = data.path("id").asText(null);
            String status = data.path("status").asText("");
            SigningStatus mapped = eventType.isEmpty() ? mapStatus(status) : mapEvent(eventType, status);
            if (submissionId == null || mapped == null) {
                return null;
            }
            return new EnvelopeWebhook(submissionId, mapped);
        } catch (IOException e) {
            log.warn("[esign/docuseal] webhook parse failed: {}", e.getMessage());
            return null;
        }
    }

    private SigningStatus mapEvent(String eventType, String status) {
        return switch (eventType) {
            case "submission.completed", "form.completed" -> SigningStatus.SIGNED;
            case "form.declined" -> SigningStatus.DECLINED;
            case "submission.expired" -> SigningStatus.EXPIRED;
            case "submission.archived" -> SigningStatus.CANCELED;
            case "form.viewed", "submission.created", "form.started" -> SigningStatus.SENT;
            default -> mapStatus(status);
        };
    }

    private SigningStatus mapStatus(String status) {
        return switch (status == null ? "" : status.toLowerCase()) {
            case "completed" -> SigningStatus.SIGNED;
            case "declined" -> SigningStatus.DECLINED;
            case "expired" -> SigningStatus.EXPIRED;
            case "archived", "canceled", "cancelled" -> SigningStatus.CANCELED;
            case "pending", "sent", "opened", "awaiting", "" -> SigningStatus.SENT;
            default -> SigningStatus.SENT;
        };
    }

    private String extractSubmissionId(JsonNode submission) {
        if (submission.isArray() && !submission.isEmpty()) {
            JsonNode first = submission.get(0);
            if (first.hasNonNull("submission_id")) {
                return first.get("submission_id").asText();
            }
            return first.path("id").asText(null);
        }
        return submission.path("id").asText(null);
    }

    private JsonNode send(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api" + path))
                .timeout(Duration.ofSeconds(30))
                .header("X-Auth-Token", apiToken)
                .header("Content-Type", "application/json");
        switch (method) {
            case "GET" -> b.GET();
            case "DELETE" -> b.DELETE();
            default -> b.method(method, HttpRequest.BodyPublishers.ofString(
                    body == null ? "" : body, StandardCharsets.UTF_8));
        }
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            log.warn("[esign/docuseal] {} {} -> HTTP {}", method, path, res.statusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "DocuSeal " + method + " " + path + " returned " + res.statusCode());
        }
        String payload = res.body();
        return payload == null || payload.isBlank()
                ? mapper.createObjectNode()
                : mapper.readTree(payload);
    }
}
