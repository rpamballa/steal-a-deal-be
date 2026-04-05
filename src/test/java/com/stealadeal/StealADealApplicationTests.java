package com.stealadeal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StealADealApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dealerVehicleLeadAndAppointmentFlowWorks() throws Exception {
        mockMvc.perform(post("/api/dealers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "North Star Auto",
                                  "licenseNumber": "CA-99887",
                                  "city": "San Jose",
                                  "state": "CA"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approved").value(false));

        mockMvc.perform(patch("/api/dealers/1/approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true));

        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dealerId": 1,
                                  "vin": "1HGCM82633A004352",
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
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dealerName").value("North Star Auto"))
                .andExpect(jsonPath("$.primaryImageUrl").value("https://example.com/honda-accord.jpg"))
                .andExpect(jsonPath("$.imageUrls[1]").value("https://example.com/honda-accord-side.jpg"))
                .andExpect(jsonPath("$.status").value("LIVE"));

        mockMvc.perform(get("/api/vehicles")
                        .param("make", "hon")
                        .param("maxPrice", "30000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].model").value("Accord"));

        mockMvc.perform(post("/api/vehicles/1/leads")
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

        mockMvc.perform(post("/api/vehicles/1/appointments")
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

        mockMvc.perform(get("/api/leads").param("vehicleId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].buyerEmail").value("taylor@example.com"));

        mockMvc.perform(get("/api/appointments").param("status", "REQUESTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("TEST_DRIVE"));

        mockMvc.perform(patch("/api/leads/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "QUALIFIED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUALIFIED"));

        mockMvc.perform(patch("/api/appointments/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CONFIRMED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(put("/api/vehicles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dealerId": 1,
                                  "vin": "1HGCM82633A004352",
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
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trim").value("Touring"))
                .andExpect(jsonPath("$.primaryImageUrl").value("https://example.com/honda-accord-touring.jpg"))
                .andExpect(jsonPath("$.imageUrls[1]").value("https://example.com/honda-accord-touring-rear.jpg"))
                .andExpect(jsonPath("$.status").value("RESERVED"));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealerCount").value(1))
                .andExpect(jsonPath("$.vehicleCount").value(1))
                .andExpect(jsonPath("$.newLeadCount").value(0))
                .andExpect(jsonPath("$.requestedAppointmentCount").value(0));

        mockMvc.perform(post("/api/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": 1,
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
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("INITIATED"))
                .andExpect(jsonPath("$.tradeIn").value(true))
                .andExpect(jsonPath("$.depositPaid").value(false));

        mockMvc.perform(patch("/api/deals/1/stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "OFFER_SENT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("OFFER_SENT"));

        mockMvc.perform(patch("/api/deals/1/stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "BUYER_CONFIRMED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("BUYER_CONFIRMED"));

        mockMvc.perform(post("/api/deals/1/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 500.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DEPOSIT_PAID"))
                .andExpect(jsonPath("$.depositPaid").value(true));

        mockMvc.perform(post("/api/deals/1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "INSURANCE_PROOF",
                                  "fileName": "insurance-card.pdf"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UPLOADED"));

        mockMvc.perform(get("/api/deals/1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dealId").value(1));

        mockMvc.perform(patch("/api/deals/1/documents/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(patch("/api/deals/1/documents/2/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(patch("/api/deals/1/documents/3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(patch("/api/deals/1/stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "DOCUMENTS_PENDING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DOCUMENTS_PENDING"));

        mockMvc.perform(patch("/api/deals/1/stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "READY_FOR_HANDOFF"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("READY_FOR_HANDOFF"));

        mockMvc.perform(patch("/api/deals/1/stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "INITIATED"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/deals/1/fulfillment")
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

        mockMvc.perform(get("/api/deals/1/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("DEAL_CREATED"));

        mockMvc.perform(get("/api/deals/1/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyForHandoff").value(false))
                .andExpect(jsonPath("$.blockers[0]").exists());
    }

    @Test
    void corsPreflightAllowsFrontendOrigins() throws Exception {
        mockMvc.perform(options("/api/vehicles")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }
}
