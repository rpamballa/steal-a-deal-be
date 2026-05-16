package com.stealadeal;

import com.stealadeal.repository.DealerRepository;
import com.stealadeal.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "app.seed.stress.enabled=true",
        "app.seed.stress.vehicle-count=100"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StressSeedTest {

    @Autowired
    private DealerRepository dealerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    void stressSeedProvisionsDealerAndHundredListings() {
        var dealer = dealerRepository.findAll().stream()
                .filter(d -> "STRESS-0001".equals(d.getLicenseNumber()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("stress dealer not provisioned"));
        Assertions.assertTrue(dealer.isApproved(), "stress dealer must be approved");

        long stressVehicles = vehicleRepository.findByDealerIdOrderByIdDesc(dealer.getId()).stream()
                .filter(v -> v.getVin().startsWith("STRESS"))
                .count();
        Assertions.assertEquals(100, stressVehicles, "expected 100 synthetic listings");

        var sample = vehicleRepository.findByDealerIdOrderByIdDesc(dealer.getId()).stream()
                .filter(v -> v.getVin().startsWith("STRESS"))
                .findFirst()
                .orElseThrow();
        Assertions.assertTrue(sample.getPrice().signum() > 0, "price must be positive");
        Assertions.assertTrue(sample.getModelYear() >= 2015 && sample.getModelYear() <= 2024,
                "model year in expected range");
        Assertions.assertFalse(sample.getImageUrls().isEmpty(), "listing must have images");
    }

    @Test
    void stressSeedIsIdempotentOnReevaluation() {
        var dealer = dealerRepository.findAll().stream()
                .filter(d -> "STRESS-0001".equals(d.getLicenseNumber()))
                .findFirst()
                .orElseThrow();
        long before = vehicleRepository.findByDealerIdOrderByIdDesc(dealer.getId()).stream()
                .filter(v -> v.getVin().startsWith("STRESS"))
                .count();
        Assertions.assertEquals(100, before);
    }
}
