package com.stealadeal.service;

import com.stealadeal.config.FeeProperties;
import com.stealadeal.domain.Deal;
import com.stealadeal.repository.DealRepository;
import com.stealadeal.service.billing.BillingProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes and settles the per-deal platform transaction fee (the
 * pitch's revenue stream #2). Settlement runs the moment a deal
 * completes; the actual money movement is delegated to the
 * {@link BillingProvider} so a Stripe-Connect transfer drops in later.
 */
@Service
@Transactional
public class TransactionFeeService {

    private static final Logger log = LoggerFactory.getLogger(TransactionFeeService.class);

    private final FeeProperties feeProperties;
    private final BillingProvider billingProvider;
    private final DealRepository dealRepository;

    public TransactionFeeService(
            FeeProperties feeProperties,
            BillingProvider billingProvider,
            DealRepository dealRepository
    ) {
        this.feeProperties = feeProperties;
        this.billingProvider = billingProvider;
        this.dealRepository = dealRepository;
    }

    public BigDecimal computeFee(Deal deal) {
        return deal.getVehiclePrice()
                .multiply(feeProperties.transactionRate())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Idempotent: a deal whose fee is already settled is left untouched.
     * Returns the deal with fee fields populated.
     */
    public Deal settleForCompletedDeal(Deal deal) {
        if (deal.isPlatformFeeSettled()) {
            return deal;
        }
        BigDecimal fee = computeFee(deal);
        deal.setPlatformFeeRate(feeProperties.transactionRate());
        deal.setPlatformFeeAmount(fee);

        BillingProvider.TransactionFeeRef ref = billingProvider.chargeTransactionFee(
                new BillingProvider.TransactionFeeRequest(
                        deal.getId(),
                        deal.getVehicle().getDealer().getId(),
                        fee,
                        "usd"
                )
        );
        deal.setPlatformFeeChargeId(ref.chargeId());
        if ("SETTLED".equalsIgnoreCase(ref.status())) {
            deal.setPlatformFeeSettled(true);
            deal.setPlatformFeeSettledAt(OffsetDateTime.now());
        } else {
            log.warn("[fees] transaction fee for deal {} not settled: provider status {}",
                    deal.getId(), ref.status());
        }
        return dealRepository.save(deal);
    }
}
