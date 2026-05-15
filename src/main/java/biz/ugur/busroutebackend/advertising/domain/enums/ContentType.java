package biz.ugur.busroutebackend.advertising.domain.enums;

public enum ContentType {
    CONTENT,
    LINK;

    public static ContentType from(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return ContentType.valueOf(raw.trim().toUpperCase());
    }
}
