package com.stealadeal.service;

import com.stealadeal.domain.Dealer;
import com.stealadeal.repository.DealerRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically re-evaluates onboarding for every dealer so milestones
 * advance and stuck-dealer nudges fire without any manual trigger.
 * Each dealer is evaluated in its own transaction so one failure does
 * not abort the batch.
 */
@Component
public class DealerOnboardingProcessor {

    private static final Logger log = LoggerFactory.getLogger(DealerOnboardingProcessor.class);

    private final DealerRepository dealerRepository;
    private final DealerOnboardingService onboardingService;

    public DealerOnboardingProcessor(
            DealerRepository dealerRepository,
            DealerOnboardingService onboardingService
    ) {
        this.dealerRepository = dealerRepository;
        this.onboardingService = onboardingService;
    }

    @Scheduled(fixedDelayString = "${app.onboarding.poll-ms:3600000}")
    public int runOnce() {
        List<Dealer> dealers = dealerRepository.findAll();
        int evaluated = 0;
        for (Dealer dealer : dealers) {
            try {
                onboardingService.evaluate(dealer);
                evaluated++;
            } catch (RuntimeException exception) {
                log.warn("[onboarding] evaluation failed for dealer {}: {}",
                        dealer.getId(), exception.getMessage());
            }
        }
        if (evaluated > 0) {
            log.info("[onboarding] evaluated {} dealers", evaluated);
        }
        return evaluated;
    }
}
