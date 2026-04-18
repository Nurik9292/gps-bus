package biz.ugur.busroutebackend.shared.infrastructure.storage;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ImageMagicBytesTest {

    @Test
    void detectsJpeg() {
        byte[] jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01);
        assertThat(ImageMagicBytes.detect(jpeg)).contains(ImageMagicBytes.Format.JPEG);
    }

    @Test
    void detectsPng() {
        byte[] png = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D);
        assertThat(ImageMagicBytes.detect(png)).contains(ImageMagicBytes.Format.PNG);
    }

    @Test
    void detectsGif87a() {
        byte[] gif = bytes('G', 'I', 'F', '8', '7', 'a', 0x01, 0x00, 0x01, 0x00, 0x00, 0x00);
        assertThat(ImageMagicBytes.detect(gif)).contains(ImageMagicBytes.Format.GIF);
    }

    @Test
    void detectsGif89a() {
        byte[] gif = bytes('G', 'I', 'F', '8', '9', 'a', 0x01, 0x00, 0x01, 0x00, 0x00, 0x00);
        assertThat(ImageMagicBytes.detect(gif)).contains(ImageMagicBytes.Format.GIF);
    }

    @Test
    void detectsWebp() {
        byte[] webp = bytes('R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P');
        assertThat(ImageMagicBytes.detect(webp)).contains(ImageMagicBytes.Format.WEBP);
    }

    @Test
    void rejectsExecutable() {
        byte[] elf = bytes(0x7F, 'E', 'L', 'F', 0x02, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00);
        assertThat(ImageMagicBytes.detect(elf)).isEmpty();
    }

    @Test
    void rejectsWindowsPeExecutable() {
        byte[] mz = bytes('M', 'Z', 0x90, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00);
        assertThat(ImageMagicBytes.detect(mz)).isEmpty();
    }

    @Test
    void rejectsPdf() {
        byte[] pdf = bytes('%', 'P', 'D', 'F', '-', '1', '.', '4', 0x0A, 0x00, 0x00, 0x00);
        assertThat(ImageMagicBytes.detect(pdf)).isEmpty();
    }

    @Test
    void rejectsEmpty() {
        assertThat(ImageMagicBytes.detect(new byte[0])).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThat(ImageMagicBytes.detect(null)).isEmpty();
    }

    @Test
    void rejectsTooShort() {
        assertThat(ImageMagicBytes.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty();
    }

    @Test
    void fromDeclaredExtensionNormalisesJpeg() {
        assertThat(ImageMagicBytes.Format.fromDeclaredExtension("jpeg"))
                .contains(ImageMagicBytes.Format.JPEG);
        assertThat(ImageMagicBytes.Format.fromDeclaredExtension("JPG"))
                .contains(ImageMagicBytes.Format.JPEG);
    }

    @Test
    void fromDeclaredExtensionUnknownFormat() {
        assertThat(ImageMagicBytes.Format.fromDeclaredExtension("svg")).isEmpty();
        assertThat(ImageMagicBytes.Format.fromDeclaredExtension(null)).isEmpty();
    }

    @Test
    void formatAttributes() {
        assertThat(ImageMagicBytes.Format.JPEG.mimeType()).isEqualTo("image/jpeg");
        assertThat(ImageMagicBytes.Format.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageMagicBytes.Format.PNG.mimeType()).isEqualTo("image/png");
        assertThat(ImageMagicBytes.Format.GIF.mimeType()).isEqualTo("image/gif");
        assertThat(ImageMagicBytes.Format.WEBP.mimeType()).isEqualTo("image/webp");
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
