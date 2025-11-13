package biz.ugur.busroutebackend.banner.infrastructure.config;

import biz.ugur.busroutebackend.banner.domain.services.BannerSchedulingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class BannerDomainConfig {


    @Bean
    public BannerSchedulingService bannerSchedulingService() {
        return new BannerSchedulingService();
    }
}
