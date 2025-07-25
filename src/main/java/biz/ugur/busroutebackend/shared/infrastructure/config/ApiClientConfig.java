package biz.ugur.busroutebackend.shared.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class ApiClientConfig {

    @Bean("gpsApiClient")
    public WebClient gpsApiClient(
            @Value("${external.api.gps.base-url}") String baseUrl,
            @Value("${external.api.gps.timeout:10s}") Duration timeout) {

        return WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    @Bean("busInfoApiClient")
    public WebClient busInfoApiClient(
            @Value("${external.api.bus-info.base-url}") String baseUrl,
            @Value("${external.api.bus-info.timeout:10s}") Duration timeout) {

        return WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }
}
