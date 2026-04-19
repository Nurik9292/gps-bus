package biz.ugur.busroutebackend.advertising.application.processor;

import biz.ugur.busroutebackend.advertising.domain.storage.AdPlacementStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class AdPlacementImageProcessorTest {

    @InjectMocks
    private AdPlacementImageProcessor processor;

    @Mock
    private AdPlacementStorage storage;

    @Test
    void processStoresDataUriAndReturnsSavedPath() {
        when(storage.save("data:image/png;base64,AAA")).thenReturn(Mono.just("/ads/saved.png"));

        StepVerifier.create(processor.process("data:image/png;base64,AAA"))
                .assertNext(path -> assertEquals("/ads/saved.png", path))
                .verifyComplete();

        verify(storage).save("data:image/png;base64,AAA");
    }

    @Test
    void processReturnsExistingPathUnchangedWhenNotDataUri() {
        StepVerifier.create(processor.process("/ads/existing.jpg"))
                .assertNext(path -> assertEquals("/ads/existing.jpg", path))
                .verifyComplete();

        verify(storage, never()).save(anyString());
    }

    @Test
    void processCompletesEmptyWhenUrlIsNull() {
        StepVerifier.create(processor.process(null))
                .verifyComplete();
    }

    @Test
    void processForUpdateKeepsOldWhenNewIsNull() {
        StepVerifier.create(processor.processForUpdate(null, "/ads/old.jpg"))
                .assertNext(path -> assertEquals("/ads/old.jpg", path))
                .verifyComplete();

        verify(storage, never()).delete(anyString());
    }

    @Test
    void processForUpdateKeepsOldWhenNewIsSame() {
        StepVerifier.create(processor.processForUpdate("/ads/same.jpg", "/ads/same.jpg"))
                .assertNext(path -> assertEquals("/ads/same.jpg", path))
                .verifyComplete();

        verify(storage, never()).delete(anyString());
    }

    @Test
    void processForUpdateSavesNewAndDeletesOldForDataUri() {
        when(storage.save("data:image/png;base64,BBB")).thenReturn(Mono.just("/ads/new.png"));
        when(storage.delete("/ads/old.jpg")).thenReturn(Mono.empty());

        StepVerifier.create(processor.processForUpdate("data:image/png;base64,BBB", "/ads/old.jpg"))
                .assertNext(path -> assertEquals("/ads/new.png", path))
                .verifyComplete();

        verify(storage).save("data:image/png;base64,BBB");
        verify(storage).delete("/ads/old.jpg");
    }

    @Test
    void processForUpdateDeletesOldAndReturnsPlainNewUrl() {
        when(storage.delete("/ads/old.jpg")).thenReturn(Mono.empty());

        StepVerifier.create(processor.processForUpdate("/ads/new.jpg", "/ads/old.jpg"))
                .assertNext(path -> assertEquals("/ads/new.jpg", path))
                .verifyComplete();

        verify(storage).delete("/ads/old.jpg");
        verify(storage, never()).save(anyString());
    }
}
