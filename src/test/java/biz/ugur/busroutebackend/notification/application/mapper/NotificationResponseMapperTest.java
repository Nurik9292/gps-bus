package biz.ugur.busroutebackend.notification.application.mapper;

import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationTitle;
import biz.ugur.busroutebackend.shared.application.compressor.DataCompressor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class NotificationResponseMapperTest {

    @InjectMocks
    private NotificationResponseMapper mapper;

    @Mock
    private DataCompressor dataCompressor;

    @Test
    void toResponseDecompressesContent() {
        Notification notification = Notification.create(
                NotificationTitle.of("Hello"), 5, "COMPRESSED", true);

        when(dataCompressor.decodeAndDecompress("COMPRESSED"))
                .thenReturn(Mono.just("Hello world"));

        StepVerifier.create(mapper.toResponse(notification))
                .assertNext(response -> {
                    assertEquals(notification.getId().getValue(), response.id());
                    assertEquals("Hello", response.title());
                    assertEquals("Hello world", response.content());
                    assertEquals(5, response.displayOrder());
                })
                .verifyComplete();
    }

    @Test
    void toResponseUsesEmptyStringWhenContentIsBlank() {
        Notification notification = Notification.create(
                NotificationTitle.of("Empty"), 0, "", true);

        when(dataCompressor.decodeAndDecompress(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(mapper.toResponse(notification))
                .assertNext(response -> assertEquals("", response.content()))
                .verifyComplete();
    }
}
