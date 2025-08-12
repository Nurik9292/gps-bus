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
public class BannerStorageService {

    @Value("${app.storage.banners.base-path:/app/data/banners}")
    private String basePath;

    @Value("${app.storage.banners.base-url:http://localhost:8080/banners}")
    private String baseUrl;

    @Value("${app.storage.banners.max-size-mb:10}")
    private int maxSizeMb;

    @Value("${app.storage.banners.create-thumbnails:false}")
    private boolean createThumbnails;

    @Value("${app.storage.banners.thumbnail-width:400}")
    private int thumbnailWidth;

    @Value("${app.storage.banners.thumbnail-height:200}")
    private int thumbnailHeight;

    @Value("${app.storage.banners.jpeg-quality:0.85}")
    private float jpegQuality;

    public Mono<BannerResult> saveBanner(String base64Data) {
        return Mono.fromCallable(() -> {
            try {

                BannerMetadata metadata = parseBase64Data(base64Data);

                validateBanner(metadata);

                OptimizedImages optimized = optimizeImage(metadata.decodedData, metadata.extension);

                BannerPaths paths = saveImageFiles(optimized, metadata.extension);

                log.info("✅ Banner saved successfully: original={}, thumbnail={}",
                        paths.originalPath, paths.thumbnailPath);

                return new BannerResult(paths.originalPath, paths.thumbnailPath,
                        optimized.originalSize, optimized.thumbnailSize);

            } catch (Exception e) {
                log.error("❌ Failed to save banner: {}", e.getMessage());
                throw new RuntimeException("Failed to save banner: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> saveBannerFile(byte[] fileData, String originalFilename, String contentType) {
        return Mono.fromCallable(() -> {
            try {
                log.info("💾 Saving banner file: {}", originalFilename);

                String extension = getExtensionFromFilename(originalFilename);
                validateFileData(fileData, extension);

                OptimizedImages optimized = optimizeImage(fileData, extension);
                BannerPaths paths = saveImageFiles(optimized, extension);

                log.info("✅ Banner file saved successfully: {}", paths.originalPath);

                return paths.originalPath.replace("/banners/", "");

            } catch (Exception e) {
                log.error("❌ Failed to save banner file: {}", e.getMessage());
                throw new RuntimeException("Failed to save banner file: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> deleteBanner(String bannerPath) {
        if (bannerPath == null || bannerPath.startsWith("data:")) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            try {
                Path originalPath = Paths.get(basePath, bannerPath.replace("banners/", ""));
                log.debug("Before delete banner file: {}", originalPath);
                if (Files.exists(originalPath)) {
                    Files.delete(originalPath);
                    log.info("🗑️ Deleted original banner: {}", bannerPath);
                }

                String thumbnailPath = bannerPath.replace("_original", "_thumb");
                Path thumbPath = Paths.get(basePath, thumbnailPath);
                if (Files.exists(thumbPath)) {
                    Files.delete(thumbPath);
                    log.info("🗑️ Deleted thumbnail banner: {}", thumbnailPath);
                }
            } catch (IOException e) {
                log.warn("⚠️ Failed to delete banner {}: {}", bannerPath, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private BannerMetadata parseBase64Data(String base64Data) {
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
        if ("jpeg".equals(extension)) {
            extension = "jpg";
        }

        byte[] decodedData = Base64.getDecoder().decode(data);

        return new BannerMetadata(extension, decodedData, mimeType);
    }

    private String getExtensionFromFilename(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("Invalid filename");
        }

        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        if (isValidImageExtension(extension)) {
            throw new IllegalArgumentException("Unsupported image format: " + extension);
        }

        return "jpeg".equals(extension) ? "jpg" : extension;
    }

    private boolean isValidImageExtension(String extension) {
        return !extension.equals("jpg") && !extension.equals("jpeg") &&
                !extension.equals("png") && !extension.equals("gif") &&
                !extension.equals("webp");
    }

    private void validateBanner(BannerMetadata metadata) {
        // Check file size
        long sizeInMb = metadata.decodedData.length / (1024 * 1024);
        if (sizeInMb > maxSizeMb) {
            throw new IllegalArgumentException("Banner size exceeds " + maxSizeMb + "MB limit");
        }

        if (isValidImageExtension(metadata.extension)) {
            throw new IllegalArgumentException("Unsupported image format: " + metadata.extension);
        }
    }

    private void validateFileData(byte[] fileData, String extension) {
        long sizeInMb = fileData.length / (1024 * 1024);
        if (sizeInMb > maxSizeMb) {
            throw new IllegalArgumentException("Banner size exceeds " + maxSizeMb + "MB limit");
        }

        if (isValidImageExtension(extension)) {
            throw new IllegalArgumentException("Unsupported image format: " + extension);
        }
    }

    private OptimizedImages optimizeImage(byte[] imageData, String extension) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
        BufferedImage originalImage = ImageIO.read(bais);

        if (originalImage == null) {
            throw new IllegalArgumentException("Invalid image data");
        }

        byte[] optimizedOriginal = optimizeImageQuality(originalImage, extension);

        byte[] thumbnail = null;
        if (createThumbnails) {
            BufferedImage thumbnailImage = resizeImage(originalImage, thumbnailWidth, thumbnailHeight);
            thumbnail = optimizeImageQuality(thumbnailImage, extension);
        }

        return new OptimizedImages(optimizedOriginal, thumbnail,
                optimizedOriginal.length, thumbnail != null ? thumbnail.length : 0);
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

    private BannerPaths saveImageFiles(OptimizedImages images, String extension) throws IOException {
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);

        String originalFileName = String.format("banner_original_%s_%s.%s", timestamp, uniqueId, extension);
        String thumbnailFileName = String.format("banner_thumb_%s_%s.%s", timestamp, uniqueId, extension);

        Path dateDir = Paths.get(basePath, dateFolder);
        Files.createDirectories(dateDir);

        Path originalPath = dateDir.resolve(originalFileName);
        Files.write(originalPath, images.original);

        String originalRelativePath = "/banners/" + dateFolder + "/" + originalFileName;
        String thumbnailRelativePath = null;

        if (images.thumbnail != null) {
            Path thumbnailPath = dateDir.resolve(thumbnailFileName);
            Files.write(thumbnailPath, images.thumbnail);
            thumbnailRelativePath = "/banners/" + dateFolder + "/" + thumbnailFileName;
        }

        return new BannerPaths(originalRelativePath, thumbnailRelativePath);
    }

    private record BannerMetadata(String extension, byte[] decodedData, String mimeType) {}
    private record OptimizedImages(byte[] original, byte[] thumbnail, int originalSize, int thumbnailSize) {}
    private record BannerPaths(String originalPath, String thumbnailPath) {}

    public record BannerResult(String originalPath, String thumbnailPath, int originalSize, int thumbnailSize) {
        public String getDisplayUrl() {
            return thumbnailPath != null ? thumbnailPath : originalPath;
        }

        public String getFullUrl(String baseUrl) {
            return baseUrl + getDisplayUrl();
        }
    }
}