package biz.ugur.busroutebackend.client.domain.enums;

import lombok.Getter;

@Getter
public enum Platform {
    ANDROID("Android"),
    IOS("iOS"),
    WEB("Web"),
    MOBILE_WEB("Mobile Web"),
    API("API Integration");

    private final String displayName;

    Platform(String displayName) {
        this.displayName = displayName;
    }

}