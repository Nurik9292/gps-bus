package biz.ugur.busroutebackend.shared.application.compressor;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import lombok.Getter;

@Getter
public class DataCompressionException extends AbstractDomainException {

    private final String operation;
    private final String dataType;

    public DataCompressionException(String message) {
        super("SHARED.COMPRESSION_ERROR", message, Severity.ERROR);
        this.operation = null;
        this.dataType = null;
    }

    public DataCompressionException(String message, Throwable cause) {
        super("SHARED.COMPRESSION_ERROR", message, Severity.ERROR);
        this.operation = null;
        this.dataType = null;
        initCause(cause);
    }

    public DataCompressionException(String operation, String dataType, String message) {
        super("SHARED.COMPRESSION_ERROR",
              String.format("Compression %s failed for %s: %s", operation, dataType, message),
              Severity.ERROR);
        this.operation = operation;
        this.dataType = dataType;
    }

    public static DataCompressionException compressionFailed(String dataType, Throwable cause) {
        DataCompressionException ex = new DataCompressionException("compress", dataType, cause.getMessage());
        ex.initCause(cause);
        return ex;
    }

    public static DataCompressionException decompressionFailed(String dataType, Throwable cause) {
        DataCompressionException ex = new DataCompressionException("decompress", dataType, cause.getMessage());
        ex.initCause(cause);
        return ex;
    }
}
