package biz.ugur.busroutebackend.shared.application.compressor;


public class DataCompressionException extends RuntimeException {

    public DataCompressionException(String message) {
        super(message);
    }

    public DataCompressionException(String message, Throwable cause) {
        super(message, cause);
    }
}
