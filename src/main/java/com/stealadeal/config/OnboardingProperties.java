package com.stealadeal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.onboarding")
public record OnboardingProperties(
        Integer staleHours,
        Integer maxNudgesPerStage
) {

    public OnboardingProperties {
        if (staleHours == null || staleHours < 0) {
            staleHours = 48;
        }
        if (maxNudgesPerStage == null || maxNudgesPerStage < 1) {
            maxNudgesPerStage = 3;
        }
    }
}
