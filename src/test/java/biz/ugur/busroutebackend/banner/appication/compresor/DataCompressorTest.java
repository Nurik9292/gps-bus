package biz.ugur.busroutebackend.banner.appication.compresor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DataCompressorTest {

    private final static String TEXT = "Text Text Text Text Text Text Text Text Text Text Text Text Text Text Text Text " +
            "Text Text Text Text Text Text Text Text Text Text Text Text Text Text Text Text Text Text Text Text ";

    @InjectMocks
    private DataCompressor dataCompressor;

    @Test
    void compressAndEncodeSuccessfully() {
        StepVerifier.create(dataCompressor.compressAndEncode(TEXT))
                .assertNext(compressText -> {
                    assertNotNull(compressText);
                    assertNotEquals(TEXT, compressText);
                })
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Disabled("MockedConstruction не работает корректно с reactive подходом")
    void compressAndEncodeFailure() {
      try(MockedConstruction<GZIPOutputStream> mocked = mockConstruction(GZIPOutputStream.class,
              (mock, context) -> {
                doThrow(new IOException("Test IOException")).when(mock).write(any(byte[].class));
              })) {
          StepVerifier.create(dataCompressor.compressAndEncode(TEXT))
                  .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                          throwable.getCause() instanceof IOException &&
                          throwable.getMessage().contains("Failed to compress data"))
                  .verify();
      }
    }

    @Test
    void compressAndEncodeFailWhenValueIsNull() {
        StepVerifier.create(dataCompressor.compressAndEncode(null))
                .expectComplete()
                .verify();
    }

    @Test
    void compressAndEncodeFailWhenValueIsEmpty() {
        StepVerifier.create(dataCompressor.compressAndEncode(""))
                .expectComplete()
                .verify();
    }

    @Test
    void decodeAndDecompressSuccessfully() {
        dataCompressor.compressAndEncode(TEXT)
                .flatMap(dataCompressor::decodeAndDecompress)
                .as(StepVerifier::create)
                .assertNext(decompressText -> {
                    assertNotNull(decompressText);
                    assertEquals(TEXT, decompressText);
                })
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Disabled("MockedConstruction не работает корректно с reactive подходом")
    void decodeAndDecompressFailure() {
        dataCompressor.compressAndEncode(TEXT)
                .flatMap(compressText -> {
                    try(MockedConstruction<GZIPInputStream> mocked = mockConstruction(GZIPInputStream.class,
                            (mock, context) -> {
                                doThrow(new IOException("Test IOException")).when(mock).read(any(byte[].class));
                            })) {
                        return dataCompressor.decodeAndDecompress(compressText);
                    }
                })
                .as(StepVerifier::create)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getCause() instanceof IOException)
                .verify();
    }

    @Test
    void decodeAndDecompressFailWhenValueIsNull() {
        StepVerifier.create(dataCompressor.decodeAndDecompress(null))
                .expectComplete()
                .verify();
    }

    @Test
    void decodeAndDecompressFailWhenValueIsEmpty() {
        StepVerifier.create(dataCompressor.decodeAndDecompress(""))
                .expectComplete()
                .verify();
    }
}