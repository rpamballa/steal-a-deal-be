package com.stealadeal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stealadeal.repository.AppointmentRepository;
import com.stealadeal.repository.DealDocumentRepository;
import com.stealadeal.repository.DealRepository;
import com.stealadeal.repository.DealerRepository;
import com.stealadeal.repository.LeadRepository;
import com.stealadeal.repository.NotificationRepository;
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
class StealADealApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealerRepository dealerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private DealDocumentRepository dealDocumentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void dealerVehicleLeadAndAppointmentFlowWorks() throws Exception {
        String uniqueLicenseNumber = "CA-99887-" + System.nanoTime();
        String uniqueVehicleVin = "1HGCM82633A00" + String.format("%04d", (int) (System.nanoTime() % 10000));
        String adminToken = login("admin@stealadeal.local", "Admin123!");

        mockMvc.perform(post("/api/dealers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "North Star Auto",
                                  "licenseNumber": "%s",
                                  "city": "San Jose",
                                  "state": "CA"
                                }
                                """.formatted(uniqueLicenseNumber)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approved").value(false));

        long dealerId = dealerRepository.findAll().stream()
                .max(Comparator.comparing(dealer -> dealer.getId()))
                .orElseThrow()
                .getId();

        mockMvc.perform(patch("/api/dealers/" + dealerId + "/approval")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true));

        String dealerToken = registerDealerUser(dealerId, "dealer+" + dealerId + "@example.com");
        String buyerToken = registerBuyerUser("taylor@example.com");

        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", bearer(dealerToken))
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
                                """.formatted(dealerId, uniqueVehicleVin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dealerName").value("North Star Auto"))
                .andExpect(jsonPath("$.primaryImageUrl").value("https://example.com/honda-accord.jpg"))
                .andExpect(jsonPath("$.imageUrls[1]").value("https://example.com/honda-accord-side.jpg"))
                .andExpect(jsonPath("$.status").value("LIVE"));

        long vehicleId = vehicleRepository.findAll().stream()
                .max(Comparator.comparing(vehicle -> vehicle.getId()))
                .orElseThrow()
                .getId();

        mockMvc.perform(get("/api/vehicles")
                        .param("make", "hon")
                        .param("maxPrice", "30000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].model").value("Accord"));

        mockMvc.perform(post("/api/vehicles/" + vehicleId + "/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "buyerName": "Taylor Reed",
                                  "buyerEmail": "taylor@example.com",
                                  "buyerPhone": "4085550199",
                                  "message": "Interested in availability this weekend."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"));

        mockMvc.perform(post("/api/vehicles/" + vehicleId + "/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "buyerName": "Taylor Reed",
                                  "buyerEmail": "taylor@example.com",
                                  "type": "TEST_DRIVE",
                                  "scheduledAt": "2026-03-21T10:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        long leadId = leadRepository.findAll().stream()
                .max(Comparator.comparing(lead -> lead.getId()))
                .orElseThrow()
                .getId();

        long appointmentId = appointmentRepository.findAll().stream()
                .max(Comparator.comparing(appointment -> appointment.getId()))
                .orElseThrow()
                .getId();

        mockMvc.perform(get("/api/leads")
                        .header("Authorization", bearer(dealerToken))
                        .param("vehicleId", String.valueOf(vehicleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].buyerEmail").value("taylor@example.com"));

        mockMvc.perform(get("/api/appointments")
                        .header("Authorization", bearer(dealerToken))
                        .param("status", "REQUESTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("TEST_DRIVE"));

        mockMvc.perform(patch("/api/leads/" + leadId + "/status")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "QUALIFIED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUALIFIED"));

        mockMvc.perform(patch("/api/appointments/" + appointmentId + "/status")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CONFIRMED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(put("/api/vehicles/" + vehicleId)
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dealerId": %d,
                                  "vin": "%s",
                                  "modelYear": 2023,
                                  "make": "Honda",
                                  "model": "Accord",
                                  "trim": "Touring",
                                  "imageUrls": [
                                    "https://example.com/honda-accord-touring.jpg",
                                    "https://example.com/honda-accord-touring-rear.jpg"
                                  ],
                                  "mileage": 12000,
                                  "price": 29495.00,
                                  "status": "RESERVED"
                                }
                                """.formatted(dealerId, uniqueVehicleVin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trim").value("Touring"))
                .andExpect(jsonPath("$.primaryImageUrl").value("https://example.com/honda-accord-touring.jpg"))
                .andExpect(jsonPath("$.imageUrls[1]").value("https://example.com/honda-accord-touring-rear.jpg"))
                .andExpect(jsonPath("$.status").value("RESERVED"));

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealerCount").value(1))
                .andExpect(jsonPath("$.vehicleCount").value(1))
                .andExpect(jsonPath("$.newLeadCount").value(0))
                .andExpect(jsonPath("$.requestedAppointmentCount").value(0));

        mockMvc.perform(post("/api/deals")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "buyerName": "Taylor Reed",
                                  "buyerEmail": "taylor@example.com",
                                  "buyerPhone": "4085550199",
                                  "buyerAddressLine1": "10 Main Street",
                                  "buyerAddressLine2": "Apt 4B",
                                  "buyerCity": "San Jose",
                                  "buyerState": "CA",
                                  "buyerPostalCode": "95112",
                                  "fulfillmentType": "HOME_DELIVERY",
                                  "tradeIn": true,
                                  "tradeInVin": "1HGFA16526L081415",
                                  "tradeInMileage": 64000,
                                  "tradeInOffer": 3200.00,
                                  "deliveryFee": 299.00,
                                  "discountAmount": 100.00
                                }
                                """.formatted(vehicleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("INITIATED"))
                .andExpect(jsonPath("$.tradeIn").value(true))
                .andExpect(jsonPath("$.depositPaid").value(false));

        long dealId = dealRepository.findAll().stream()
                .max(Comparator.comparing(deal -> deal.getId()))
                .orElseThrow()
                .getId();

        mockMvc.perform(patch("/api/deals/" + dealId + "/stage")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "OFFER_SENT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("OFFER_SENT"));

        mockMvc.perform(patch("/api/deals/" + dealId + "/stage")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "BUYER_CONFIRMED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("BUYER_CONFIRMED"));

        mockMvc.perform(post("/api/deals/" + dealId + "/deposit")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 500.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DEPOSIT_PAID"))
                .andExpect(jsonPath("$.depositPaid").value(true));

        mockMvc.perform(post("/api/deals/" + dealId + "/documents")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "INSURANCE_PROOF",
                                  "fileName": "insurance-card.pdf"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UPLOADED"));

        mockMvc.perform(get("/api/deals/" + dealId + "/documents")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dealId").value(dealId));

        var documents = dealDocumentRepository.findByDealId(dealId);
        long buyerAgreementId = documents.stream().filter(document -> document.getType().name().equals("BUYER_AGREEMENT")).findFirst().orElseThrow().getId();
        long driverLicenseId = documents.stream().filter(document -> document.getType().name().equals("DRIVER_LICENSE")).findFirst().orElseThrow().getId();
        long insuranceProofId = documents.stream().filter(document -> document.getType().name().equals("INSURANCE_PROOF")).findFirst().orElseThrow().getId();
        long odometerDisclosureId = documents.stream().filter(document -> document.getType().name().equals("ODOMETER_DISCLOSURE")).findFirst().orElseThrow().getId();
        long asIsDisclosureId = documents.stream().filter(document -> document.getType().name().equals("AS_IS_DISCLOSURE")).findFirst().orElseThrow().getId();

        mockMvc.perform(patch("/api/deals/" + dealId + "/documents/" + buyerAgreementId + "/status")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(patch("/api/deals/" + dealId + "/documents/" + driverLicenseId + "/status")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(patch("/api/deals/" + dealId + "/documents/" + insuranceProofId + "/status")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(patch("/api/deals/" + dealId + "/documents/" + odometerDisclosureId + "/status")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(patch("/api/deals/" + dealId + "/documents/" + asIsDisclosureId + "/status")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(patch("/api/deals/" + dealId + "/stage")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "DOCUMENTS_PENDING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DOCUMENTS_PENDING"));

        mockMvc.perform(patch("/api/deals/" + dealId + "/stage")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "READY_FOR_HANDOFF"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("READY_FOR_HANDOFF"));

        mockMvc.perform(patch("/api/deals/" + dealId + "/stage")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "INITIATED"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/deals/" + dealId + "/fulfillment")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SCHEDULED",
                                  "scheduledAt": "2026-03-25T18:30:00Z",
                                  "location": "North Star Auto, San Jose",
                                  "notes": "Customer requested curbside pickup"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfillmentStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$.fulfillmentLocation").value("North Star Auto, San Jose"));

        mockMvc.perform(get("/api/deals/" + dealId + "/activity")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("DEAL_CREATED"));

        mockMvc.perform(get("/api/deals/" + dealId + "/readiness")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyForHandoff").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty());

        mockMvc.perform(get("/api/deals/" + dealId + "/tasks")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("buyer-pay-deposit"));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(buyerToken))
                        .param("recipientType", "BUYER")
                        .param("recipientReference", "taylor@example.com")
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipientType").value("BUYER"));

        long notificationId = notificationRepository.findAll().stream()
                .max(Comparator.comparing(notification -> notification.getId()))
                .orElseThrow()
                .getId();

        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "read": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(get("/api/inbox/buyers/taylor@example.com")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantType").value("BUYER"))
                .andExpect(jsonPath("$.summary.totalDeals").value(1))
                .andExpect(jsonPath("$.summary.readyForHandoffCount").value(1))
                .andExpect(jsonPath("$.deals[0].nextAction").value("Prepare for vehicle handoff"));

        mockMvc.perform(get("/api/inbox/dealers/" + dealerId)
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inbox.participantType").value("DEALER"))
                .andExpect(jsonPath("$.queueSummary.readyForHandoffCount").value(1))
                .andExpect(jsonPath("$.inbox.deals[0].nextAction").value("Complete vehicle handoff"));

        mockMvc.perform(get("/api/dealers/" + dealerId + "/deal-queue")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.readyForHandoffCount").value(1))
                .andExpect(jsonPath("$.readyForHandoff[0].dealId").value(dealId));

        mockMvc.perform(post("/api/dealers/" + dealerId + "/inventory-upload")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "CREATE_ONLY",
                                  "vehicles": [
                                    {
                                      "vin": "%s",
                                      "modelYear": 2023,
                                      "make": "Honda",
                                      "model": "Accord",
                                      "trim": "Duplicate",
                                      "imageUrls": ["https://example.com/duplicate.jpg"],
                                      "mileage": 11000,
                                      "price": 28000.00,
                                      "status": "LIVE"
                                    },
                                    {
                                      "vin": "1FTFW1E50NFA12345",
                                      "modelYear": 2022,
                                      "make": "Ford",
                                      "model": "F-150",
                                      "trim": "Lariat",
                                      "imageUrls": [
                                        "https://example.com/f150-front.jpg",
                                        "https://example.com/f150-rear.jpg"
                                      ],
                                      "mileage": 18300,
                                      "price": 43995.00,
                                      "status": "LIVE"
                                    }
                                  ]
                                }
                                """.formatted(uniqueVehicleVin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(1))
                .andExpect(jsonPath("$.rows[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.rows[1].status").value("CREATED"));

        mockMvc.perform(post("/api/dealers/" + dealerId + "/inventory-upload")
                        .header("Authorization", bearer(dealerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "UPSERT",
                                  "vehicles": [
                                    {
                                      "vin": "1FTFW1E50NFA12345",
                                      "modelYear": 2022,
                                      "make": "Ford",
                                      "model": "F-150",
                                      "trim": "Platinum",
                                      "imageUrls": [
                                        "https://example.com/f150-platinum-front.jpg",
                                        "https://example.com/f150-platinum-rear.jpg"
                                      ],
                                      "mileage": 17500,
                                      "price": 44995.00,
                                      "status": "RESERVED"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1))
                .andExpect(jsonPath("$.rows[0].status").value("UPDATED"));

        mockMvc.perform(get("/api/dealers/" + dealerId + "/inventory")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].vin").value("1FTFW1E50NFA12345"))
                .andExpect(jsonPath("$[0].trim").value("Platinum"))
                .andExpect(jsonPath("$[0].status").value("RESERVED"));

        MockMultipartFile csvFile = new MockMultipartFile(
                "file",
                "inventory.csv",
                "text/csv",
                """
                        vin,modelYear,make,model,trim,imageUrls,mileage,price,status
                        1C4RJFBG8NCB12345,2022,Jeep,Grand Cherokee,Limited,https://example.com/jeep-front.jpg|https://example.com/jeep-side.jpg,21450,38995.00,LIVE
                        1FTFW1E50NFA12345,2022,Ford,F-150,Should Reject,https://example.com/duplicate.jpg,18000,43000.00,LIVE
                        2C4RC1BG3NRB54321,2023,Chrysler,Pacifica,Touring L,https://example.com/pacifica-front.jpg|https://example.com/pacifica-rear.jpg,9800,36100.00,RESERVED
                        """.getBytes()
        );

        mockMvc.perform(multipart("/api/dealers/" + dealerId + "/inventory-upload")
                        .file(csvFile)
                        .param("mode", "CREATE_ONLY")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(2))
                .andExpect(jsonPath("$.rejectedCount").value(1))
                .andExpect(jsonPath("$.rows[0].status").value("CREATED"))
                .andExpect(jsonPath("$.rows[1].status").value("REJECTED"))
                .andExpect(jsonPath("$.rows[2].status").value("CREATED"));

        mockMvc.perform(get("/api/dealers/" + dealerId + "/portal")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.dealerName").value("North Star Auto"))
                .andExpect(jsonPath("$.overview.totalInventoryCount").value(4))
                .andExpect(jsonPath("$.overview.leadCount").value(1))
                .andExpect(jsonPath("$.overview.activeDealCount").value(1))
                .andExpect(jsonPath("$.queueSummary.readyForHandoffCount").value(1))
                .andExpect(jsonPath("$.recentDeals[0].nextAction").value("Complete vehicle handoff"))
                .andExpect(jsonPath("$.recentActivity[0].eventType").isNotEmpty());

        mockMvc.perform(get("/api/dealers/" + dealerId + "/portal/deals")
                        .header("Authorization", bearer(dealerToken))
                        .param("stage", "READY_FOR_HANDOFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dealId").value(dealId));

        mockMvc.perform(get("/api/dealers/" + dealerId + "/portal/leads")
                        .header("Authorization", bearer(dealerToken))
                        .param("status", "QUALIFIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].buyerEmail").value("taylor@example.com"));

        mockMvc.perform(get("/api/dealers/" + dealerId + "/portal/appointments")
                        .header("Authorization", bearer(dealerToken))
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].buyerEmail").value("taylor@example.com"));

        mockMvc.perform(get("/api/dealers/" + dealerId + "/portal/documents")
                        .header("Authorization", bearer(dealerToken))
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dealId").value(dealId));

        mockMvc.perform(get("/api/dealers/" + dealerId + "/portal/subscription")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("GROWTH"))
                .andExpect(jsonPath("$.status").value("TRIALING"));

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
                .andExpect(jsonPath("$[0].status").value("OPEN"));

        mockMvc.perform(get("/api/inbox/dealers/99")
                        .header("Authorization", bearer(dealerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsPreflightAllowsFrontendOrigins() throws Exception {
        mockMvc.perform(options("/api/vehicles")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
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
                                  "displayName": "Taylor Reed",
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
