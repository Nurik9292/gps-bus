package biz.ugur.busroutebackend.client.infrastructure.security;

import biz.ugur.busroutebackend.shared.infrastructure.security.BaseJwtProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.jwt.client")
public class ClientJwtProperties extends BaseJwtProperties {

    @Override
    protected String getContext() {
        return "Client";
    }
}
