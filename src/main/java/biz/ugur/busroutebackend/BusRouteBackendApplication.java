package biz.ugur.busroutebackend;

import biz.ugur.busroutebackend.shared.infrastructure.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties(JwtProperties.class)
@SpringBootApplication
public class BusRouteBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusRouteBackendApplication.class, args);
    }

}
