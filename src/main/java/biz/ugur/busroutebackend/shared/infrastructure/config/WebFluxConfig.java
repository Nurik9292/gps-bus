package biz.ugur.busroutebackend.shared.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.multipart.DefaultPartHttpMessageReader;
import org.springframework.http.codec.multipart.MultipartHttpMessageReader;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Slf4j
@Configuration
@EnableWebFlux
public class WebFluxConfig implements WebFluxConfigurer {

    @Value("${app.storage.avatars.base-path:/app/data/avatars}")
    private String avatarsBasePath;

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