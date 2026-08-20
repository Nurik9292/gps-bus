package biz.ugur.busroutebackend.advertising.infrastructure.external.tanat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "external.api.tanat")
public class TanatBannerProperties {

    private boolean enabled = false;

    private String baseUrl;

    private String positionKey;

    private String serviceId = "tanat";

    private Duration fetchInterval = Duration.ofMinutes(5);

    private List<String> languages = List.of("tk", "ru", "en");

    private List<String> devices = List.of("mobile");

    private List<String> operatingSystems = List.of("android", "ios");
}
