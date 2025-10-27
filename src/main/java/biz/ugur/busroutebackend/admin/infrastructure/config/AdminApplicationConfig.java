package biz.ugur.busroutebackend.admin.infrastructure.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {
        "biz.ugur.busroutebackend.admin.application.usecase",
        "biz.ugur.busroutebackend.admin.infrastructure.persistence.repository",
        "biz.ugur.busroutebackend.admin.infrastructure.persistence.entity",
        "biz.ugur.busroutebackend.transport.application.usecase",
        "biz.ugur.busroutebackend.transport.infrastructure.repository"
})
public class AdminApplicationConfig {

}