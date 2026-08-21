package biz.ugur.busroutebackend.advertising.infrastructure.external.tanat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TanatBannerResponse(
        boolean success,
        String message,
        Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(Banner banner) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Banner(
            String lang,
            @JsonProperty("banner_file") String bannerFile,
            String hash,
            String url) {
    }

    public boolean carriesBanner() {
        return success
                && data != null
                && data.banner() != null
                && data.banner().hash() != null
                && !data.banner().hash().isBlank()
                && data.banner().bannerFile() != null
                && !data.banner().bannerFile().isBlank();
    }

    public Banner bannerOrNull() {
        return carriesBanner() ? data.banner() : null;
    }
}
