package biz.ugur.busroutebackend.shared.infrastructure.storage;

import java.util.Optional;

public final class ImageMagicBytes {

    private ImageMagicBytes() {
    }

    public enum Format {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        GIF("image/gif", "gif"),
        WEBP("image/webp", "webp");

        private final String mimeType;
        private final String extension;

        Format(String mimeType, String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }

        public String mimeType() {
            return mimeType;
        }

        public String extension() {
            return extension;
        }

        public static Optional<Format> fromDeclaredExtension(String extension) {
            if (extension == null) {
                return Optional.empty();
            }
            String normalized = "jpeg".equalsIgnoreCase(extension) ? "jpg" : extension.toLowerCase();
            for (Format f : values()) {
                if (f.extension.equals(normalized)) {
                    return Optional.of(f);
                }
            }
            return Optional.empty();
        }
    }

    public static Optional<Format> detect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return Optional.empty();
        }
        if (isJpeg(bytes)) {
            return Optional.of(Format.JPEG);
        }
        if (isPng(bytes)) {
            return Optional.of(Format.PNG);
        }
        if (isGif(bytes)) {
            return Optional.of(Format.GIF);
        }
        if (isWebp(bytes)) {
            return Optional.of(Format.WEBP);
        }
        return Optional.empty();
    }

    private static boolean isJpeg(byte[] b) {
        return (b[0] & 0xFF) == 0xFF
                && (b[1] & 0xFF) == 0xD8
                && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return (b[0] & 0xFF) == 0x89
                && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D
                && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A
                && (b[7] & 0xFF) == 0x0A;
    }

    private static boolean isGif(byte[] b) {
        return b[0] == 'G' && b[1] == 'I' && b[2] == 'F'
                && b[3] == '8'
                && (b[4] == '7' || b[4] == '9')
                && b[5] == 'a';
    }

    private static boolean isWebp(byte[] b) {
        return b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
