package biz.ugur.busroutebackend.interfaces.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/actuator/health")
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/health")
    public Mono<Map<String, String>> simpleHealth() {
        return Mono.just(Map.of("status", "UP"));
    }
}