package biz.ugur.busroutebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
public class BusRouteBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusRouteBackendApplication.class, args);
    }

}
