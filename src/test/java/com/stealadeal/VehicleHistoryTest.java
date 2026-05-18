package com.stealadeal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class VehicleHistoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    void historyIsAvailableFalseWhenNoReportAnd404ForUnknownVehicle() throws Exception {
        long dealerId = approvedDealer();
        long vehicleId = liveVehicle(dealerId, "1HGAA20001AA00001");

        // No report yet -> 200 available:false (NOT an error), public.
        mockMvc.perform(get("/api/vehicles/" + vehicleId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value(vehicleId))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.source").doesNotExist())
                .andExpect(jsonPath("$.summary").doesNotExist());

        // Unknown vehicle id -> 404.
        mockMvc.perform(get("/api/vehicles/99999999/history"))
                .andExpect(status().isNotFound());
    }

    @Test
    void dealerUploadsHistoryThenItBecomesAvailableAndDownloadable() throws Exception {
        long dealerId = approvedDealer();
        long vehicleId = liveVehicle(dealerId, "1HGAA20002AA00002");
        String dealerTok = bearer(registerDealer(dealerId, "hist-d+" + uniq() + "@example.com"));

        MockMultipartFile pdf = new MockMultipartFile(
                "file", "carfax.pdf", "application/pdf", "%PDF-1.4 fake report".getBytes());

        mockMvc.perform(multipart("/api/dealers/" + dealerId + "/inventory/" + vehicleId + "/history")
                        .file(pdf)
                        .header("Authorization", dealerTok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.source").value("DEALER_UPLOAD"))
                .andExpect(jsonPath("$.reportUrl")
                        .value("/api/vehicles/" + vehicleId + "/history/report"));

        // Public GET now reports available + the report PDF streams.
        mockMvc.perform(get("/api/vehicles/" + vehicleId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.source").value("DEALER_UPLOAD"));

        mockMvc.perform(get("/api/vehicles/" + vehicleId + "/history/report"))
                .andExpect(status().isOk())
                .andExpect(content -> {
                    String ct = content.getResponse().getContentType();
                    org.junit.jupiter.api.Assertions.assertTrue(
                            ct != null && ct.startsWith("application/pdf"), "pdf content type");
                });
    }

    @Test
    void uploadIsRejectedForWrongOwnerAndBadFile() throws Exception {
        long dealerA = approvedDealer();
        long vehicleA = liveVehicle(dealerA, "1HGAA20003AA00003");
        String dealerAtok = bearer(registerDealer(dealerA, "hist-a+" + uniq() + "@example.com"));

        long dealerB = approvedDealer();
        String dealerBtok = bearer(registerDealer(dealerB, "hist-b+" + uniq() + "@example.com"));

        MockMultipartFile pdf = new MockMultipartFile(
                "file", "r.pdf", "application/pdf", "%PDF-1.4".getBytes());

        // Dealer B cannot target dealer A's dealer path -> 403 (PreAuthorize).
        mockMvc.perform(multipart("/api/dealers/" + dealerA + "/inventory/" + vehicleA + "/history")
                        .file(pdf)
                        .header("Authorization", dealerBtok))
                .andExpect(status().isForbidden());

        // Dealer B's own path but a vehicle they do not own -> 403 (service).
        mockMvc.perform(multipart("/api/dealers/" + dealerB + "/inventory/" + vehicleA + "/history")
                        .file(pdf)
                        .header("Authorization", dealerBtok))
                .andExpect(status().isForbidden());

        // Non-PDF -> 400.
        MockMultipartFile notPdf = new MockMultipartFile(
                "file", "r.txt", "text/plain", "not a pdf".getBytes());
        mockMvc.perform(multipart("/api/dealers/" + dealerA + "/inventory/" + vehicleA + "/history")
                        .file(notPdf)
                        .header("Authorization", dealerAtok))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historyEndpointRbacMatrix() throws Exception {
        long dealerId = approvedDealer();
        long vehicleId = liveVehicle(dealerId, "1HGAA20004AA00004");

        // GET history is public for everyone (anon ok).
        mockMvc.perform(get("/api/vehicles/" + vehicleId + "/history"))
                .andExpect(status().isOk());

        MockMultipartFile pdf = new MockMultipartFile(
                "file", "r.pdf", "application/pdf", "%PDF".getBytes());

        // anon upload -> 401
        mockMvc.perform(multipart("/api/dealers/" + dealerId + "/inventory/" + vehicleId + "/history")
                        .file(pdf))
                .andExpect(status().isUnauthorized());

        // BUYER upload -> 403
        String buyerTok = bearer(registerBuyer("hist-rbac-b+" + uniq() + "@example.com"));
        mockMvc.perform(multipart("/api/dealers/" + dealerId + "/inventory/" + vehicleId + "/history")
                        .file(pdf)
                        .header("Authorization", buyerTok))
                .andExpect(status().isForbidden());

        // DEALER (owner) upload -> 200
        String dealerTok = bearer(registerDealer(dealerId, "hist-rbac-d+" + uniq() + "@example.com"));
        mockMvc.perform(multipart("/api/dealers/" + dealerId + "/inventory/" + vehicleId + "/history")
                        .file(pdf)
                        .header("Authorization", dealerTok))
                .andExpect(status().isOk());
    }

    @Test
    void everyDealHasDisclosuresAndGenerateEndpointsWork() throws Exception {
        long dealerId = approvedDealer();
        long vehicleId = liveVehicle(dealerId, "1HGAA20005AA00005");
        String buyerEmail = "disc+" + uniq() + "@example.com";
        String buyerTok = bearer(registerBuyer(buyerEmail));

        long dealId = createDeal(buyerTok, vehicleId, buyerEmail);

        // §8.3: both disclosures exist on every deal as DealDocument types.
        MvcResult docs = mockMvc.perform(get("/api/deals/" + dealId + "/documents")
                        .header("Authorization", buyerTok))
                .andExpect(status().isOk())
                .andReturn();
        String body = docs.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(
                body.contains("ODOMETER_DISCLOSURE"), "odometer disclosure present");
        org.junit.jupiter.api.Assertions.assertTrue(
                body.contains("AS_IS_DISCLOSURE"), "as-is disclosure present");

        mockMvc.perform(post("/api/deals/" + dealId + "/documents/odometer/generate")
                        .header("Authorization", buyerTok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ODOMETER_DISCLOSURE"))
                .andExpect(jsonPath("$.hasContent").value(true));

        mockMvc.perform(post("/api/deals/" + dealId + "/documents/as-is/generate")
                        .header("Authorization", buyerTok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("AS_IS_DISCLOSURE"))
                .andExpect(jsonPath("$.hasContent").value(true));

        // Gating: disclosures are unsigned/unapproved, so the deal is
        // blocked on pending documents.
        mockMvc.perform(get("/api/deals/" + dealId + "/readiness")
                        .header("Authorization", buyerTok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyForHandoff").value(false))
                .andExpect(jsonPath("$.blockers", org.hamcrest.Matchers.hasItem(
                        "Required deal documents are still pending approval")));
    }

    // ---- helpers ----

    private String uniq() {
        return Long.toString(System.nanoTime());
    }

    private long approvedDealer() throws Exception {
        String lic = "HIST-" + uniq();
        mockMvc.perform(post("/api/dealers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hist Motors\",\"licenseNumber\":\"" + lic
                                + "\",\"city\":\"San Jose\",\"state\":\"CA\"}"))
                .andExpect(status().isCreated());
        String adminTok = bearer(login("admin@stealadeal.local", "Admin123!"));
        MvcResult r = mockMvc.perform(get("/api/dealers")).andExpect(status().isOk()).andReturn();
        String b = r.getResponse().getContentAsString();
        int idx = b.lastIndexOf("\"licenseNumber\":\"" + lic + "\"");
        int objStart = b.lastIndexOf('{', idx);
        String obj = b.substring(objStart, b.indexOf('}', idx) + 1);
        long dealerId = Long.parseLong(obj.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/dealers/" + dealerId + "/approval")
                        .header("Authorization", adminTok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk());
        return dealerId;
    }

    private long liveVehicle(long dealerId, String vin) throws Exception {
        String dealerTok = bearer(registerDealer(dealerId, "veh+" + uniq() + "@example.com"));
        mockMvc.perform(post("/api/vehicles").header("Authorization", dealerTok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dealerId":%d,"vin":"%s","modelYear":2023,"make":"Honda",
                                 "model":"Accord","trim":"EX","imageUrls":["https://x/y.jpg"],
                                 "mileage":12450,"price":28995.00,"status":"LIVE"}"""
                                .formatted(dealerId, vin)))
                .andExpect(status().isCreated());
        return vehicleRepository.findAll().stream()
                .max(Comparator.comparing(v -> v.getId())).orElseThrow().getId();
    }

    private long createDeal(String buyerTok, long vehicleId, String buyerEmail) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/deals").header("Authorization", buyerTok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":%d,"buyerName":"Test Buyer",
                                 "buyerEmail":"%s","buyerPhone":"555-0100",
                                 "buyerAddressLine1":"1 Main St","buyerCity":"San Jose",
                                 "buyerState":"CA","buyerPostalCode":"95110",
                                 "fulfillmentType":"PICKUP","tradeIn":false,
                                 "tradeInOffer":0.00,"deliveryFee":0.00,"discountAmount":0.00}"""
                                .formatted(vehicleId, buyerEmail)))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(r.getResponse().getContentAsString()
                .replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private String registerBuyer(String email) throws Exception {
        return token(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"B\",\"email\":\"" + email
                                + "\",\"password\":\"Buyer123!\",\"role\":\"BUYER\"}"))
                .andExpect(status().isCreated()).andReturn());
    }

    private String registerDealer(long dealerId, String email) throws Exception {
        return token(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"D\",\"email\":\"" + email
                                + "\",\"password\":\"Dealer123!\",\"role\":\"DEALER\",\"dealerId\":"
                                + dealerId + "}"))
                .andExpect(status().isCreated()).andReturn());
    }

    private String login(String email, String password) throws Exception {
        return token(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn());
    }

    private String token(MvcResult r) throws Exception {
        String j = r.getResponse().getContentAsString();
        int s = j.indexOf("\"token\":\"") + 9;
        return j.substring(s, j.indexOf('"', s));
    }

    private String bearer(String t) {
        return "Bearer " + t;
    }
}
