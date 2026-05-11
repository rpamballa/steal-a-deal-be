package com.stealadeal.config;

import com.stealadeal.service.AuthService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapAdminConfig {

    @Bean
    CommandLineRunner ensureBootstrapAdmin(AuthService authService) {
        return args -> authService.ensureBootstrapAdmin();
    }
}
