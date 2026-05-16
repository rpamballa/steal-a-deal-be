package com.stealadeal.web;

import com.stealadeal.service.DealerOnboardingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dealers")
public class DealerOnboardingController {

    private final DealerOnboardingService onboardingService;

    public DealerOnboardingController(DealerOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping("/{dealerId}/onboarding")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public DealerOnboardingService.OnboardingView getOnboarding(@PathVariable Long dealerId) {
        return onboardingService.getOrEvaluate(dealerId);
    }
}
