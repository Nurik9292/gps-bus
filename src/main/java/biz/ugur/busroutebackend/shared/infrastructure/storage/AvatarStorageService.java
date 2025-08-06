package biz.ugur.busroutebackend.shared.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarStorageService {

    @Value("${app.storage.avatars.base-path:/app/data/avatars}")
    private String basePath;

    @Value("${app.storage.avatars.base-url:http://localhost:8080/uploads/avatars}")
    private String baseUrl;

    @Value("${app.storage.avatars.max-size-mb:2}")
    private int maxSizeMb;

    @Value("${app.storage.avatars.create-thumbnails:true}")
    private boolean createThumbnails;

    @Value("${app.storage.avatars.thumbnail-size:150}")
    private int thumbnailSize;

    @Value("${app.storage.avatars.jpeg-quality:0.85}")
    private float jpegQuality;

    public Mono<AvatarResult> saveAvatar(String adminId, String base64Data) {
        return Mono.fromCallable(() -> {
            try {
                log.info("💾 Saving avatar for admin: {}", adminId);

                AvatarMetadata metadata = parseBase64Data(base64Data);

                validateAvatar(metadata);

                OptimizedImages optimized = optimizeImage(metadata.decodedData, metadata.extension);

                AvatarPaths paths = saveImageFiles(adminId, optimized, metadata.extension);

                log.info("✅ Avatar saved successfully for admin {}: original={}, thumbnail={}",
                        adminId, paths.originalPath, paths.thumbnailPath);

                return new AvatarResult(paths.originalPath, paths.thumbnailPath, optimized.originalSize, optimized.thumbnailSize);

            } catch (Exception e) {
                log.error("❌ Failed to save avatar for admin {}: {}", adminId, e.getMessage());
                throw new RuntimeException("Failed to save avatar: " + e.getMessage(), e);
            }
        });
    }

    public Mono<Void> deleteAvatar(String avatarPath) {
        if (avatarPath == null || avatarPath.startsWith("data:")) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            try {
                Path originalPath = Paths.get(basePath + avatarPath.replace("avatars/", ""));
                System.out.println("originalPath: " + originalPath.toString());
                if (Files.exists(originalPath)) {
                    Files.delete(originalPath);
                    log.info("🗑️ Deleted original avatar: {}", avatarPath);
                }

                String thumbnailPath = avatarPath.replace("_original", "_thumb");
                Path thumbPath = Paths.get(basePath + thumbnailPath);
                if (Files.exists(thumbPath)) {
                    Files.delete(thumbPath);
                    log.info("🗑️ Deleted thumbnail avatar: {}", thumbnailPath);
                }
            } catch (IOException e) {
                log.warn("⚠️ Failed to delete avatar {}: {}", avatarPath, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private AvatarMetadata parseBase64Data(String base64Data) {
        if (!base64Data.startsWith("data:image/")) {
            throw new IllegalArgumentException("Invalid base64 image format");
        }

        String[] parts = base64Data.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid base64 format");
        }

        String header = parts[0];
        String data = parts[1];

        String mimeType = header.substring(5, header.indexOf(";"));
        String extension = mimeType.substring(mimeType.indexOf("/") + 1);
        if ("jpeg".equals(extension)) extension = "jpg";

        byte[] decodedData = Base64.getDecoder().decode(data);

        return new AvatarMetadata(extension, decodedData, mimeType);
    }

    private void validateAvatar(AvatarMetadata metadata) {
        int sizeMb = metadata.decodedData.length / (1024 * 1024);
        if (sizeMb > maxSizeMb) {
            throw new IllegalArgumentException(
                    String.format("Avatar too large: %dMB (max %dMB)", sizeMb, maxSizeMb)
            );
        }

        String[] allowedFormats = {"jpg", "jpeg", "png", "webp", "gif"};
        boolean formatAllowed = false;
        for (String format : allowedFormats) {
            if (format.equals(metadata.extension)) {
                formatAllowed = true;
                break;
            }
        }

        if (!formatAllowed) {
            throw new IllegalArgumentException("Unsupported format: " + metadata.extension);
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(metadata.decodedData));
            if (image == null) {
                throw new IllegalArgumentException("Invalid image data");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read image data");
        }
    }

    private OptimizedImages optimizeImage(byte[] originalData, String format) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalData));

        byte[] optimizedOriginal = optimizeImageQuality(original, format);

        byte[] thumbnail = null;
        if (createThumbnails) {
            BufferedImage thumbnailImage = resizeImage(original, thumbnailSize, thumbnailSize);
            thumbnail = optimizeImageQuality(thumbnailImage, format);
        }

        return new OptimizedImages(optimizedOriginal, thumbnail, optimizedOriginal.length,
                thumbnail != null ? thumbnail.length : 0);
    }

    private BufferedImage resizeImage(BufferedImage original, int maxWidth, int maxHeight) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();

        double widthRatio = (double) maxWidth / originalWidth;
        double heightRatio = (double) maxHeight / originalHeight;
        double ratio = Math.min(widthRatio, heightRatio);

        int newWidth = (int) (originalWidth * ratio);
        int newHeight = (int) (originalHeight * ratio);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();

        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, newWidth, newHeight);

        graphics.drawImage(original, 0, 0, newWidth, newHeight, null);
        graphics.dispose();

        return resized;
    }

    private byte[] optimizeImageQuality(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if ("jpg".equals(format) || "jpeg".equals(format)) {
            var writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (writers.hasNext()) {
                var writer = writers.next();
                var writeParam = writer.getDefaultWriteParam();
                writeParam.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(jpegQuality);

                try (var ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    writer.write(null, new javax.imageio.IIOImage(image, null, null), writeParam);
                    writer.dispose();
                }
            }
        } else {
            ImageIO.write(image, format, baos);
        }

        return baos.toByteArray();
    }

    private AvatarPaths saveImageFiles(String adminId, OptimizedImages images, String extension) throws IOException {
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);

        String originalFileName = String.format("%s_original_%s_%s.%s", adminId, timestamp, uniqueId, extension);
        String thumbnailFileName = String.format("%s_thumb_%s_%s.%s", adminId, timestamp, uniqueId, extension);

        Path dateDir = Paths.get(basePath, dateFolder);
        Files.createDirectories(dateDir);

        Path originalPath = dateDir.resolve(originalFileName);
        Files.write(originalPath, images.original);

        String originalRelativePath = "/avatars/" + dateFolder + "/" + originalFileName;
        String thumbnailRelativePath = null;

        if (images.thumbnail != null) {
            Path thumbnailPath = dateDir.resolve(thumbnailFileName);
            Files.write(thumbnailPath, images.thumbnail);
            thumbnailRelativePath = "/avatars/" + dateFolder + "/" + thumbnailFileName;
        }

        return new AvatarPaths(originalRelativePath, thumbnailRelativePath);
    }

    private record AvatarMetadata(String extension, byte[] decodedData, String mimeType) {}
    private record OptimizedImages(byte[] original, byte[] thumbnail, int originalSize, int thumbnailSize) {}
    private record AvatarPaths(String originalPath, String thumbnailPath) {}

    public record AvatarResult(String originalPath, String thumbnailPath, int originalSize, int thumbnailSize) {
        public String getDisplayUrl() {
            return thumbnailPath != null ? thumbnailPath : originalPath;
        }

        public String getFullUrl(String baseUrl) {
            return baseUrl + getDisplayUrl();
        }
    }
}