package biz.ugur.busroutebackend.transport.infrastructure.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DebugEventTap {

    @EventListener
    public void onAny(Object e) {
        log.info("Spring event received: {}", e.getClass().getName());
    }
}
