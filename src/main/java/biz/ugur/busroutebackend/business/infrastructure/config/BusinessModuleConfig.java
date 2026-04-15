package biz.ugur.busroutebackend.business.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Marker configuration for the <b>business</b> bounded context.
 *
 * <p>All beans in this module (repositories, use cases, factories, mappers) are discovered
 * via classpath component-scanning (@Repository / @Service / @Component). This class exists
 * so the module has an explicit configuration anchor and can host module-scoped beans later
 * (e.g. domain services, schedulers) without requiring package rewiring.
 */
@Configuration
public class BusinessModuleConfig {
}
