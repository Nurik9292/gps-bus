package biz.ugur.busroutebackend.banner.application.usecase.admin;

import biz.ugur.busroutebackend.banner.application.dto.BannerListResponse;
import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.application.dto.SearchBannersQuery;
import biz.ugur.busroutebackend.banner.application.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.banner.domain.valueobjects.*;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для SearchBannersUseCase.
 * Проверяют корректность построения Specification на основе SearchBannersQuery.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SearchBannersUseCase Tests")
class SearchBannersUseCaseTest {

    @Mock
    private AdminBannerRepository bannerRepository;

    @Mock
    private BannerResponseMapper bannerResponseMapper;

    @Mock
    private CorrelationContextService correlationContextService;

    @Mock
    private EventBus eventBus;

    private SearchBannersUseCase useCase;

    private Banner testBanner;
    private BannerResponse testBannerResponse;

    @BeforeEach
    void setUp() {
        useCase = new SearchBannersUseCase(
            bannerRepository,
            correlationContextService,
            eventBus,
            bannerResponseMapper
        );

        LocalDateTime now = LocalDateTime.now();
        testBanner = Banner.create(
            BannerTitle.of("Тестовый баннер"),
            BannerType.MAIN,
            BannerPeriod.between(now.minusDays(1), now.plusDays(10)),
            BannerImage.of("test.jpg"),
            "target.com",
            10,
            null
        );

        testBannerResponse = new BannerResponse(
            testBanner.getId().getValue(),
            testBanner.getTitle().getValue(),
            testBanner.getType().getValue(),
            testBanner.getImageUrl().getValue(),
            testBanner.getTargetUrl(),
            testBanner.getIsActive(),
            testBanner.getDisplayOrder(),
            testBanner.getPeriod().getStartTime(),
            testBanner.getPeriod().getEndTime(),
            null
        );

        when(correlationContextService.getCurrentCorrelationId())
            .thenReturn(Mono.just(new biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId("TEST-12345678")));
        when(correlationContextService.executeWithCorrelation(any(), any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Поиск с минимальными параметрами - должен использовать базовую спецификацию")
    void testSearchWithMinimalParameters() {
        SearchBannersQuery query = SearchBannersQuery.builder()
            .page(1)
            .size(10)
            .build();

        when(bannerRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
            .thenReturn(Flux.just(testBanner));
        when(bannerRepository.countBySpecification(any(Specification.class)))
            .thenReturn(Mono.just(1L));
        when(bannerResponseMapper.toResponse(testBanner))
            .thenReturn(Mono.just(testBannerResponse));

        StepVerifier.create(useCase.execute(Mono.just(query)))
            .assertNext(response -> {
                assertNotNull(response);
                assertEquals(1, response.getBanners().size());
                assertEquals(1L, response.getActiveCount());
            })
            .verifyComplete();

        verify(bannerRepository).findBySpecification(any(Specification.class), any(Pageable.class));
        verify(bannerRepository).countBySpecification(any(Specification.class));
    }

    @Test
    @DisplayName("Поиск по типу - должен добавить спецификацию hasType")
    void testSearchByType() {
        SearchBannersQuery query = SearchBannersQuery.builder()
            .type(BannerType.MAIN)
            .page(1)
            .size(10)
            .build();

        when(bannerRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
            .thenReturn(Flux.just(testBanner));
        when(bannerRepository.countBySpecification(any(Specification.class)))
            .thenReturn(Mono.just(1L));
        when(bannerResponseMapper.toResponse(testBanner))
            .thenReturn(Mono.just(testBannerResponse));

        StepVerifier.create(useCase.execute(Mono.just(query)))
            .assertNext(response -> {
                assertNotNull(response);
                assertEquals(1, response.getBanners().size());
            })
            .verifyComplete();

        ArgumentCaptor<Specification<Banner>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(bannerRepository).findBySpecification(specCaptor.capture(), any(Pageable.class));

        Specification<Banner> capturedSpec = specCaptor.getValue();
        assertNotNull(capturedSpec);
    }

    @Test
    @DisplayName("Поиск активных баннеров - должен добавить спецификацию isActive")
    void testSearchActiveOnly() {
        SearchBannersQuery query = SearchBannersQuery.builder()
            .isActive(true)
            .page(1)
            .size(10)
            .build();

        when(bannerRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
            .thenReturn(Flux.just(testBanner));
        when(bannerRepository.countBySpecification(any(Specification.class)))
            .thenReturn(Mono.just(1L));
        when(bannerResponseMapper.toResponse(testBanner))
            .thenReturn(Mono.just(testBannerResponse));

        StepVerifier.create(useCase.execute(Mono.just(query)))
            .assertNext(response -> {
                assertNotNull(response);
                assertEquals(1, response.getBanners().size());
            })
            .verifyComplete();

        verify(bannerRepository).findBySpecification(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Поиск неактивных баннеров - должен добавить спецификацию isInactive")
    void testSearchInactiveOnly() {
        SearchBannersQuery query = SearchBannersQuery.builder()
            .isActive(false)
            .page(1)
            .size(10)
            .build();

        when(bannerRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
            .thenReturn(Flux.empty());
        when(bannerRepository.countBySpecification(any(Specification.class)))
            .thenReturn(Mono.just(0L));

        StepVerifier.create(useCase.execute(Mono.just(query)))
            .assertNext(response -> {
                assertNotNull(response);
                assertEquals(0, response.getBanners().size());
                assertEquals(0L, response.getActiveCount());
            })
            .verifyComplete();

        verify(bannerRepository).findBySpecification(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Текстовый поиск по названию - должен добавить спецификацию titleContains")
    void testSearchByTitle() {
        SearchBannersQuery query = SearchBannersQuery.builder()
            .titleSearch("тестовый")
            .page(1)
            .size(10)
            .build();

        when(bannerRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
            .thenReturn(Flux.just(testBanner));
        when(bannerRepository.countBySpecification(any(Specification.class)))
            .thenReturn(Mono.just(1L));
        when(bannerResponseMapper.toResponse(testBanner))
            .thenReturn(Mono.just(testBannerResponse));

        StepVerifier.create(useCase.execute(Mono.just(query)))
            .assertNext(response -> {
                assertNotNull(response);
                assertEquals(1, response.getBanners().size());
            })
            .verifyComplete();

        verify(bannerRepository).findBySpecification(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Поиск с активным периодом - должен добавить спецификацию isPeriodActive")
    void testSearchByPeriodActive() {
        SearchBannersQuery query = SearchBannersQuery.builder()
            .periodActive(true)
            .page(1)
            .size(10)
            .build();

        when(bannerRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
            .thenReturn(Flux.just(testBanner));
        when(bannerRepository.countBySpecification(any(Specification.class)))
            .thenReturn(Mono.just(1L));
        when(bannerResponseMapper.toResponse(testBanner))
            .thenReturn(Mono.just(testBannerResponse));

        StepVerifier.create(useCase.execute(Mono.just(query)))
            .assertNext(response -> {
                assertNotNull(response);
                assertEquals(1, response.getBanners().size());
            })
            .verifyComplete();

        verify(bannerRepository).findBySpecification(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Поиск по диапазону displayOrder - должен добавить спецификацию displayOrderBetween")
    void testSearchByDisplayOrderRange() {
        SearchBannersQuery query = SearchBannersQuery.builder()
            .minDisplayOrder(5)
            .maxDisplayOrder(15)
            .page(1)
            .size(10)
            .build();

        when(bannerRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
            .thenReturn(Flux.just(testBanner));
        when(bannerRepository.countBySpecification(any(Specification.class)))
            .thenReturn(Mono.just(1L));
        when(bannerResponseMapper.toResponse(testBanner))
            .thenReturn(Mono.just(testBannerResponse));

        StepVerifier.create(useCase.execute(Mono.just(query)))
            .assertNext(response -> {
                assertNotNull(response);
                assertEquals(1, response.getBanners().size());
            })
            .verifyComplete();

        verify(bannerRepository).findBySpecification(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Поиск истекающих баннеров - должен добавить спецификацию periodExpiresWithinDays")
    void testSearchExpiringBanners() {
        SearchBannersQuery query = SearchBannersQuery.builder()
            .expiringWithinDays(7)
            .page(1)
            .size(10)
            .build();

        when(bannerRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
            .thenReturn(Flux.just(testBanner));
        when(bannerRepository.countBySpecification(any(Specification.class)))
            .thenReturn(Mono.just(1L));
        when(bannerResponseMapper.toResponse(testBanner))
            .thenReturn(Mono.just(testBannerResponse));

        StepVerifier.create(useCase.execute(Mono.just(query)))
            .assertNext(response -> {
                assertNotNull(response);
                assertEquals(1, response.getBanners().size());
            })
            .verifyComplete();

        verify(bannerRepository).findBySpecification(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Комплексный поиск - должен комбинировать несколько спецификаций")
    void testComplexSearch() {
        SearchBannersQuery query = SearchBannersQuery.builder()
            .type(BannerType.MAIN)
            .isActive(true)
            .titleSearch("тестовый")
            .periodActive(true)
            .minDisplayOrder(5)
            .maxDisplayOrder(15)
            .page(1)
            .size(10)
            .build();

        when(bannerRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
            .thenReturn(Flux.just(testBanner));
        when(bannerRepository.countBySpecification(any(Specification.class)))
            .thenReturn(Mono.just(1L));
        when(bannerResponseMapper.toResponse(testBanner))
            .thenReturn(Mono.just(testBannerResponse));

        StepVerifier.create(useCase.execute(Mono.just(query)))
            .assertNext(response -> {
                assertNotNull(response);
                assertEquals(1, response.getBanners().size());
                assertEquals(1L, response.getActiveCount());
            })
            .verifyComplete();

        // Проверяем, что использовалась комплексная спецификация
        ArgumentCaptor<Specification<Banner>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(bannerRepository).findBySpecification(specCaptor.capture(), any(Pageable.class));

        Specification<Banner> capturedSpec = specCaptor.getValue();
        assertNotNull(capturedSpec);

        // Проверяем, что спецификация корректно работает с тестовым баннером
        assertTrue(capturedSpec.isSatisfiedBy(testBanner));
    }

    @Test
    @DisplayName("Пагинация - должна корректно передаваться в репозиторий")
    void testPagination() {
        SearchBannersQuery query = SearchBannersQuery.builder()
            .page(2)
            .size(20)
            .build();

        when(bannerRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
            .thenReturn(Flux.just(testBanner));
        when(bannerRepository.countBySpecification(any(Specification.class)))
            .thenReturn(Mono.just(1L));
        when(bannerResponseMapper.toResponse(testBanner))
            .thenReturn(Mono.just(testBannerResponse));

        StepVerifier.create(useCase.execute(Mono.just(query)))
            .assertNext(response -> assertNotNull(response))
            .verifyComplete();

        // Проверяем Pageable
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(bannerRepository).findBySpecification(any(Specification.class), pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(1, capturedPageable.getPageNumber()); // Page 2 в запросе = index 1
        assertEquals(20, capturedPageable.getPageSize());
    }

    @Test
    @DisplayName("Сортировка - должна корректно передаваться в Pageable")
    void testSorting() {
        SearchBannersQuery query = SearchBannersQuery.builder()
            .page(1)
            .size(10)
            .sortField("title")
            .sortOrder("desc")
            .build();

        when(bannerRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
            .thenReturn(Flux.just(testBanner));
        when(bannerRepository.countBySpecification(any(Specification.class)))
            .thenReturn(Mono.just(1L));
        when(bannerResponseMapper.toResponse(testBanner))
            .thenReturn(Mono.just(testBannerResponse));

        StepVerifier.create(useCase.execute(Mono.just(query)))
            .assertNext(response -> assertNotNull(response))
            .verifyComplete();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(bannerRepository).findBySpecification(any(Specification.class), pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        assertFalse(capturedPageable.getSort().isEmpty());
    }

    @Test
    @DisplayName("Валидация - должна выбросить исключение при некорректных параметрах")
    void testValidation() {
        SearchBannersQuery query = SearchBannersQuery.builder()
            .page(-1) // Некорректный page
            .size(10)
            .build();

        StepVerifier.create(useCase.execute(Mono.just(query)))
            .expectError(IllegalArgumentException.class)
            .verify();
    }

    @Test
    @DisplayName("Пустой результат - должен вернуть пустой список")
    void testEmptyResult() {
        SearchBannersQuery query = SearchBannersQuery.builder()
            .type(BannerType.POPUP)
            .page(1)
            .size(10)
            .build();

        when(bannerRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
            .thenReturn(Flux.empty());
        when(bannerRepository.countBySpecification(any(Specification.class)))
            .thenReturn(Mono.just(0L));

        StepVerifier.create(useCase.execute(Mono.just(query)))
            .assertNext(response -> {
                assertNotNull(response);
                assertEquals(0, response.getBanners().size());
                assertEquals(0L, response.getActiveCount());
            })
            .verifyComplete();
    }
}
