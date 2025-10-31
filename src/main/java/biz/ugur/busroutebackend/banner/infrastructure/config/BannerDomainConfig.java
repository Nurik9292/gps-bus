package biz.ugur.busroutebackend.banner.infrastructure.config;

import biz.ugur.busroutebackend.banner.domain.services.BannerSchedulingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Banner domain services.
 * Registers domain services as Spring beans while keeping domain layer clean from framework dependencies.
 *
 * @author Claude Code
 * @since 2025-10-30
 */
@Configuration
public class BannerDomainConfig {

    /**
     * Banner scheduling domain service bean.
     * Handles banner activation logic based on time periods.
     *
     * @return BannerSchedulingService instance
     */
    @Bean
    public BannerSchedulingService bannerSchedulingService() {
        return new BannerSchedulingService();
    }
}
