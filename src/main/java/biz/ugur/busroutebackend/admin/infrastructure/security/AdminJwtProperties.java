package biz.ugur.busroutebackend.admin.infrastructure.security;

import biz.ugur.busroutebackend.shared.infrastructure.security.BaseJwtProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "app.security.jwt.admin")
public class AdminJwtProperties extends BaseJwtProperties {

    @Override
    protected String getContext() {
        return "Admin";
    }
}
