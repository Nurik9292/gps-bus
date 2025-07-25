package biz.ugur.busroutebackend.shared.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

//    private final VehiclePositionHandler vehiclePositionHandler;

//    public WebSocketConfig(VehiclePositionHandler vehiclePositionHandler) {
//        this.vehiclePositionHandler = vehiclePositionHandler;
//    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
//        registry.addHandler(vehiclePositionHandler, "/ws/vehicle-positions")
//                .setAllowedOrigins("*"); // В продакшне указать конкретные домены
    }
}

