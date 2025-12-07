package biz.ugur.busroutebackend.banner.domain.exceptions;

import lombok.Getter;

@Getter
public class BannerImageProcessingException extends BannerDomainException {

    private final String imagePath;
    private final String processingOperation;

    public BannerImageProcessingException(String message) {
        super("IMAGE_PROCESSING_ERROR", message, Severity.ERROR);
        this.imagePath = null;
        this.processingOperation = null;
    }

    public BannerImageProcessingException(String message, Throwable cause) {
        super("IMAGE_PROCESSING_ERROR", message, Severity.ERROR);
        this.imagePath = null;
        this.processingOperation = null;
        initCause(cause);
    }

    public BannerImageProcessingException(String imagePath, String processingOperation, String message) {
        super("IMAGE_PROCESSING_ERROR",
              String.format("Image processing failed for '%s' during %s: %s", imagePath, processingOperation, message),
              Severity.ERROR);
        this.imagePath = imagePath;
        this.processingOperation = processingOperation;
    }

    public static BannerImageProcessingException uploadFailed(String imagePath, String reason) {
        return new BannerImageProcessingException(imagePath, "upload", reason);
    }

    public static BannerImageProcessingException resizeFailed(String imagePath, String reason) {
        return new BannerImageProcessingException(imagePath, "resize", reason);
    }

    public static BannerImageProcessingException deleteFailed(String imagePath, String reason) {
        return new BannerImageProcessingException(imagePath, "delete", reason);
    }
}
