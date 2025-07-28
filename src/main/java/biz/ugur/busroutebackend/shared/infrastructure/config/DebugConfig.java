package biz.ugur.busroutebackend.shared.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DebugConfig {
    public DebugConfig(@Value("${app.scheduling.enabled:false}") boolean enabled) {
        log.info("DEBUG >>> app.scheduling.enabled = {}", enabled);
    }
}