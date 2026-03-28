package biz.ugur.busroutebackend.shared.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.multipart.DefaultPartHttpMessageReader;
import org.springframework.http.codec.multipart.MultipartHttpMessageReader;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import reactor.core.publisher.Hooks;

@Slf4j
@Configuration
@EnableWebFlux
public class WebFluxConfig implements WebFluxConfigurer {

    @Value("${app.storage.avatars.base-path:/app/data/avatars}")
    private String avatarsBasePath;

    /**
     * Suppress the "client response body has been released due to cancellation" error that
     * Reactor Netty emits when a WebClient request is cancelled (e.g. by .timeout()) at the
     * exact moment the HTTP response body starts arriving. This is a race condition in Reactor
     * Netty, not a bug in application logic — the cancellation itself works correctly, the
     * request is abandoned, and no data is lost. Without this hook the error gets logged as
     * ERROR via the default onErrorDropped handler, which is misleading.
     */
    @PostConstruct
    public void configureReactorHooks() {
        Hooks.onErrorDropped(error -> {
            if (error instanceof IllegalStateException
                    && error.getMessage() != null
                    && error.getMessage().contains("client response body has been released")) {
                log.debug("Suppressed WebClient cancellation race: {}", error.getMessage());
            } else {
                log.error("Reactor onErrorDropped: {}", error.getMessage(), error);
            }
        });
    }

    @Value("${app.storage.banners.base-path:/app/data/banners}")
    private String bannersBasePath;



    @Bean
    public MultipartHttpMessageReader multipartReader() {
        return new MultipartHttpMessageReader(new DefaultPartHttpMessageReader());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/*.html")
                .addResourceLocations("classpath:/static/");
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");

        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:" + avatarsBasePath + "/");
        registry.addResourceHandler("/api/v1/avatars/**")
                .addResourceLocations("file:" + avatarsBasePath + "/");

        registry.addResourceHandler("/banners/**")
                .addResourceLocations("file:" + bannersBasePath + "/");
        registry.addResourceHandler("/api/v1/banners/**")
                .addResourceLocations("file:" + bannersBasePath + "/");
    }
}