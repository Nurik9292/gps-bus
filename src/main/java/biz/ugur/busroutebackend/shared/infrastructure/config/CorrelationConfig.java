package biz.ugur.busroutebackend.shared.infrastructure.config;

import biz.ugur.busroutebackend.shared.infrastructure.correlation.CorrelationIdGenerator;
import biz.ugur.busroutebackend.shared.infrastructure.correlation.CorrelationIdWebFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CorrelationConfig {

    @Bean
    public CorrelationIdGenerator correlationIdGenerator() {
        log.info("Initializing Correlation ID Generator");
        return new CorrelationIdGenerator();
    }

    @Bean
    public CorrelationIdWebFilter correlationIdWebFilter(CorrelationIdGenerator generator) {
        log.info("Initializing Correlation ID Web Filter");
        return new CorrelationIdWebFilter(generator);
    }
}