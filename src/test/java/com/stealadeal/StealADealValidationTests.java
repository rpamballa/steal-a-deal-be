package com.stealadeal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stealadeal.repository.DealDocumentRepository;
import com.stealadeal.repository.DealRepository;
import com.stealadeal.repository.DealTaskRepository;
import com.stealadeal.repository.DealerRepository;
import com.stealadeal.repository.VehicleRepository;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class StealADealValidationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealerRepository dealerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private DealTaskRepository dealTaskRepository;

    @Autowired
    private DealDocumentRepository dealDocumentRepository;

    @Test
    void inventoryUploadRejectsUnapprovedDealer() throws Exception {
        long dealerId = createDealer("Pending Dealer", "CA-55555");
        String adminToken = login("admin@stealadeal.local", "Admin123!");

        mockMvc.perform(post("/api/dealers/" + dealerId + "/inventory-upload")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "CREATE_ONLY",
                                  "vehicles": [
                                    {
                                      "vin": "1FTFW1E50NFA12345",
                                      "modelYear": 2022,
                                      "make": "Ford",
                                      "model": "F-150",
                                      "trim": "Lariat",
                                      "imageUrls": ["https://example.com/f150-front.jpg"],
                                      "mileage": 18300,
                                      "price": 43995.00,
                                      "status": "LIVE"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dealer must be approved before publishing inventory"));
    }

    @Test
    void inventoryCsvUploadRejectsMissingRequiredHeader() throws Exception {
        long dealerId = createAndApproveDealer();
        String dealerToken = registerDealerUser(dealerId, "dealer+" + dealerId + "@example.com");

        MockMultipartFile csvFile = new MockMultipartFile(
                "file",
                "inventory.csv",
                "text/csv",
                """
                        vin,modelYear,make,model,trim,imageUrls,mileage,status
                        1C4RJFBG8NCB12345,2022,Jeep,Grand Cherokee,Limited,https://example.com/jeep-front.jpg,21450,LIVE
                        """.getBytes()
        );

        mockMvc.perform(multipart("/api/dealers/" + dealerId + "/inventory-upload")
                        .file(csvFile)
                        .param("mode", "CREATE_ONLY")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV header missing required column: price"));
    }

    @Test
    void dealReadinessShowsBlockersUntilDepositAndFulfillmentAreHandled() throws Exception {
        long dealerId = createAndApproveDealer();
        long vehicleId = createVehicle(dealerId);
        String buyerEmail = "buyer+" + System.nanoTime() + "@example.com";
        String buyerToken = registerBuyerUser(buyerEmail);

        mockMvc.perform(post("/api/deals")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "buyerName": "Taylor Reed",
                                  "buyerEmail": "%s",
                                  "buyerPhone": "4085550199",
                                  "buyerAddressLine1": "10 Main Street",
                                  "buyerAddressLine2": "Apt 4B",
                                  "buyerCity": "San Jose",
                                  "buyerState": "CA",
                                  "buyerPostalCode": "95112",
                                  "fulfillmentType": "HOME_DELIVERY",
                                  "tradeIn": false,
                                  "tradeInVin": null,
                                  "tradeInMileage": null,
                                  "tradeInOffer": 0.00,
                                  "deliveryFee": 299.00,
                                  "discountAmount": 100.00
                                }
                                """.formatted(vehicleId, buyerEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("INITIATED"));

        long dealId = dealRepository.findAll().stream()
                .max(Comparator.comparing(deal -> deal.getId()))
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/api/deals/" + dealId + "/deposit")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 100.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Deposit amount is below required minimum"));

        mockMvc.perform(get("/api/deals/" + dealId + "/readiness")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyForHandoff").value(false))
                .andExpect(jsonPath("$.blockers[0]").value("Deposit has not been paid"))
                .andExpect(jsonPath("$.blockers[1]").value("Required deal documents are still pending approval"))
                .andExpect(jsonPath("$.blockers[2]").value("Fulfillment has not been scheduled"));
    }

    @Test
    void dealerPortalEndpointsReturnNotFoundForUnknownDealer() throws Exception {
        mockMvc.perform(get("/api/dealers/99/portal"))
                .andExpect(status().isUnauthorized());
        String adminToken = login("admin@stealadeal.local", "Admin123!");
        mockMvc.perform(get("/api/dealers/99/portal/subscription")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/dealers/99/portal/invoices")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/dealers/99/portal/documents")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void activatingSubscriptionCreatesInvoiceForDealerWithoutBillingHistory() throws Exception {
        long dealerId = createAndApproveDealer();
        String dealerToken = registerDealerUser(dealerId, "dealer+" + dealerId + "@example.com");

        mockMvc.perform(patch("/api/dealers/" + dealerId + "/portal/subscription")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plan": "PERFORMANCE",
                                  "status": "ACTIVE",
                                  "autoRenew": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PERFORMANCE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.monthlyPrice").value(1499.00));

        mockMvc.perform(get("/api/dealers/" + dealerId + "/portal/invoices")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].amount").value(1499.00));
    }

    @Test
    void documentUploadStoresContentAndDownloadReturnsBytes() throws Exception {
        long dealerId = createAndApproveDealer();
        long vehicleId = createVehicle(dealerId);
        String buyerEmail = "buyer+" + System.nanoTime() + "@example.com";
        String buyerToken = registerBuyerUser(buyerEmail);

        mockMvc.perform(post("/api/deals")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "buyerName": "Doc Buyer",
                                  "buyerEmail": "%s",
                                  "buyerPhone": "4085550100",
                                  "buyerAddressLine1": "1 Test Way",
                                  "buyerCity": "San Jose",
                                  "buyerState": "CA",
                                  "buyerPostalCode": "95112",
                                  "fulfillmentType": "PICKUP",
                                  "tradeIn": false,
                                  "tradeInOffer": 0.00,
                                  "deliveryFee": 0.00,
                                  "discountAmount": 0.00
                                }
                                """.formatted(vehicleId, buyerEmail)))
                .andExpect(status().isCreated());

        long dealId = dealRepository.findAll().stream()
                .max(Comparator.comparing(deal -> deal.getId()))
                .orElseThrow()
                .getId();
        var doc = dealDocumentRepository.findByDealId(dealId).stream()
                .filter(d -> d.getType().name().equals("DRIVER_LICENSE"))
                .findFirst()
                .orElseThrow();

        byte[] payload = "%PDF-1.4 fake content".getBytes();
        org.springframework.mock.web.MockMultipartFile pdf = new org.springframework.mock.web.MockMultipartFile(
                "file", "license.pdf", "application/pdf", payload);

        mockMvc.perform(multipart("/api/deals/" + dealId + "/documents/" + doc.getId() + "/upload")
                        .file(pdf)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.fileName").value("license.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(payload.length))
                .andExpect(jsonPath("$.hasContent").value(true));

        mockMvc.perform(get("/api/deals/" + dealId + "/documents/" + doc.getId() + "/download")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"license.pdf\""))
                .andExpect(mvcResult ->
                        org.junit.jupiter.api.Assertions.assertArrayEquals(payload, mvcResult.getResponse().getContentAsByteArray()));
    }

    @Test
    void vinOnlyCreateEnrichesMakeModelYearFromDecoder() throws Exception {
        long dealerId = createAndApproveDealer();
        String dealerToken = registerDealerUser(dealerId, "dealer-vin+" + dealerId + "@example.com");
        String uniqueVin = "1HGCM82633A20" + String.format("%04d", (int) (System.nanoTime() % 10000));

        // No make/model/modelYear supplied — the stub decoder fills them.
        mockMvc.perform(post("/api/dealers/" + dealerId + "/inventory/vin")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vin": "%s",
                                  "imageUrls": ["https://example.com/x.jpg"],
                                  "mileage": 15000,
                                  "price": 24995.00,
                                  "status": "LIVE"
                                }
                                """.formatted(uniqueVin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.make").value("Honda"))
                .andExpect(jsonPath("$.model").value("Accord"))
                .andExpect(jsonPath("$.modelYear").value(2022))
                .andExpect(jsonPath("$.trim").value("EX"))
                .andExpect(jsonPath("$.status").value("LIVE"));

        // Caller-provided value wins over the decode.
        String vin2 = "1HGCM82633A21" + String.format("%04d", (int) (System.nanoTime() % 10000));
        mockMvc.perform(post("/api/dealers/" + dealerId + "/inventory/vin")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vin": "%s",
                                  "make": "Acura",
                                  "imageUrls": ["https://example.com/y.jpg"],
                                  "mileage": 9000,
                                  "price": 31000.00,
                                  "status": "LIVE"
                                }
                                """.formatted(vin2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.make").value("Acura"))
                .andExpect(jsonPath("$.model").value("Accord"));
    }

    @Test
    void documentUploadRejectsUnsupportedContentType() throws Exception {
        long dealerId = createAndApproveDealer();
        long vehicleId = createVehicle(dealerId);
        String buyerEmail = "buyer+" + System.nanoTime() + "@example.com";
        String buyerToken = registerBuyerUser(buyerEmail);

        mockMvc.perform(post("/api/deals")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "buyerName": "Doc Buyer",
                                  "buyerEmail": "%s",
                                  "buyerPhone": "4085550100",
                                  "buyerAddressLine1": "1 Test Way",
                                  "buyerCity": "San Jose",
                                  "buyerState": "CA",
                                  "buyerPostalCode": "95112",
                                  "fulfillmentType": "PICKUP",
                                  "tradeIn": false,
                                  "tradeInOffer": 0.00,
                                  "deliveryFee": 0.00,
                                  "discountAmount": 0.00
                                }
                                """.formatted(vehicleId, buyerEmail)))
                .andExpect(status().isCreated());

        long dealId = dealRepository.findAll().stream()
                .max(Comparator.comparing(deal -> deal.getId()))
                .orElseThrow()
                .getId();
        var doc = dealDocumentRepository.findByDealId(dealId).stream()
                .findFirst()
                .orElseThrow();

        org.springframework.mock.web.MockMultipartFile bad = new org.springframework.mock.web.MockMultipartFile(
                "file", "evil.exe", "application/x-msdownload", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/deals/" + dealId + "/documents/" + doc.getId() + "/upload")
                        .file(bad)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void billingWebhookEndpointIsPublicAndAcksFromConfiguredProvider() throws Exception {
        mockMvc.perform(post("/api/webhooks/billing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt_test\",\"type\":\"invoice.paid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.provider").value("stub"));
    }

    @Test
    void fAndIProductAttachesToDealAndComputesRevenueShare() throws Exception {
        String adminToken = login("admin@stealadeal.local", "Admin123!");
        long dealerId = createAndApproveDealer();
        long vehicleId = createVehicle(dealerId);
        String buyerEmail = "buyer+" + System.nanoTime() + "@example.com";
        String buyerToken = registerBuyerUser(buyerEmail);
        String dealerToken = registerDealerUser(dealerId, "dealer-fni+" + dealerId + "@example.com");

        MvcResult productResult = mockMvc.perform(post("/api/fni/products")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "EXTENDED_WARRANTY",
                                  "name": "5yr / 60k Powertrain",
                                  "retailPrice": 2500.00,
                                  "revenueShareRate": 0.07
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        long productId = Long.parseLong(extractNumber(productResult.getResponse().getContentAsString(), "id"));

        // dealer can list active catalog
        mockMvc.perform(get("/api/fni/products").header("Authorization", bearer(dealerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("5yr / 60k Powertrain"));

        mockMvc.perform(post("/api/deals")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "buyerName": "FnI Buyer",
                                  "buyerEmail": "%s",
                                  "buyerPhone": "4085550155",
                                  "buyerAddressLine1": "1 FnI Way",
                                  "buyerCity": "San Jose",
                                  "buyerState": "CA",
                                  "buyerPostalCode": "95112",
                                  "fulfillmentType": "PICKUP",
                                  "tradeIn": false,
                                  "tradeInOffer": 0.00,
                                  "deliveryFee": 0.00,
                                  "discountAmount": 0.00
                                }
                                """.formatted(vehicleId, buyerEmail)))
                .andExpect(status().isCreated());
        long dealId = dealRepository.findAll().stream()
                .max(Comparator.comparing(deal -> deal.getId()))
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/api/deals/" + dealId + "/fni")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(2500.00))
                .andExpect(jsonPath("$.revenueShareAmount").value(175.00));

        mockMvc.perform(get("/api/deals/" + dealId + "/fni")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRetail").value(2500.00))
                .andExpect(jsonPath("$.totalPlatformRevenue").value(175.00))
                .andExpect(jsonPath("$.items.length()").value(1));

        // non-admin cannot create catalog products
        mockMvc.perform(post("/api/fni/products")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "GAP_INSURANCE",
                                  "name": "GAP",
                                  "retailPrice": 800.00,
                                  "revenueShareRate": 0.10
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private String extractNumber(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            return "0";
        }
        start += marker.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)))) {
            end++;
        }
        return json.substring(start, end);
    }

    @Test
    void auditTrailRecordsDealerApprovalAndIsAdminOnly() throws Exception {
        long dealerId = createAndApproveDealer();
        String adminToken = login("admin@stealadeal.local", "Admin123!");
        String dealerToken = registerDealerUser(dealerId, "dealer-audit+" + dealerId + "@example.com");

        mockMvc.perform(get("/api/audit")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='DEALER_APPROVAL_CHANGED')]").isNotEmpty());

        mockMvc.perform(get("/api/audit")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void platformFeeIsProjectedBeforeCompletionAndSettledOnCompletion() throws Exception {
        long dealerId = createAndApproveDealer();
        long vehicleId = createVehicle(dealerId);
        String buyerEmail = "buyer+" + System.nanoTime() + "@example.com";
        String buyerToken = registerBuyerUser(buyerEmail);
        String dealerToken = registerDealerUser(dealerId, "dealer-fee+" + dealerId + "@example.com");

        mockMvc.perform(post("/api/deals")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "buyerName": "Fee Buyer",
                                  "buyerEmail": "%s",
                                  "buyerPhone": "4085550144",
                                  "buyerAddressLine1": "1 Fee Way",
                                  "buyerCity": "San Jose",
                                  "buyerState": "CA",
                                  "buyerPostalCode": "95112",
                                  "fulfillmentType": "PICKUP",
                                  "tradeIn": false,
                                  "tradeInOffer": 0.00,
                                  "deliveryFee": 0.00,
                                  "discountAmount": 0.00
                                }
                                """.formatted(vehicleId, buyerEmail)))
                .andExpect(status().isCreated());

        long dealId = dealRepository.findAll().stream()
                .max(Comparator.comparing(deal -> deal.getId()))
                .orElseThrow()
                .getId();

        // 28995.00 * 0.0075 = 217.4625 -> 217.46
        mockMvc.perform(get("/api/deals/" + dealId + "/platform-fee")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settled").value(false))
                .andExpect(jsonPath("$.feeAmount").value(217.46));

        // Drive the deal to COMPLETED
        mockMvc.perform(patch("/api/deals/" + dealId + "/stage").header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"OFFER_SENT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/deals/" + dealId + "/stage").header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"BUYER_CONFIRMED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/deals/" + dealId + "/deposit").header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":500.00}"))
                .andExpect(status().isOk());
        var documents = dealDocumentRepository.findByDealId(dealId);
        for (var d : documents) {
            mockMvc.perform(patch("/api/deals/" + dealId + "/documents/" + d.getId() + "/status")
                            .header("Authorization", bearer(dealerToken))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(patch("/api/deals/" + dealId + "/stage").header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"DOCUMENTS_PENDING\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/deals/" + dealId + "/stage").header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"READY_FOR_HANDOFF\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/deals/" + dealId + "/stage").header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformFeeSettled").value(true))
                .andExpect(jsonPath("$.platformFeeAmount").value(217.46));

        mockMvc.perform(get("/api/deals/" + dealId + "/platform-fee")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settled").value(true))
                .andExpect(jsonPath("$.chargeId").isNotEmpty());
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.stealadeal.repository.NotificationRepository notificationRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.stealadeal.service.NotificationOutboxProcessor notificationOutboxProcessor;

    @org.springframework.beans.factory.annotation.Autowired
    private com.stealadeal.service.DealerOnboardingProcessor dealerOnboardingProcessor;

    @Test
    void onboardingEndpointReflectsDerivedState() throws Exception {
        long dealerId = createAndApproveDealer();
        String dealerToken = registerDealerUser(dealerId, "dealer-onb+" + dealerId + "@example.com");

        mockMvc.perform(patch("/api/dealers/" + dealerId + "/portal/subscription")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":"PERFORMANCE","status":"ACTIVE","autoRenew":true}
                                """))
                .andExpect(status().isOk());

        long vehicleId = createVehicle(dealerId);
        String buyerEmail = "buyer+" + System.nanoTime() + "@example.com";
        String buyerToken = registerBuyerUser(buyerEmail);
        mockMvc.perform(post("/api/deals")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "buyerName": "Onb Buyer",
                                  "buyerEmail": "%s",
                                  "buyerPhone": "4085550166",
                                  "buyerAddressLine1": "1 Onb Way",
                                  "buyerCity": "San Jose",
                                  "buyerState": "CA",
                                  "buyerPostalCode": "95112",
                                  "fulfillmentType": "PICKUP",
                                  "tradeIn": false,
                                  "tradeInOffer": 0.00,
                                  "deliveryFee": 0.00,
                                  "discountAmount": 0.00
                                }
                                """.formatted(vehicleId, buyerEmail)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/dealers/" + dealerId + "/onboarding")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("FIRST_DEAL"))
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.nextActionStage").value("ACTIVATED"))
                .andExpect(jsonPath("$.approvedAt").isNotEmpty())
                .andExpect(jsonPath("$.subscriptionActiveAt").isNotEmpty())
                .andExpect(jsonPath("$.inventoryLiveAt").isNotEmpty())
                .andExpect(jsonPath("$.firstDealAt").isNotEmpty());
    }

    @Test
    void stuckDealerIsAutoNudgedThroughNotificationOutbox() throws Exception {
        long dealerId = createAndApproveDealer();
        // Approved but no dealer login yet -> blocking stage USER_CREATED.
        // Test profile sets stale-hours=0 so a nudge fires immediately.
        dealerOnboardingProcessor.runOnce();

        java.util.List<com.stealadeal.domain.Notification> dealerNotes =
                notificationRepository.findByRecipientTypeAndRecipientReferenceOrderByCreatedAtDesc(
                        com.stealadeal.domain.ParticipantType.DEALER, String.valueOf(dealerId));

        org.junit.jupiter.api.Assertions.assertTrue(
                dealerNotes.stream().anyMatch(n ->
                        "Finish setting up your StealADeal portal".equals(n.getTitle())
                                && n.getMessage().contains("Create your dealer login")),
                "expected an onboarding nudge for the stuck dealer");
    }

    @Test
    void pendingNotificationIsDeliveredByOutboxProcessor() throws Exception {
        com.stealadeal.domain.Notification pending = new com.stealadeal.domain.Notification();
        pending.setRecipientType(com.stealadeal.domain.ParticipantType.BUYER);
        pending.setRecipientReference("outbox-buyer@example.com");
        pending.setTitle("Pending delivery");
        pending.setMessage("This row was left PENDING by a failed inline attempt.");
        pending.setRead(false);
        pending.setCreatedAt(java.time.OffsetDateTime.now());
        pending.setDispatchStatus(com.stealadeal.domain.NotificationDispatchStatus.PENDING);
        pending.setDispatchAttempts(0);
        com.stealadeal.domain.Notification savedPending = notificationRepository.save(pending);

        int delivered = notificationOutboxProcessor.drainOutbox();
        org.junit.jupiter.api.Assertions.assertTrue(delivered >= 1);

        com.stealadeal.domain.Notification refreshed =
                notificationRepository.findById(savedPending.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(
                com.stealadeal.domain.NotificationDispatchStatus.DISPATCHED, refreshed.getDispatchStatus());
        org.junit.jupiter.api.Assertions.assertEquals("email,sms", refreshed.getDispatchChannels());
        org.junit.jupiter.api.Assertions.assertNotNull(refreshed.getDispatchedAt());
    }

    @Test
    void notificationsAreDispatchedOnExternalChannels() throws Exception {
        long dealerId = createAndApproveDealer();
        long vehicleId = createVehicle(dealerId);
        String buyerEmail = "buyer+" + System.nanoTime() + "@example.com";
        String buyerToken = registerBuyerUser(buyerEmail);
        String dealerToken = registerDealerUser(dealerId, "dealer-notify+" + dealerId + "@example.com");

        mockMvc.perform(post("/api/deals")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "buyerName": "Notify Buyer",
                                  "buyerEmail": "%s",
                                  "buyerPhone": "4085550133",
                                  "buyerAddressLine1": "1 Notify Way",
                                  "buyerCity": "San Jose",
                                  "buyerState": "CA",
                                  "buyerPostalCode": "95112",
                                  "fulfillmentType": "PICKUP",
                                  "tradeIn": false,
                                  "tradeInOffer": 0.00,
                                  "deliveryFee": 0.00,
                                  "discountAmount": 0.00
                                }
                                """.formatted(vehicleId, buyerEmail)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(dealerToken))
                        .param("recipientType", "DEALER")
                        .param("recipientReference", String.valueOf(dealerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dispatchedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].dispatchChannels").value("email"));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(buyerToken))
                        .param("recipientType", "BUYER")
                        .param("recipientReference", buyerEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dispatchChannels").value("email,sms"));
    }

    @Test
    void depositIntentConfirmedViaWebhookMarksDealPaid() throws Exception {
        long dealerId = createAndApproveDealer();
        long vehicleId = createVehicle(dealerId);
        String buyerEmail = "buyer+" + System.nanoTime() + "@example.com";
        String buyerToken = registerBuyerUser(buyerEmail);

        mockMvc.perform(post("/api/deals")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "buyerName": "Deposit Buyer",
                                  "buyerEmail": "%s",
                                  "buyerPhone": "4085550122",
                                  "buyerAddressLine1": "1 Deposit Way",
                                  "buyerCity": "San Jose",
                                  "buyerState": "CA",
                                  "buyerPostalCode": "95112",
                                  "fulfillmentType": "PICKUP",
                                  "tradeIn": false,
                                  "tradeInOffer": 0.00,
                                  "deliveryFee": 0.00,
                                  "discountAmount": 0.00
                                }
                                """.formatted(vehicleId, buyerEmail)))
                .andExpect(status().isCreated());

        long dealId = dealRepository.findAll().stream()
                .max(Comparator.comparing(deal -> deal.getId()))
                .orElseThrow()
                .getId();

        MvcResult intentResult = mockMvc.perform(post("/api/deals/" + dealId + "/deposit/intent")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUIRES_PAYMENT"))
                .andExpect(jsonPath("$.intentId").isNotEmpty())
                .andReturn();
        String intentId = extractField(intentResult.getResponse().getContentAsString(), "intentId");

        mockMvc.perform(get("/api/deals/" + dealId)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositPaid").value(false));

        mockMvc.perform(post("/api/webhooks/billing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intentId\":\"" + intentId + "\",\"status\":\"SUCCEEDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        mockMvc.perform(get("/api/deals/" + dealId)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositPaid").value(true))
                .andExpect(jsonPath("$.stage").value("DEPOSIT_PAID"));
    }

    @Test
    void documentSignatureFlowEndsWithSignedAndApprovedStatus() throws Exception {
        long dealerId = createAndApproveDealer();
        long vehicleId = createVehicle(dealerId);
        String buyerEmail = "buyer+" + System.nanoTime() + "@example.com";
        String buyerToken = registerBuyerUser(buyerEmail);

        mockMvc.perform(post("/api/deals")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "buyerName": "Sign Buyer",
                                  "buyerEmail": "%s",
                                  "buyerPhone": "4085550111",
                                  "buyerAddressLine1": "1 Sign Way",
                                  "buyerCity": "San Jose",
                                  "buyerState": "CA",
                                  "buyerPostalCode": "95112",
                                  "fulfillmentType": "PICKUP",
                                  "tradeIn": false,
                                  "tradeInOffer": 0.00,
                                  "deliveryFee": 0.00,
                                  "discountAmount": 0.00
                                }
                                """.formatted(vehicleId, buyerEmail)))
                .andExpect(status().isCreated());

        long dealId = dealRepository.findAll().stream()
                .max(Comparator.comparing(deal -> deal.getId()))
                .orElseThrow()
                .getId();
        var buyerAgreement = dealDocumentRepository.findByDealId(dealId).stream()
                .filter(d -> d.getType().name().equals("BUYER_AGREEMENT"))
                .findFirst()
                .orElseThrow();

        org.springframework.mock.web.MockMultipartFile pdf = new org.springframework.mock.web.MockMultipartFile(
                "file", "buyer-agreement.pdf", "application/pdf", "agreement bytes".getBytes());

        mockMvc.perform(multipart("/api/deals/" + dealId + "/documents/" + buyerAgreement.getId() + "/upload")
                        .file(pdf)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk());

        MvcResult signResult = mockMvc.perform(post("/api/deals/" + dealId + "/documents/" + buyerAgreement.getId() + "/sign")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signingStatus").value("SENT"))
                .andExpect(jsonPath("$.signingEnvelopeId").isNotEmpty())
                .andReturn();

        String envelopeId = extractField(signResult.getResponse().getContentAsString(), "signingEnvelopeId");

        mockMvc.perform(post("/api/webhooks/esign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"envelopeId\":\"" + envelopeId + "\",\"status\":\"SIGNED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        var refreshed = dealDocumentRepository.findById(buyerAgreement.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("SIGNED", refreshed.getSigningStatus().name());
        org.junit.jupiter.api.Assertions.assertEquals("APPROVED", refreshed.getStatus().name());
    }

    private String extractField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    @Test
    void unauthenticatedRequestToProtectedEndpointReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/deals"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/appointments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dealerCannotListDealsBelongingToAnotherDealer() throws Exception {
        long dealerAId = createAndApproveDealer();
        long dealerBId = createAndApproveDealer();
        long vehicleBId = createVehicle(dealerBId);
        String buyerEmail = "buyer+" + System.nanoTime() + "@example.com";
        String buyerToken = registerBuyerUser(buyerEmail);

        mockMvc.perform(post("/api/deals")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "buyerName": "Cross Tenant Buyer",
                                  "buyerEmail": "%s",
                                  "buyerPhone": "4085550199",
                                  "buyerAddressLine1": "10 Main Street",
                                  "buyerCity": "San Jose",
                                  "buyerState": "CA",
                                  "buyerPostalCode": "95112",
                                  "fulfillmentType": "PICKUP",
                                  "tradeIn": false,
                                  "tradeInOffer": 0.00,
                                  "deliveryFee": 0.00,
                                  "discountAmount": 0.00
                                }
                                """.formatted(vehicleBId, buyerEmail)))
                .andExpect(status().isCreated());

        String dealerAToken = registerDealerUser(dealerAId, "dealer-a+" + dealerAId + "@example.com");
        mockMvc.perform(get("/api/deals")
                        .header("Authorization", bearer(dealerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void buyerCannotUpdateDealerAssignedTask() throws Exception {
        long dealerId = createAndApproveDealer();
        long vehicleId = createVehicle(dealerId);
        String buyerEmail = "buyer+" + System.nanoTime() + "@example.com";
        String buyerToken = registerBuyerUser(buyerEmail);

        mockMvc.perform(post("/api/deals")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "buyerName": "Taylor Reed",
                                  "buyerEmail": "%s",
                                  "buyerPhone": "4085550199",
                                  "buyerAddressLine1": "10 Main Street",
                                  "buyerAddressLine2": "Apt 4B",
                                  "buyerCity": "San Jose",
                                  "buyerState": "CA",
                                  "buyerPostalCode": "95112",
                                  "fulfillmentType": "HOME_DELIVERY",
                                  "tradeIn": false,
                                  "tradeInVin": null,
                                  "tradeInMileage": null,
                                  "tradeInOffer": 0.00,
                                  "deliveryFee": 299.00,
                                  "discountAmount": 100.00
                                }
                                """.formatted(vehicleId, buyerEmail)))
                .andExpect(status().isCreated());

        long dealId = dealRepository.findAll().stream()
                .max(Comparator.comparing(deal -> deal.getId()))
                .orElseThrow()
                .getId();
        long dealerTaskId = dealTaskRepository.findByDealIdOrderByCreatedAtAsc(dealId).stream()
                .filter(task -> "DEALER".equals(task.getAssigneeType().name()))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(patch("/api/tasks/" + dealerTaskId + "/status")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "IN_PROGRESS"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private long createAndApproveDealer() throws Exception {
        long dealerId = createDealer("North Star Auto", "CA-99887");
        mockMvc.perform(patch("/api/dealers/" + dealerId + "/approval")
                        .header("Authorization", bearer(login("admin@stealadeal.local", "Admin123!")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true
                                }
                                """))
                .andExpect(status().isOk());
        return dealerId;
    }

    private long createDealer(String name, String licenseNumberPrefix) throws Exception {
        String uniqueLicenseNumber = licenseNumberPrefix + "-" + System.nanoTime();
        mockMvc.perform(post("/api/dealers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "licenseNumber": "%s",
                                  "city": "San Jose",
                                  "state": "CA"
                                }
                                """.formatted(name, uniqueLicenseNumber)))
                .andExpect(status().isCreated());
        return dealerRepository.findAll().stream()
                .max(Comparator.comparing(dealer -> dealer.getId()))
                .orElseThrow()
                .getId();
    }

    private long createVehicle(long dealerId) throws Exception {
        String uniqueVin = "1HGCM82633A00" + String.format("%04d", (int) (System.nanoTime() % 10000));
        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", bearer(registerDealerUser(dealerId, "vehicle-owner+" + System.nanoTime() + "@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dealerId": %d,
                                  "vin": "%s",
                                  "modelYear": 2023,
                                  "make": "Honda",
                                  "model": "Accord",
                                  "trim": "EX",
                                  "imageUrls": [
                                    "https://example.com/honda-accord.jpg",
                                    "https://example.com/honda-accord-side.jpg"
                                  ],
                                  "mileage": 12450,
                                  "price": 28995.00,
                                  "status": "LIVE"
                                }
                                """.formatted(dealerId, uniqueVin)))
                .andExpect(status().isCreated());
        return vehicleRepository.findAll().stream()
                .max(Comparator.comparing(vehicle -> vehicle.getId()))
                .orElseThrow()
                .getId();
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return extractToken(result.getResponse().getContentAsString());
    }

    private String registerDealerUser(long dealerId, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Dealer Owner",
                                  "email": "%s",
                                  "password": "Dealer123!",
                                  "role": "DEALER",
                                  "dealerId": %d
                                }
                                """.formatted(email, dealerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractToken(result.getResponse().getContentAsString());
    }

    private String registerBuyerUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Buyer User",
                                  "email": "%s",
                                  "password": "Buyer123!",
                                  "role": "BUYER"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractToken(result.getResponse().getContentAsString());
    }

    private String extractToken(String json) {
        int start = json.indexOf("\"token\":\"") + 9;
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
