package com.stealadeal.config;

import com.stealadeal.domain.Dealer;
import com.stealadeal.domain.Vehicle;
import com.stealadeal.domain.VehicleStatus;
import com.stealadeal.repository.DealerRepository;
import com.stealadeal.repository.VehicleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Generates a large batch of realistic synthetic vehicle listings for
 * stress/load testing. Fully opt-in: the seeding bean only exists when
 * {@code app.seed.stress.enabled=true}. Independent of the base
 * SeedDataConfig — it provisions its own approved dealer so it works
 * in any environment (H2 dev or Postgres prod) and is idempotent
 * (re-running tops up to the target count, never duplicates).
 */
@Configuration
@EnableConfigurationProperties(StressSeedProperties.class)
public class StressSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(StressSeedConfig.class);
    private static final String STRESS_LICENSE = "STRESS-0001";
    private static final String VIN_PREFIX = "STRESS";

    private record Model(String make, String model, String[] trims, int tierBasePrice) {
    }

    // Common US used-car mix; tierBasePrice is a 0-mileage, current-year anchor.
    private static final Model[] CATALOG = {
            new Model("Toyota", "Camry", new String[] {"LE", "SE", "XLE", "XSE"}, 31000),
            new Model("Toyota", "Corolla", new String[] {"L", "LE", "SE", "XSE"}, 24000),
            new Model("Toyota", "RAV4", new String[] {"LE", "XLE", "XLE Premium", "Adventure"}, 34000),
            new Model("Toyota", "Highlander", new String[] {"LE", "XLE", "Limited"}, 42000),
            new Model("Toyota", "Tacoma", new String[] {"SR", "SR5", "TRD Sport"}, 38000),
            new Model("Honda", "Civic", new String[] {"LX", "Sport", "EX", "Touring"}, 26000),
            new Model("Honda", "Accord", new String[] {"LX", "Sport", "EX-L", "Touring"}, 32000),
            new Model("Honda", "CR-V", new String[] {"LX", "EX", "EX-L", "Touring"}, 33000),
            new Model("Honda", "Pilot", new String[] {"Sport", "EX-L", "Touring"}, 43000),
            new Model("Ford", "F-150", new String[] {"XL", "XLT", "Lariat", "Platinum"}, 45000),
            new Model("Ford", "Escape", new String[] {"S", "SE", "SEL", "Titanium"}, 30000),
            new Model("Ford", "Explorer", new String[] {"Base", "XLT", "Limited"}, 40000),
            new Model("Ford", "Mustang", new String[] {"EcoBoost", "GT"}, 39000),
            new Model("Chevrolet", "Silverado", new String[] {"WT", "LT", "RST", "LTZ"}, 44000),
            new Model("Chevrolet", "Equinox", new String[] {"LS", "LT", "Premier"}, 29000),
            new Model("Chevrolet", "Malibu", new String[] {"LS", "LT", "Premier"}, 27000),
            new Model("Chevrolet", "Tahoe", new String[] {"LS", "LT", "Premier"}, 58000),
            new Model("Tesla", "Model 3", new String[] {"Standard", "Long Range", "Performance"}, 41000),
            new Model("Tesla", "Model Y", new String[] {"Long Range", "Performance"}, 49000),
            new Model("BMW", "3 Series", new String[] {"330i", "M340i"}, 46000),
            new Model("BMW", "X3", new String[] {"sDrive30i", "xDrive30i", "M40i"}, 48000),
            new Model("BMW", "X5", new String[] {"xDrive40i"}, 65000),
            new Model("Nissan", "Altima", new String[] {"S", "SV", "SL"}, 27000),
            new Model("Nissan", "Rogue", new String[] {"S", "SV", "SL"}, 30000),
            new Model("Jeep", "Grand Cherokee", new String[] {"Laredo", "Limited", "Overland"}, 42000),
            new Model("Jeep", "Wrangler", new String[] {"Sport", "Sahara", "Rubicon"}, 41000),
    };

    private static final List<String> IMAGE_POOL = List.of(
            "https://images.pexels.com/photos/170811/pexels-photo-170811.jpeg",
            "https://images.pexels.com/photos/3802510/pexels-photo-3802510.jpeg",
            "https://images.pexels.com/photos/3729464/pexels-photo-3729464.jpeg",
            "https://images.pexels.com/photos/1592384/pexels-photo-1592384.jpeg",
            "https://images.pexels.com/photos/193991/pexels-photo-193991.jpeg",
            "https://images.pexels.com/photos/1007410/pexels-photo-1007410.jpeg"
    );

    private static final int CURRENT_YEAR = 2025;

    @Bean
    @Order(100)
    @ConditionalOnProperty(name = "app.seed.stress.enabled", havingValue = "true")
    CommandLineRunner stressSeedData(
            DealerRepository dealerRepository,
            VehicleRepository vehicleRepository,
            StressSeedProperties properties
    ) {
        return args -> {
            int target = properties.vehicleCount();
            Dealer dealer = dealerRepository.findAll().stream()
                    .filter(d -> STRESS_LICENSE.equals(d.getLicenseNumber()))
                    .findFirst()
                    .orElseGet(() -> {
                        Dealer d = new Dealer();
                        d.setName("Stress Test Motors");
                        d.setLicenseNumber(STRESS_LICENSE);
                        d.setCity("Phoenix");
                        d.setState("AZ");
                        d.setApproved(true);
                        return dealerRepository.save(d);
                    });

            long existing = vehicleRepository.findByDealerIdOrderByIdDesc(dealer.getId()).stream()
                    .filter(v -> v.getVin().startsWith(VIN_PREFIX))
                    .count();
            if (existing >= target) {
                log.info("[stress-seed] {} stress listings already present (target {}); skipping",
                        existing, target);
                return;
            }

            int created = 0;
            for (int i = (int) existing; i < target; i++) {
                String vin = String.format("%s%011d", VIN_PREFIX, i);
                if (vehicleRepository.findByVinIgnoreCase(vin).isPresent()) {
                    continue;
                }
                Model m = CATALOG[i % CATALOG.length];
                int year = 2015 + (i % (CURRENT_YEAR - 2015));
                int age = CURRENT_YEAR - year;
                int mileage = age * 12000 + (i * 137 % 9000);
                String trim = m.trims()[i % m.trims().length];

                BigDecimal price = priceFor(m.tierBasePrice(), age, mileage);
                VehicleStatus status = statusFor(i);
                List<String> images = List.of(
                        IMAGE_POOL.get(i % IMAGE_POOL.size()),
                        IMAGE_POOL.get((i + 2) % IMAGE_POOL.size())
                );

                Vehicle v = new Vehicle();
                v.setDealer(dealer);
                v.setVin(vin);
                v.setModelYear(year);
                v.setMake(m.make());
                v.setModel(m.model());
                v.setTrim(trim);
                v.setImageUrls(images);
                v.setMileage(mileage);
                v.setPrice(price);
                v.setStatus(status);
                vehicleRepository.save(v);
                created++;
            }
            log.info("[stress-seed] created {} synthetic listings for dealer {} (target {})",
                    created, dealer.getId(), target);
        };
    }

    private BigDecimal priceFor(int tierBasePrice, int age, int mileage) {
        // ~9%/yr depreciation + ~$0.06/mile, floored at $7,500.
        double depreciated = tierBasePrice * Math.pow(0.91, age) - mileage * 0.06;
        double floored = Math.max(depreciated, 7500.0);
        return BigDecimal.valueOf(floored).setScale(2, RoundingMode.HALF_UP);
    }

    private VehicleStatus statusFor(int i) {
        if (i % 25 == 0) {
            return VehicleStatus.SOLD;
        }
        if (i % 13 == 0) {
            return VehicleStatus.DRAFT;
        }
        if (i % 10 == 0) {
            return VehicleStatus.RESERVED;
        }
        return VehicleStatus.LIVE;
    }
}
