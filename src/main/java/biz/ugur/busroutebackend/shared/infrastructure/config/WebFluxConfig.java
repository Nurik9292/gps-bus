package biz.ugur.busroutebackend.shared.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.multipart.DefaultPartHttpMessageReader;
import org.springframework.http.codec.multipart.MultipartHttpMessageReader;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Slf4j
@Configuration
@EnableWebFlux
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {

        configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB

        configurer.defaultCodecs().enableLoggingRequestDetails(true);

    }

    @Bean
    public MultipartHttpMessageReader multipartReader() {
        return new MultipartHttpMessageReader(new DefaultPartHttpMessageReader());
    }
}