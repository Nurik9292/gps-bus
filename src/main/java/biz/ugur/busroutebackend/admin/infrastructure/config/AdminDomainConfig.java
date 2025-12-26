package biz.ugur.busroutebackend.admin.infrastructure.config;

import biz.ugur.busroutebackend.admin.domain.services.AdminFactory;
import biz.ugur.busroutebackend.shared.domain.services.PasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminDomainConfig {

    @Bean
    public AdminFactory adminFactory(PasswordEncoder passwordEncoder) {
        return new AdminFactory(passwordEncoder);
    }
}
