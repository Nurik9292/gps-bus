package biz.ugur.busroutebackend.shared.infrastructure.config;

import biz.ugur.busroutebackend.interfaces.websocket.VehiclePositionHandler;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.API_V1;

@Log4j2
@Configuration
public class WebSocketConfig {

    private final VehiclePositionHandler vehiclePositionHandler;

    public WebSocketConfig(VehiclePositionHandler vehiclePositionHandler) {
        this.vehiclePositionHandler = vehiclePositionHandler;
        log.info("🔌 WebSocketConfig initialized with handler: {}",
                vehiclePositionHandler.getClass().getSimpleName());
    }


    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        log.info("🗺️ Registering WebSocket handler mappings...");
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put(API_V1 + "/ws/vehicle-positions", vehiclePositionHandler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1);



        CorsConfiguration  corsConfig =  new CorsConfiguration();
        corsConfig.setAllowCredentials(true);
        corsConfig.addAllowedOriginPattern("*");
        corsConfig.addAllowedHeader("*");
        corsConfig.addAllowedMethod("*");

        corsConfig.addAllowedHeader("Sec-WebSocket-Key");
        corsConfig.addAllowedHeader("Sec-WebSocket-Version");
        corsConfig.addAllowedHeader("Sec-WebSocket-Extensions");
        corsConfig.addAllowedHeader("Sec-WebSocket-Protocol");
        corsConfig.addAllowedHeader("Upgrade");
        corsConfig.addAllowedHeader("Connection");
        corsConfig.addAllowedHeader("Origin");

        mapping.setCorsConfigurations(Map.of("/**", corsConfig));

        log.info("✅ WebSocket mappings registered:");
        map.forEach((path, handler) ->
                log.info("   📍 {} -> {}", path, handler.getClass().getSimpleName())
        );

        return mapping;
    }


    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        log.info("🔧 Creating WebSocketHandlerAdapter...");
        WebSocketHandlerAdapter adapter = new WebSocketHandlerAdapter();
        log.info("✅ WebSocketHandlerAdapter created successfully");
        return adapter;
    }
}
