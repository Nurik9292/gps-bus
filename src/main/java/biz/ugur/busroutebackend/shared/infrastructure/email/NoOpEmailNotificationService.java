package biz.ugur.busroutebackend.shared.infrastructure.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEmailNotificationService implements EmailNotificationService {

    @Override
    public Mono<Void> sendComplaintNotification(String title, String type, String description) {
        log.debug("Email disabled, skipping complaint notification for: {}", title);
        return Mono.empty();
    }
}
