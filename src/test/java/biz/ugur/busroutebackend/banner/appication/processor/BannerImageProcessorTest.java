package biz.ugur.busroutebackend.banner.appication.processor;

import biz.ugur.busroutebackend.banner.domain.storage.BannerStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BannerImageProcessorTest {

    private final static String RETURN_PATH_IMAGE = "banners/10/09/image.jpg";
    private final static String DATA_IMAGE = "data:image/tettestsetststestsdfdsfd";
    private final static String NEW_DATA_IMAGE = "data:image/tettestsetststestsdfdsfd";
    private final static String NEW_RETURN_PATH_IMAGE = "banners/10/09/new.jpg";
    private final static String OLD_PATH_IMAGE = "banners/10/09/old.jpg";
    private final static String UPDATE_OLD_IMAGE_PATH = "banners/10/09/updateOldImage.jpg";

    @InjectMocks
    private BannerImageProcessor processor;

    @Mock
    private BannerStorage storage;

    @Test
    void processSuccessFully() {
        when(storage.save(any(String.class))).thenReturn(Mono.just(RETURN_PATH_IMAGE));
       StepVerifier.create(processor.process(DATA_IMAGE))
               .expectNext(RETURN_PATH_IMAGE).verifyComplete();

       verify(storage, times(1)).save(DATA_IMAGE);
    }

    @Test
    void processSuccessFullyReturnOriginalUrlWhenNotDataUrl() {
        StepVerifier.create(processor.process(RETURN_PATH_IMAGE))
                .expectNext(RETURN_PATH_IMAGE).verifyComplete();

        verify(storage, never()).save(any(String.class));
    }

    @Test
    void processSuccessWhenImageUrlIsNull() {
        StepVerifier.create(processor.process(null))
                .verifyComplete();
        verify(storage, never()).save(any());
    }

    @Test
    void processForUpdateSuccessfully() {
        when(storage.save(NEW_DATA_IMAGE)).thenReturn(Mono.just(NEW_RETURN_PATH_IMAGE));
        when(storage.delete(OLD_PATH_IMAGE)).thenReturn(Mono.empty());


        StepVerifier.create(processor.processForUpdate(NEW_DATA_IMAGE, OLD_PATH_IMAGE))
                .expectNext(NEW_RETURN_PATH_IMAGE).verifyComplete();

        verify(storage, times(1)).save(NEW_DATA_IMAGE);
        verify(storage, times(1)).delete(OLD_PATH_IMAGE);
    }


    @Test
    void processUpdateSuccessFullyReturnOriginalUrlWhenNotDataUrl() {
        when(storage.delete(any(String.class))).thenReturn(Mono.empty());

        StepVerifier.create(processor.processForUpdate(UPDATE_OLD_IMAGE_PATH, OLD_PATH_IMAGE))
                .expectNext(UPDATE_OLD_IMAGE_PATH).verifyComplete();

        verify(storage, never()).save(any(String.class));
        verify(storage, times(1)).delete(any(String.class));
    }

    @Test
    void processUpdateSuccessWhenImageUrlIsNull() {
        StepVerifier.create(processor.processForUpdate(null,  OLD_PATH_IMAGE))
                .expectNext(OLD_PATH_IMAGE)
                .verifyComplete();
        verify(storage, never()).save(any());
    }

    @Test
    void processForUpdateSuccessfullyWhenNewImageUrlEqualsOldImageUrl() {
        StepVerifier.create(processor.processForUpdate(OLD_PATH_IMAGE,  OLD_PATH_IMAGE))
                .expectNext(OLD_PATH_IMAGE)
                .verifyComplete();
        verify(storage, never()).save(any());
    }
}