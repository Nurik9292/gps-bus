package biz.ugur.busroutebackend.banner.infrastructure.config;

import biz.ugur.busroutebackend.banner.domain.services.BannerConflictDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class BannerDomainConfig {

    @Bean
    public BannerConflictDetector bannerConflictDetector() {
        return new BannerConflictDetector();
    }
}
