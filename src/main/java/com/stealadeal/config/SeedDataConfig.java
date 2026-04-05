package com.stealadeal.config;

import com.stealadeal.domain.Appointment;
import com.stealadeal.domain.AppointmentStatus;
import com.stealadeal.domain.AppointmentType;
import com.stealadeal.domain.Dealer;
import com.stealadeal.domain.Deal;
import com.stealadeal.domain.DealActivity;
import com.stealadeal.domain.DealDocument;
import com.stealadeal.domain.DealStage;
import com.stealadeal.domain.Lead;
import com.stealadeal.domain.LeadStatus;
import com.stealadeal.domain.DocumentStatus;
import com.stealadeal.domain.DocumentType;
import com.stealadeal.domain.FulfillmentStatus;
import com.stealadeal.domain.FulfillmentType;
import com.stealadeal.domain.Vehicle;
import com.stealadeal.domain.VehicleStatus;
import com.stealadeal.repository.AppointmentRepository;
import com.stealadeal.repository.DealActivityRepository;
import com.stealadeal.repository.DealerRepository;
import com.stealadeal.repository.DealDocumentRepository;
import com.stealadeal.repository.DealRepository;
import com.stealadeal.repository.LeadRepository;
import com.stealadeal.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Profile("!test")
public class SeedDataConfig {

    @Bean
    CommandLineRunner seedData(
            DealerRepository dealerRepository,
            VehicleRepository vehicleRepository,
            LeadRepository leadRepository,
            AppointmentRepository appointmentRepository,
            DealRepository dealRepository,
            DealDocumentRepository dealDocumentRepository,
            DealActivityRepository dealActivityRepository
    ) {
        return args -> {
            if (dealerRepository.count() > 0) {
                return;
            }

            Dealer westCoast = new Dealer();
            westCoast.setName("West Coast Auto Gallery");
            westCoast.setLicenseNumber("CA-11223");
            westCoast.setCity("San Jose");
            westCoast.setState("CA");
            westCoast.setApproved(true);
            westCoast = dealerRepository.save(westCoast);

            Dealer urban = new Dealer();
            urban.setName("Urban Drive Motors");
            urban.setLicenseNumber("TX-77889");
            urban.setCity("Austin");
            urban.setState("TX");
            urban.setApproved(true);
            urban = dealerRepository.save(urban);

            Vehicle tesla = vehicleRepository.save(vehicle(
                    westCoast, "5YJ3E1EA7LF000111", 2022, "Tesla", "Model 3", "Long Range",
                    List.of(
                            "https://images.pexels.com/photos/170811/pexels-photo-170811.jpeg",
                            "https://images.pexels.com/photos/3802510/pexels-photo-3802510.jpeg"
                    ), 18420,
                    new BigDecimal("31995.00"), VehicleStatus.LIVE
            ));
            Vehicle bmw = vehicleRepository.save(vehicle(
                    westCoast, "WBA73AK09N7J00123", 2021, "BMW", "X3", "xDrive30i",
                    List.of(
                            "https://images.pexels.com/photos/3729464/pexels-photo-3729464.jpeg",
                            "https://images.pexels.com/photos/1545743/pexels-photo-1545743.jpeg"
                    ), 27610,
                    new BigDecimal("34500.00"), VehicleStatus.LIVE
            ));
            Vehicle honda = vehicleRepository.save(vehicle(
                    urban, "1HGCV1F30MA000456", 2023, "Honda", "Accord", "Sport Hybrid",
                    List.of(
                            "https://images.pexels.com/photos/1007410/pexels-photo-1007410.jpeg",
                            "https://images.pexels.com/photos/210019/pexels-photo-210019.jpeg"
                    ), 8650,
                    new BigDecimal("29950.00"), VehicleStatus.RESERVED
            ));
            vehicleRepository.save(vehicle(
                    urban, "2T3R1RFV8PC010789", 2024, "Toyota", "RAV4", "XLE Premium",
                    List.of(
                            "https://images.pexels.com/photos/1592384/pexels-photo-1592384.jpeg",
                            "https://images.pexels.com/photos/116675/pexels-photo-116675.jpeg"
                    ), 4120,
                    new BigDecimal("33890.00"), VehicleStatus.LIVE
            ));
            vehicleRepository.save(vehicle(
                    westCoast, "1FMDE5BH9NLA12345", 2022, "Ford", "Bronco", "Outer Banks",
                    List.of(
                            "https://images.pexels.com/photos/193991/pexels-photo-193991.jpeg",
                            "https://images.pexels.com/photos/1955134/pexels-photo-1955134.jpeg"
                    ), 15210,
                    new BigDecimal("42995.00"), VehicleStatus.LIVE
            ));

            Lead lead = new Lead();
            lead.setVehicle(tesla);
            lead.setBuyerName("Jordan Blake");
            lead.setBuyerEmail("jordan@example.com");
            lead.setBuyerPhone("4085550101");
            lead.setMessage("Is home delivery available in the Bay Area?");
            lead.setStatus(LeadStatus.NEW);
            lead.setCreatedAt(OffsetDateTime.now().minusDays(1));
            leadRepository.save(lead);

            Appointment appointment = new Appointment();
            appointment.setVehicle(bmw);
            appointment.setBuyerName("Morgan Lee");
            appointment.setBuyerEmail("morgan@example.com");
            appointment.setType(AppointmentType.TEST_DRIVE);
            appointment.setStatus(AppointmentStatus.CONFIRMED);
            appointment.setScheduledAt(OffsetDateTime.now().plusDays(2));
            appointment.setCreatedAt(OffsetDateTime.now().minusHours(6));
            appointmentRepository.save(appointment);

            Appointment delivery = new Appointment();
            delivery.setVehicle(honda);
            delivery.setBuyerName("Avery Smith");
            delivery.setBuyerEmail("avery@example.com");
            delivery.setType(AppointmentType.HOME_DELIVERY);
            delivery.setStatus(AppointmentStatus.REQUESTED);
            delivery.setScheduledAt(OffsetDateTime.now().plusDays(4));
            delivery.setCreatedAt(OffsetDateTime.now().minusHours(2));
            appointmentRepository.save(delivery);

            Deal deal = new Deal();
            deal.setVehicle(tesla);
            deal.setBuyerName("Jordan Blake");
            deal.setBuyerEmail("jordan@example.com");
            deal.setBuyerPhone("4085550101");
            deal.setBuyerAddressLine1("1450 Market Street");
            deal.setBuyerAddressLine2("Unit 9");
            deal.setBuyerCity("San Francisco");
            deal.setBuyerState("CA");
            deal.setBuyerPostalCode("94103");
            deal.setFulfillmentType(FulfillmentType.HOME_DELIVERY);
            deal.setFulfillmentStatus(FulfillmentStatus.SCHEDULED);
            deal.setFulfillmentScheduledAt(OffsetDateTime.now().plusDays(3));
            deal.setFulfillmentLocation("1450 Market Street, San Francisco, CA");
            deal.setFulfillmentNotes("Buyer requested evening handoff after 6 PM");
            deal.setTradeIn(true);
            deal.setTradeInVin("1C4RJFBG7FC625797");
            deal.setTradeInMileage(78200);
            deal.setTradeInOffer(new BigDecimal("4200.00"));
            deal.setVehiclePrice(tesla.getPrice());
            deal.setTaxAmount(new BigDecimal("2639.59"));
            deal.setRegistrationFee(new BigDecimal("425.00"));
            deal.setDocumentationFee(new BigDecimal("199.00"));
            deal.setDeliveryFee(new BigDecimal("299.00"));
            deal.setDiscountAmount(new BigDecimal("250.00"));
            deal.setDepositAmount(new BigDecimal("500.00"));
            deal.setDepositPaid(true);
            deal.setTotalAmount(new BigDecimal("31107.59"));
            deal.setStage(DealStage.DOCUMENTS_PENDING);
            deal.setCreatedAt(OffsetDateTime.now().minusHours(10));
            deal.setUpdatedAt(OffsetDateTime.now().minusHours(1));
            deal = dealRepository.save(deal);

            tesla.setStatus(VehicleStatus.RESERVED);
            vehicleRepository.save(tesla);

            dealDocumentRepository.save(document(
                    deal, DocumentType.BUYER_AGREEMENT, DocumentStatus.UPLOADED, "buyer-agreement.pdf",
                    OffsetDateTime.now().minusHours(8)
            ));
            dealDocumentRepository.save(document(
                    deal, DocumentType.DRIVER_LICENSE, DocumentStatus.APPROVED, "driver-license.png",
                    OffsetDateTime.now().minusHours(7)
            ));
            dealDocumentRepository.save(document(
                    deal, DocumentType.INSURANCE_PROOF, DocumentStatus.REQUESTED, "insurance-proof-upload",
                    OffsetDateTime.now().minusHours(6)
            ));

            dealActivityRepository.save(activity(deal, "DEAL_CREATED", "Deal created for Jordan Blake", OffsetDateTime.now().minusHours(10)));
            dealActivityRepository.save(activity(deal, "DEPOSIT_PAID", "Deposit recorded in the amount of 500.00", OffsetDateTime.now().minusHours(9)));
            dealActivityRepository.save(activity(deal, "DOCUMENT_STATUS_UPDATED", "DRIVER_LICENSE marked as APPROVED", OffsetDateTime.now().minusHours(7)));
            dealActivityRepository.save(activity(deal, "FULFILLMENT_UPDATED", "Fulfillment set to SCHEDULED at 1450 Market Street, San Francisco, CA", OffsetDateTime.now().minusHours(2)));
        };
    }

    private Vehicle vehicle(
            Dealer dealer,
            String vin,
            int year,
            String make,
            String model,
            String trim,
            List<String> imageUrls,
            int mileage,
            BigDecimal price,
            VehicleStatus status
    ) {
        Vehicle vehicle = new Vehicle();
        vehicle.setDealer(dealer);
        vehicle.setVin(vin);
        vehicle.setModelYear(year);
        vehicle.setMake(make);
        vehicle.setModel(model);
        vehicle.setTrim(trim);
        vehicle.setImageUrls(imageUrls);
        vehicle.setMileage(mileage);
        vehicle.setPrice(price);
        vehicle.setStatus(status);
        return vehicle;
    }

    private DealDocument document(
            Deal deal,
            DocumentType type,
            DocumentStatus status,
            String fileName,
            OffsetDateTime timestamp
    ) {
        DealDocument document = new DealDocument();
        document.setDeal(deal);
        document.setType(type);
        document.setStatus(status);
        document.setFileName(fileName);
        document.setCreatedAt(timestamp);
        document.setUpdatedAt(timestamp);
        return document;
    }

    private DealActivity activity(Deal deal, String eventType, String message, OffsetDateTime timestamp) {
        DealActivity activity = new DealActivity();
        activity.setDeal(deal);
        activity.setEventType(eventType);
        activity.setMessage(message);
        activity.setCreatedAt(timestamp);
        return activity;
    }
}
