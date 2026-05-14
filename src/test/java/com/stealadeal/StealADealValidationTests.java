package com.stealadeal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void billingWebhookEndpointIsPublicAndAcksFromConfiguredProvider() throws Exception {
        mockMvc.perform(post("/api/webhooks/billing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt_test\",\"type\":\"invoice.paid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.provider").value("stub"));
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
