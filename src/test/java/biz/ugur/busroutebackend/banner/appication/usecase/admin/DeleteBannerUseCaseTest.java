package biz.ugur.busroutebackend.banner.appication.usecase.admin;

import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.banner.domain.storage.BannerStorage;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerImage;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
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
class DeleteBannerUseCaseTest {

    private final static String BANNER_ID = "bannerId";
    private final static String BANNER_IMAGE_URL = "imageUrl";

    @InjectMocks
    private DeleteBannerUseCase deleteBannerUseCase;

    @Mock
    private AdminBannerRepository  adminBannerRepository;

    @Mock
    private BannerStorage bannerStorage;

    @Mock
    private CorrelationContextService correlationContextService;

    @Test
    void  shouldDeleteBannerSuccessfully() {
        Banner existingBanner = mock(Banner.class);
        when(existingBanner.getImageUrl()).thenReturn(BannerImage.of(BANNER_IMAGE_URL));

        when(correlationContextService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(adminBannerRepository.findById(BannerId.of(BANNER_ID))).thenReturn(Mono.just(existingBanner));
        when(bannerStorage.delete(BANNER_IMAGE_URL)).thenReturn(Mono.empty());
        when(adminBannerRepository.deleteById(BannerId.of(BANNER_ID))).thenReturn(Mono.empty());

        Mono<Void> result = deleteBannerUseCase.process(Mono.just(BANNER_ID));

        StepVerifier.create(result).verifyComplete();

        verify(correlationContextService, times(1)).getCurrentCorrelationId();
        verify(adminBannerRepository, times(1)).findById(BannerId.of(BANNER_ID));
        verify(bannerStorage, times(1)).delete(BANNER_IMAGE_URL);
        verify(adminBannerRepository, times(1)).deleteById(any(BannerId.class));
    }

    @Test
    void deleteFailsThrowsError() {
        Banner existingBanner = mock(Banner.class);
        BannerImage existingBannerImage = mock(BannerImage.class);
        when(existingBanner.getImageUrl()).thenReturn(existingBannerImage);
        when(existingBannerImage.getValue()).thenReturn(BANNER_IMAGE_URL);


        when(correlationContextService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(adminBannerRepository.findById(BannerId.of(BANNER_ID))).thenReturn(Mono.just(existingBanner));
        when(adminBannerRepository.deleteById(any())).thenReturn(Mono.empty());
        when(bannerStorage.delete(anyString()))
                .thenReturn(Mono.error(new RuntimeException("delete error")));



        Mono<Void> result = deleteBannerUseCase.process(Mono.just(BANNER_ID));

        assertNotNull(result, "Mono result must not be null!");
        StepVerifier.create(result)
                .expectErrorSatisfies(e -> {
                            assertNotNull(e);
                            assertInstanceOf(RuntimeException.class, e);
                            assertEquals("delete error", e.getMessage());
                        })
                .verify();

        verify(bannerStorage, times(1)).delete(BANNER_IMAGE_URL);
        verify(adminBannerRepository, times(1)).deleteById(BannerId.of(BANNER_ID));

    }

    @Test
    void getBoundContext() {
        String admin = deleteBannerUseCase.getBoundContext();
        assertNotNull(admin);
        assertEquals("admin", admin);
    }
}