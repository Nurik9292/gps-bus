package biz.ugur.busroutebackend.shared.infrastructure.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.mail")
@Getter
@Setter
public class MailProperties {
    private boolean enabled = false;
    private String adminEmail;
    private String from;
    private String host = "smtp.gmail.com";
    private int port = 587;
    private String username;
    private String password;
}
