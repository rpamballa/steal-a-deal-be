package com.stealadeal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stealadeal.repository.VehicleRepository;
import java.util.Comparator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class BuyerEngagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    void favoritesAddListRemoveAreIdempotentAndEmbedVehicle() throws Exception {
        long dealerId = approvedDealer();
        long vehicleId = liveVehicle(dealerId, "1HGAA10001AA00001", 28995.00);
        String buyer = bearer(registerBuyer("fav+" + uniq() + "@example.com"));

        mockMvc.perform(post("/api/me/favorites").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicleId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleId").value(vehicleId))
                .andExpect(jsonPath("$.vehicle.id").value(vehicleId))
                .andExpect(jsonPath("$.vehicle.make").value("Honda"));

        // re-add => idempotent 200, no duplicate
        mockMvc.perform(post("/api/me/favorites").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicleId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/favorites").header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/me/favorites/" + vehicleId).header("Authorization", buyer))
                .andExpect(status().isNoContent());
        // idempotent delete
        mockMvc.perform(delete("/api/me/favorites/" + vehicleId).header("Authorization", buyer))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/favorites").header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void savedSearchCrudComputesMatchCount() throws Exception {
        long dealerId = approvedDealer();
        liveVehicle(dealerId, "1HGAA10002AA00002", 20000.00); // Honda Accord
        String buyer = bearer(registerBuyer("ss+" + uniq() + "@example.com"));

        MvcResult created = mockMvc.perform(post("/api/me/saved-searches").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cheap Hondas",
                                 "query":{"make":"Honda","maxPrice":25000,"status":"LIVE"},
                                 "alertOnPriceDrop":true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cheap Hondas"))
                .andExpect(jsonPath("$.alertOnPriceDrop").value(true))
                .andExpect(jsonPath("$.lastMatchedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andReturn();
        long id = idFrom(created);

        mockMvc.perform(get("/api/me/saved-searches").header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(patch("/api/me/saved-searches/" + id).header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed","query":{"make":"Toyota"},"alertOnPriceDrop":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.alertOnPriceDrop").value(false));

        mockMvc.perform(delete("/api/me/saved-searches/" + id).header("Authorization", buyer))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/saved-searches").header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void priceDropNotifiesWatchersOncePer24h() throws Exception {
        long dealerId = approvedDealer();
        String dealerTok = bearer(registerDealer(dealerId, "pd-dealer+" + uniq() + "@example.com"));
        String vin = "1HGAA10003AA00003";
        long vehicleId = liveVehicle(dealerId, vin, 30000.00);
        String buyerEmail = "pd+" + uniq() + "@example.com";
        String buyer = bearer(registerBuyer(buyerEmail));

        mockMvc.perform(post("/api/me/favorites").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicleId + "}"))
                .andExpect(status().isCreated());

        // price drop #1
        putPrice(dealerTok, dealerId, vehicleId, vin, 27000.00);
        // price drop #2 within 24h -> debounced (no second notification)
        putPrice(dealerTok, dealerId, vehicleId, vin, 25000.00);

        MvcResult notes = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", buyer)
                        .param("recipientType", "BUYER")
                        .param("recipientReference", buyerEmail)
                        .param("unreadOnly", "false"))
                .andExpect(status().isOk())
                .andReturn();
        String body = notes.getResponse().getContentAsString();
        int occurrences = body.split("Price drop on a car you're watching", -1).length - 1;
        Assertions.assertEquals(1, occurrences,
                "exactly one price-drop notification per (buyer,vehicle) per 24h");
    }

    @Test
    void meEndpointsEnforceBuyerRbac() throws Exception {
        // anon -> 401
        mockMvc.perform(get("/api/me/favorites")).andExpect(status().isUnauthorized());
        long dealerId = approvedDealer();
        String dealerTok = bearer(registerDealer(dealerId, "rbac-d+" + uniq() + "@example.com"));
        String adminTok = bearer(login("admin@stealadeal.local", "Admin123!"));
        String buyerTok = bearer(registerBuyer("rbac-b+" + uniq() + "@example.com"));
        // DEALER -> 403, ADMIN -> 403, BUYER -> 200
        mockMvc.perform(get("/api/me/favorites").header("Authorization", dealerTok))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me/favorites").header("Authorization", adminTok))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me/favorites").header("Authorization", buyerTok))
                .andExpect(status().isOk());
    }

    @Test
    void vehicleClassificationRoundTripsAndDefaultsNull() throws Exception {
        long dealerId = approvedDealer();
        String dealerTok = bearer(registerDealer(dealerId, "cls+" + uniq() + "@example.com"));
        // with classification
        mockMvc.perform(post("/api/vehicles").header("Authorization", dealerTok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dealerId":%d,"vin":"1HGAA10004AA00004","modelYear":2023,
                                 "make":"Tesla","model":"Model 3","trim":"LR",
                                 "imageUrls":["https://x/y.jpg"],"mileage":100,"price":42000.00,
                                 "status":"LIVE","bodyType":"SEDAN","fuelType":"ELECTRIC",
                                 "combinedMpg":131,"marketValueCents":4100000}""".formatted(dealerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bodyType").value("SEDAN"))
                .andExpect(jsonPath("$.fuelType").value("ELECTRIC"))
                .andExpect(jsonPath("$.combinedMpg").value(131))
                .andExpect(jsonPath("$.marketValueCents").value(4100000));
        // without => nulls, existing clients unaffected
        mockMvc.perform(post("/api/vehicles").header("Authorization", dealerTok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dealerId":%d,"vin":"1HGAA10005AA00005","modelYear":2023,
                                 "make":"Honda","model":"Civic","trim":"EX",
                                 "imageUrls":["https://x/y.jpg"],"mileage":100,"price":24000.00,
                                 "status":"LIVE"}""".formatted(dealerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bodyType").doesNotExist())
                .andExpect(jsonPath("$.fuelType").doesNotExist());
    }

    // ---- helpers ----

    private String uniq() {
        return Long.toString(System.nanoTime());
    }

    private long approvedDealer() throws Exception {
        String lic = "RBAC-" + uniq();
        mockMvc.perform(post("/api/dealers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Buyer Eng Motors\",\"licenseNumber\":\"" + lic
                                + "\",\"city\":\"San Jose\",\"state\":\"CA\"}"))
                .andExpect(status().isCreated());
        long dealerId;
        String adminTok = bearer(login("admin@stealadeal.local", "Admin123!"));
        MvcResult r = mockMvc.perform(get("/api/dealers")).andExpect(status().isOk()).andReturn();
        String b = r.getResponse().getContentAsString();
        int idx = b.lastIndexOf("\"licenseNumber\":\"" + lic + "\"");
        // id is the first "id": before this licenseNumber occurrence's object start
        int objStart = b.lastIndexOf('{', idx);
        String obj = b.substring(objStart, b.indexOf('}', idx) + 1);
        dealerId = Long.parseLong(obj.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mockMvc.perform(patch("/api/dealers/" + dealerId + "/approval")
                        .header("Authorization", adminTok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk());
        return dealerId;
    }

    private long liveVehicle(long dealerId, String vin, double price) throws Exception {
        String dealerTok = bearer(registerDealer(dealerId, "veh+" + uniq() + "@example.com"));
        mockMvc.perform(post("/api/vehicles").header("Authorization", dealerTok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dealerId":%d,"vin":"%s","modelYear":2023,"make":"Honda",
                                 "model":"Accord","trim":"EX","imageUrls":["https://x/y.jpg"],
                                 "mileage":12450,"price":%s,"status":"LIVE"}"""
                                .formatted(dealerId, vin, price)))
                .andExpect(status().isCreated());
        return vehicleRepository.findAll().stream()
                .max(Comparator.comparing(v -> v.getId())).orElseThrow().getId();
    }

    private void putPrice(String dealerTok, long dealerId, long vehicleId, String vin, double price)
            throws Exception {
        mockMvc.perform(put("/api/vehicles/" + vehicleId).header("Authorization", dealerTok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dealerId":%d,"vin":"%s","modelYear":2023,"make":"Honda",
                                 "model":"Accord","trim":"EX","imageUrls":["https://x/y.jpg"],
                                 "mileage":12450,"price":%s,"status":"LIVE"}"""
                                .formatted(dealerId, vin, price)))
                .andExpect(status().isOk());
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

    private long idFrom(MvcResult r) throws Exception {
        String j = r.getResponse().getContentAsString();
        return Long.parseLong(j.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private String bearer(String t) {
        return "Bearer " + t;
    }
}
