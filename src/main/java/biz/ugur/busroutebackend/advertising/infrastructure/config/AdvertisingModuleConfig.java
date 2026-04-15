package biz.ugur.busroutebackend.advertising.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Marker configuration for the <b>advertising</b> bounded context.
 *
 * <p>Contains two aggregates: {@code AdTariff} (pricing catalog) and {@code AdPlacement}
 * (concrete ad purchased by a business). Beans are picked up by component-scanning. A future
 * scheduler (to flip {@code SCHEDULED → ACTIVE → EXPIRED} placements) will be registered here.
 */
@Configuration
public class AdvertisingModuleConfig {
}
