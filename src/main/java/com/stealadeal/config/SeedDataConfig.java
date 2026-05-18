package com.stealadeal.config;

import com.stealadeal.domain.Appointment;
import com.stealadeal.domain.AppointmentStatus;
import com.stealadeal.domain.AppointmentType;
import com.stealadeal.domain.Dealer;
import com.stealadeal.domain.Deal;
import com.stealadeal.domain.DealActivity;
import com.stealadeal.domain.DealDocument;
import com.stealadeal.domain.DealerInvoice;
import com.stealadeal.domain.DealerSubscription;
import com.stealadeal.domain.DealStage;
import com.stealadeal.domain.Lead;
import com.stealadeal.domain.LeadStatus;
import com.stealadeal.domain.DocumentStatus;
import com.stealadeal.domain.DocumentType;
import com.stealadeal.domain.FulfillmentStatus;
import com.stealadeal.domain.FulfillmentType;
import com.stealadeal.domain.InvoiceStatus;
import com.stealadeal.domain.ParticipantType;
import com.stealadeal.domain.SubscriptionPlan;
import com.stealadeal.domain.SubscriptionStatus;
import com.stealadeal.domain.Vehicle;
import com.stealadeal.domain.VehicleStatus;
import com.stealadeal.repository.AppointmentRepository;
import com.stealadeal.repository.DealActivityRepository;
import com.stealadeal.repository.DealerRepository;
import com.stealadeal.repository.DealerInvoiceRepository;
import com.stealadeal.repository.DealerSubscriptionRepository;
import com.stealadeal.repository.DealDocumentRepository;
import com.stealadeal.repository.DealRepository;
import com.stealadeal.repository.LeadRepository;
import com.stealadeal.repository.UserAccountRepository;
import com.stealadeal.repository.VehicleRepository;
import com.stealadeal.domain.UserAccount;
import com.stealadeal.domain.UserRole;
import com.stealadeal.service.TaskNotificationService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("!test")
public class SeedDataConfig {

    @Bean
    @ConditionalOnProperty(name = "app.seed.demo.enabled", havingValue = "true", matchIfMissing = true)
    CommandLineRunner seedData(
            DealerRepository dealerRepository,
            VehicleRepository vehicleRepository,
            LeadRepository leadRepository,
            AppointmentRepository appointmentRepository,
            DealRepository dealRepository,
            DealDocumentRepository dealDocumentRepository,
            DealActivityRepository dealActivityRepository,
            DealerSubscriptionRepository dealerSubscriptionRepository,
            DealerInvoiceRepository dealerInvoiceRepository,
            TaskNotificationService taskNotificationService,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder
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
            // §8.3 regulatory disclosures are mandatory on every deal.
            // The seeded deal is hand-built (not via DealService.createDeal),
            // so add them explicitly to mirror real API-created deals.
            dealDocumentRepository.save(document(
                    deal, DocumentType.ODOMETER_DISCLOSURE, DocumentStatus.REQUESTED, "odometer-disclosure.pdf",
                    OffsetDateTime.now().minusHours(6)
            ));
            dealDocumentRepository.save(document(
                    deal, DocumentType.AS_IS_DISCLOSURE, DocumentStatus.REQUESTED, "as-is-disclosure.pdf",
                    OffsetDateTime.now().minusHours(6)
            ));

            dealActivityRepository.save(activity(deal, "DEAL_CREATED", "Deal created for Jordan Blake", OffsetDateTime.now().minusHours(10)));
            dealActivityRepository.save(activity(deal, "DEPOSIT_PAID", "Deposit recorded in the amount of 500.00", OffsetDateTime.now().minusHours(9)));
            dealActivityRepository.save(activity(deal, "DOCUMENT_STATUS_UPDATED", "DRIVER_LICENSE marked as APPROVED", OffsetDateTime.now().minusHours(7)));
            dealActivityRepository.save(activity(deal, "FULFILLMENT_UPDATED", "Fulfillment set to SCHEDULED at 1450 Market Street, San Francisco, CA", OffsetDateTime.now().minusHours(2)));

            taskNotificationService.syncForDeal(deal);
            taskNotificationService.createNotification(
                    deal,
                    ParticipantType.BUYER,
                    deal.getBuyerEmail(),
                    "Insurance proof still needed",
                    "Upload and complete the insurance proof task to move this deal forward."
            );
            taskNotificationService.createNotification(
                    deal,
                    ParticipantType.DEALER,
                    String.valueOf(deal.getVehicle().getDealer().getId()),
                    "Deal waiting on buyer document",
                    "Insurance proof is still pending for Jordan Blake's Tesla deal."
            );

            dealerSubscriptionRepository.save(subscription(
                    westCoast,
                    SubscriptionPlan.GROWTH,
                    SubscriptionStatus.ACTIVE,
                    new BigDecimal("1100.00"),
                    OffsetDateTime.now().minusDays(15),
                    OffsetDateTime.now().plusDays(15),
                    true
            ));
            dealerSubscriptionRepository.save(subscription(
                    urban,
                    SubscriptionPlan.STARTER,
                    SubscriptionStatus.TRIALING,
                    new BigDecimal("699.00"),
                    OffsetDateTime.now().minusDays(5),
                    OffsetDateTime.now().plusDays(25),
                    true
            ));

            dealerInvoiceRepository.save(invoice(
                    westCoast,
                    "INV-1-0001",
                    InvoiceStatus.PAID,
                    new BigDecimal("1100.00"),
                    OffsetDateTime.now().minusDays(45),
                    OffsetDateTime.now().minusDays(15),
                    OffsetDateTime.now().minusDays(10),
                    OffsetDateTime.now().minusDays(12)
            ));
            dealerInvoiceRepository.save(invoice(
                    westCoast,
                    "INV-1-0002",
                    InvoiceStatus.OPEN,
                    new BigDecimal("1100.00"),
                    OffsetDateTime.now().minusDays(15),
                    OffsetDateTime.now().plusDays(15),
                    OffsetDateTime.now().plusDays(7),
                    null
            ));

            userAccountRepository.save(user("West Coast Owner", "dealer1@stealadeal.local", "Dealer123!", UserRole.DEALER, westCoast.getId(), passwordEncoder));
            userAccountRepository.save(user("Urban Drive Owner", "dealer2@stealadeal.local", "Dealer123!", UserRole.DEALER, urban.getId(), passwordEncoder));
            userAccountRepository.save(user("Jordan Blake", "jordan@example.com", "Buyer123!", UserRole.BUYER, null, passwordEncoder));
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

    private DealerSubscription subscription(
            Dealer dealer,
            SubscriptionPlan plan,
            SubscriptionStatus status,
            BigDecimal monthlyPrice,
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd,
            boolean autoRenew
    ) {
        DealerSubscription subscription = new DealerSubscription();
        subscription.setDealer(dealer);
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setMonthlyPrice(monthlyPrice);
        subscription.setCurrentPeriodStart(periodStart);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setAutoRenew(autoRenew);
        subscription.setCreatedAt(periodStart);
        subscription.setUpdatedAt(OffsetDateTime.now());
        return subscription;
    }

    private DealerInvoice invoice(
            Dealer dealer,
            String invoiceNumber,
            InvoiceStatus status,
            BigDecimal amount,
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd,
            OffsetDateTime dueAt,
            OffsetDateTime paidAt
    ) {
        DealerInvoice invoice = new DealerInvoice();
        invoice.setDealer(dealer);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setStatus(status);
        invoice.setAmount(amount);
        invoice.setPeriodStart(periodStart);
        invoice.setPeriodEnd(periodEnd);
        invoice.setDueAt(dueAt);
        invoice.setPaidAt(paidAt);
        invoice.setCreatedAt(periodStart);
        return invoice;
    }

    private UserAccount user(
            String displayName,
            String email,
            String rawPassword,
            UserRole role,
            Long dealerId,
            PasswordEncoder passwordEncoder
    ) {
        UserAccount user = new UserAccount();
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setDealerId(dealerId);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }
}
