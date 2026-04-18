package biz.ugur.busroutebackend.shared.infrastructure.storage;

import lombok.extern.log4j.Log4j2;
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
import java.util.Optional;
import java.util.UUID;

@Log4j2
public abstract class BaseImageStorageService {

    protected Mono<Result> storeBase64Image(String base64Data) {
        return Mono.fromCallable(() -> {
            try {

                Metadata metadata = parseBase64Data(base64Data);

                validate(metadata);

                OptimizedImages optimized = optimizeImage(metadata.decodedData, metadata.extension);

                ImagePaths paths = saveImageFiles(optimized, metadata.extension);

                return new Result(paths.originalPath, paths.thumbnailPath,
                        optimized.originalSize, optimized.thumbnailSize);

            } catch (Exception e) {
                log.error("❌ Failed to save banner: {}", e.getMessage());
                throw new RuntimeException("Failed to save banner.", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> deleteFile(String urlPath) {
        if (urlPath == null || urlPath.isBlank() || urlPath.startsWith("data:")) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            try {
                String relativePath = urlPath.replace(dir(), "");
                Path originalPath = Paths.get(basePath(), relativePath);

                if (Files.exists(originalPath)) {
                    Files.delete(originalPath);
                    log.info("🗑️ Deleted original file: {}", originalPath);
                } else {
                    log.warn("⚠️ Original file not found: {}", originalPath);
                }

                if (createThumbnails()) {
                    String thumbnailRelativePath = relativePath.replace("original_", "thumb_");
                    Path thumbPath = Paths.get(basePath(), thumbnailRelativePath);

                    if (Files.exists(thumbPath)) {
                        Files.delete(thumbPath);
                        log.info("🗑️ Deleted thumbnail file: {}", thumbPath);
                    }
                }
            } catch (IOException e) {
                log.error("❌ Failed to delete file {}: {}", urlPath, e.getMessage(), e);
                throw new RuntimeException("Failed to delete file: " + urlPath, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }


    private Metadata parseBase64Data(String base64Data) {
        if (base64Data == null || base64Data.isBlank() || !base64Data.startsWith("data:image/")) {
            throw new IllegalArgumentException("Invalid base64 image data format.");
        }

        String[] parts = base64Data.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid base64 format");
        }

        String header = parts[0];
        String data = parts[1];

        String declaredMimeType = header.substring(5, header.indexOf(";"));
        String declaredExtension = declaredMimeType.substring(declaredMimeType.indexOf("/") + 1);
        byte[] decodedData = Base64.getDecoder().decode(data);

        ImageMagicBytes.Format detected = ImageMagicBytes.detect(decodedData)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Image bytes do not match any supported image format (JPEG/PNG/GIF/WEBP)."));

        Optional<ImageMagicBytes.Format> declared =
                ImageMagicBytes.Format.fromDeclaredExtension(declaredExtension);
        if (declared.isEmpty() || declared.get() != detected) {
            log.warn("⚠️ Image mime-type mismatch: declared={} detected={} — using detected",
                    declaredMimeType, detected.mimeType());
        }

        return new Metadata(detected.extension(), decodedData, detected.mimeType());
    }


    private OptimizedImages optimizeImage(byte[] imageData, String extension) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
        BufferedImage originalImage = ImageIO.read(bais);

        if (originalImage == null) {
            throw new IllegalArgumentException("Invalid image data");
        }

        byte[] optimizedOriginal = optimizeImageQuality(originalImage, extension);

        byte[] thumbnail = null;
        if (createThumbnails()) {
            BufferedImage thumbnailImage = resizeImage(originalImage, thumbnailWidth(), thumbnailHeight());
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
                writeParam.setCompressionQuality(jpegQuality());

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

    private ImagePaths saveImageFiles(OptimizedImages images, String extension) throws IOException {
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);

        String originalFileName = String.format("original_%s_%s.%s", timestamp, uniqueId, extension);
        Path dateDir = Paths.get(basePath(), dateFolder);
        Files.createDirectories(dateDir);

        Path originalPath = dateDir.resolve(originalFileName);
        Files.write(originalPath, images.original);

        String originalRelativePath = dir() + dateFolder + "/" + originalFileName;
        String thumbnailRelativePath = null;

        if (images.thumbnail != null) {
            String thumbnailFileName = String.format("thumb_%s_%s.%s", timestamp, uniqueId, extension);
            Path thumbnailPath = dateDir.resolve(thumbnailFileName);
            Files.write(thumbnailPath, images.thumbnail);
            thumbnailRelativePath = dir() + dateFolder + "/" + thumbnailFileName;
        }

        return new ImagePaths(originalRelativePath, thumbnailRelativePath);
    }


    private void validate(Metadata metadata) {
        long sizeInMb = metadata.decodedData.length / (1024 * 1024);
        if (sizeInMb > maxSizeMb()) {
            throw new IllegalArgumentException("Banner size exceeds " + maxSizeMb() + "MB limit");
        }

        if (isValidImageExtension(metadata.extension)) {
            throw new IllegalArgumentException("Unsupported image format: " + metadata.extension);
        }
    }


    private boolean isValidImageExtension(String extension) {
        return !extension.equals("jpg") && !extension.equals("jpeg") &&
                !extension.equals("png") && !extension.equals("gif") &&
                !extension.equals("webp");
    }

    protected abstract String basePath();
    protected abstract int maxSizeMb();
    protected abstract int thumbnailWidth();
    protected abstract int thumbnailHeight();
    protected abstract boolean createThumbnails();
    protected abstract float jpegQuality();
    protected abstract String dir();


    private record Metadata(String extension, byte[] decodedData, String mimeType) {}
    private record OptimizedImages(byte[] original, byte[] thumbnail, int originalSize, int thumbnailSize) {}
    private record ImagePaths(String originalPath, String thumbnailPath) {}
    public record Result(String originalPath, String thumbnailPath, int originalSize, int thumbnailSize) {
        public String getDisplayUrl() {
            return thumbnailPath != null ? thumbnailPath : originalPath;
        }

        public String getFullUrl(String baseUrl) {
            return baseUrl + getDisplayUrl();
        }
    }
}
