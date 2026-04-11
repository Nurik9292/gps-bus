package biz.ugur.busroutebackend.banner.domain.specification;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.*;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("BannerSpecifications Tests")
class BannerSpecificationsTest {

    private Banner activeBanner;
    private Banner inactiveBanner;
    private Banner expiredBanner;
    private Banner futureBanner;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        activeBanner = Banner.create(
            BannerTitle.of("Активный баннер"),
            BannerType.MAIN,
            BannerPeriod.between(now.minusDays(1), now.plusDays(10)),
            BannerImage.of("image1.jpg"),
            "target1.com",
            10,
            null,
            null,
            true
        );

        inactiveBanner = Banner.create(
            BannerTitle.of("Отключенный баннер"),
            BannerType.POPUP,
            BannerPeriod.between(now.minusDays(1), now.plusDays(10)),
            BannerImage.of("image2.jpg"),
            "target2.com",
            20,
            null,
            30,
            true
        ).deactivate();

        expiredBanner = Banner.create(
            BannerTitle.of("Истекший баннер"),
            BannerType.PLACES,
            BannerPeriod.between(now.minusDays(20), now.minusDays(1)),
            BannerImage.of("image3.jpg"),
            "target3.com",
            30,
            null,
            null,
            true
        );

        futureBanner = Banner.create(
            BannerTitle.of("Будущий баннер"),
            BannerType.MAIN,
            BannerPeriod.between(now.plusDays(10), now.plusDays(20)),
            BannerImage.of("image4.jpg"),
            "target4.com",
            5,
            null,
            null,
            true
        );
    }

    @Test
    @DisplayName("isActive() - должна находить только активные баннеры")
    void testIsActive() {
        Specification<Banner> spec = BannerSpecifications.isActive();

        assertTrue(spec.isSatisfiedBy(activeBanner), "Активный баннер должен удовлетворять спецификации");
        assertFalse(spec.isSatisfiedBy(inactiveBanner), "Неактивный баннер не должен удовлетворять спецификации");

        SqlCriteria criteria = spec.toSqlCriteria();
        assertEquals("is_active = :isActive", criteria.getWhereClause());
        assertEquals(true, criteria.getParameters().get("isActive"));
    }

    @Test
    @DisplayName("isInactive() - должна находить только неактивные баннеры")
    void testIsInactive() {
        Specification<Banner> spec = BannerSpecifications.isInactive();

        assertFalse(spec.isSatisfiedBy(activeBanner), "Активный баннер не должен удовлетворять спецификации");
        assertTrue(spec.isSatisfiedBy(inactiveBanner), "Неактивный баннер должен удовлетворять спецификации");

        SqlCriteria criteria = spec.toSqlCriteria();
        assertEquals("is_active = :isActive", criteria.getWhereClause());
        assertEquals(false, criteria.getParameters().get("isActive"));
    }

    @Test
    @DisplayName("hasType() - должна фильтровать по типу баннера")
    void testHasType() {
        Specification<Banner> mainSpec = BannerSpecifications.hasType(BannerType.MAIN);
        Specification<Banner> popupSpec = BannerSpecifications.hasType(BannerType.POPUP);

        assertTrue(mainSpec.isSatisfiedBy(activeBanner), "Баннер типа MAIN должен удовлетворять спецификации");
        assertFalse(mainSpec.isSatisfiedBy(inactiveBanner), "Баннер типа POPUP не должен удовлетворять спецификации MAIN");

        assertTrue(popupSpec.isSatisfiedBy(inactiveBanner), "Баннер типа POPUP должен удовлетворять спецификации");

        SqlCriteria criteria = mainSpec.toSqlCriteria();
        assertEquals("type = :type", criteria.getWhereClause());
        assertEquals("main", criteria.getParameters().get("type"));
    }

    @Test
    @DisplayName("isPeriodActive() - должна находить баннеры с активным периодом")
    void testIsPeriodActive() {
        LocalDateTime now = LocalDateTime.now();
        Specification<Banner> spec = BannerSpecifications.isPeriodActive(now);

        assertTrue(spec.isSatisfiedBy(activeBanner), "Баннер с активным периодом должен удовлетворять спецификации");
        assertFalse(spec.isSatisfiedBy(expiredBanner), "Баннер с истекшим периодом не должен удовлетворять спецификации");
        assertFalse(spec.isSatisfiedBy(futureBanner), "Баннер с будущим периодом не должен удовлетворять спецификации");

        SqlCriteria criteria = spec.toSqlCriteria();
        assertEquals("start_date <= :periodNow AND end_date >= :periodNow", criteria.getWhereClause());
        assertNotNull(criteria.getParameters().get("periodNow"));
    }

    @Test
    @DisplayName("periodExpiresWithinDays() - должна находить истекающие баннеры")
    void testPeriodExpiresWithinDays() {
        LocalDateTime now = LocalDateTime.now();
        Banner soonExpiringBanner = Banner.create(
            BannerTitle.of("Скоро истекающий"),
            BannerType.MAIN,
            BannerPeriod.between(now.minusDays(1), now.plusDays(3)),
            BannerImage.of("image5.jpg"),
            "target5.com",
            15,
            null,
            null,
            true
        );

        Specification<Banner> spec = BannerSpecifications.periodExpiresWithinDays(7);

        assertTrue(spec.isSatisfiedBy(soonExpiringBanner), "Баннер истекающий через 3 дня должен найтись в диапазоне 7 дней");
        assertFalse(spec.isSatisfiedBy(activeBanner), "Баннер истекающий через 10 дней не должен найтись в диапазоне 7 дней");
        assertFalse(spec.isSatisfiedBy(expiredBanner), "Уже истекший баннер не должен удовлетворять спецификации");

        SqlCriteria criteria = spec.toSqlCriteria();
        assertEquals("end_date < :expiryDeadline AND end_date > :expiryNow", criteria.getWhereClause());
        assertNotNull(criteria.getParameters().get("expiryDeadline"));
        assertNotNull(criteria.getParameters().get("expiryNow"));
    }

    @Test
    @DisplayName("titleContains() - должна искать по подстроке в названии")
    void testTitleContains() {
        Specification<Banner> spec = BannerSpecifications.titleContains("активный");

        assertTrue(spec.isSatisfiedBy(activeBanner), "Баннер с 'Активный баннер' должен найтись по запросу 'активный'");
        assertFalse(spec.isSatisfiedBy(inactiveBanner), "Баннер с 'Неактивный баннер' не должен найтись по запросу 'активный'");

        Specification<Banner> upperCaseSpec = BannerSpecifications.titleContains("АКТИВНЫЙ");
        assertTrue(upperCaseSpec.isSatisfiedBy(activeBanner), "Поиск должен быть case-insensitive");

        SqlCriteria criteria = spec.toSqlCriteria();
        assertEquals("LOWER(title) LIKE :titleSearch", criteria.getWhereClause());
        assertEquals("%активный%", criteria.getParameters().get("titleSearch"));
    }

    @Test
    @DisplayName("displayOrderBetween() - должна фильтровать по диапазону displayOrder")
    void testDisplayOrderBetween() {
        Specification<Banner> spec = BannerSpecifications.displayOrderBetween(5, 15);

        assertTrue(spec.isSatisfiedBy(activeBanner), "Баннер с displayOrder=10 должен попасть в диапазон 5-15");
        assertFalse(spec.isSatisfiedBy(inactiveBanner), "Баннер с displayOrder=20 не должен попасть в диапазон 5-15");
        assertTrue(spec.isSatisfiedBy(futureBanner), "Баннер с displayOrder=5 должен попасть в диапазон 5-15");

        SqlCriteria criteria = spec.toSqlCriteria();
        assertEquals("display_order BETWEEN :minOrder AND :maxOrder", criteria.getWhereClause());
        assertEquals(5, criteria.getParameters().get("minOrder"));
        assertEquals(15, criteria.getParameters().get("maxOrder"));
    }

    @Test
    @DisplayName("hasId() - должна находить баннер по ID")
    void testHasId() {
        String bannerId = activeBanner.getId().getValue();
        Specification<Banner> spec = BannerSpecifications.hasId(bannerId);

        assertTrue(spec.isSatisfiedBy(activeBanner), "Баннер должен найтись по своему ID");
        assertFalse(spec.isSatisfiedBy(inactiveBanner), "Другой баннер не должен найтись");

        SqlCriteria criteria = spec.toSqlCriteria();
        assertEquals("id = :bannerId", criteria.getWhereClause());
        assertNotNull(criteria.getParameters().get("bannerId"));
    }

    @Test
    @DisplayName("Комбинирование спецификаций через .and()")
    void testAndCombination() {
        Specification<Banner> spec = BannerSpecifications.isActive()
            .and(BannerSpecifications.hasType(BannerType.MAIN));

        assertTrue(spec.isSatisfiedBy(activeBanner), "Активный баннер типа MAIN должен удовлетворять");
        assertFalse(spec.isSatisfiedBy(inactiveBanner), "Неактивный баннер не должен удовлетворять");
        assertFalse(spec.isSatisfiedBy(expiredBanner), "Баннер типа PLACES не должен удовлетворять");

        SqlCriteria criteria = spec.toSqlCriteria();
        assertTrue(criteria.getWhereClause().contains("is_active"));
        assertTrue(criteria.getWhereClause().contains("type"));
        assertTrue(criteria.getWhereClause().contains("AND"));
    }

    @Test
    @DisplayName("Комбинирование спецификаций через .or()")
    void testOrCombination() {
        Specification<Banner> spec = BannerSpecifications.hasType(BannerType.MAIN)
            .or(BannerSpecifications.hasType(BannerType.POPUP));

        assertTrue(spec.isSatisfiedBy(activeBanner), "Баннер типа MAIN должен удовлетворять");
        assertTrue(spec.isSatisfiedBy(inactiveBanner), "Баннер типа POPUP должен удовлетворять");
        assertFalse(spec.isSatisfiedBy(expiredBanner), "Баннер типа PLACES не должен удовлетворять");

        SqlCriteria criteria = spec.toSqlCriteria();
        assertTrue(criteria.getWhereClause().contains("OR"));
    }

    @Test
    @DisplayName("Использование .not() для инверсии спецификации")
    void testNotSpecification() {
        Specification<Banner> spec = BannerSpecifications.isActive().not();

        assertFalse(spec.isSatisfiedBy(activeBanner), "Активный баннер не должен удовлетворять .not()");
        assertTrue(spec.isSatisfiedBy(inactiveBanner), "Неактивный баннер должен удовлетворять .not()");

        SqlCriteria criteria = spec.toSqlCriteria();
        assertTrue(criteria.getWhereClause().contains("NOT"));
    }

    @Test
    @DisplayName("isReadyForDisplay() - комплексная спецификация для клиента")
    void testIsReadyForDisplay() {
        Specification<Banner> spec = BannerSpecifications.isReadyForDisplay();

        assertTrue(spec.isSatisfiedBy(activeBanner), "Активный баннер с активным периодом должен быть готов к показу");
        assertFalse(spec.isSatisfiedBy(inactiveBanner), "Неактивный баннер не должен быть готов к показу");
        assertFalse(spec.isSatisfiedBy(expiredBanner), "Баннер с истекшим периодом не должен быть готов к показу");
        assertFalse(spec.isSatisfiedBy(futureBanner), "Баннер с будущим периодом не должен быть готов к показу");

        SqlCriteria criteria = spec.toSqlCriteria();
        assertTrue(criteria.getWhereClause().contains("is_active"));
        assertTrue(criteria.getWhereClause().contains("start_date"));
        assertTrue(criteria.getWhereClause().contains("end_date"));
    }

    @Test
    @DisplayName("requiresAdminAttention() - находит баннеры требующие внимания")
    void testRequiresAdminAttention() {
        LocalDateTime now = LocalDateTime.now();

        Banner attentionNeededBanner = Banner.create(
            BannerTitle.of("Требует внимания"),
            BannerType.MAIN,
            BannerPeriod.between(now.minusDays(1), now.plusDays(5)),
            BannerImage.of("image6.jpg"),
            "target6.com",
            25,
            null,
            null,
            true
        );

        Specification<Banner> spec = BannerSpecifications.requiresAdminAttention();

        assertTrue(spec.isSatisfiedBy(attentionNeededBanner), "Активный баннер истекающий через 5 дней должен требовать внимания");
        assertFalse(spec.isSatisfiedBy(inactiveBanner), "Неактивный баннер не должен требовать внимания");
        assertFalse(spec.isSatisfiedBy(activeBanner), "Баннер истекающий через 10 дней не должен требовать внимания");

        SqlCriteria criteria = spec.toSqlCriteria();
        assertTrue(criteria.getWhereClause().contains("is_active"));
        assertTrue(criteria.getWhereClause().contains("end_date"));
    }

    @Test
    @DisplayName("Сложная комбинация: активный MAIN-баннер с активным периодом и приоритетом 5-15")
    void testComplexCombination() {
        Specification<Banner> spec = BannerSpecifications.isActive()
            .and(BannerSpecifications.hasType(BannerType.MAIN))
            .and(BannerSpecifications.isPeriodActive(LocalDateTime.now()))
            .and(BannerSpecifications.displayOrderBetween(5, 15));

        assertTrue(spec.isSatisfiedBy(activeBanner), "Активный MAIN баннер с displayOrder=10 и активным периодом должен удовлетворять");
        assertFalse(spec.isSatisfiedBy(inactiveBanner), "Неактивный баннер не должен удовлетворять");
        assertFalse(spec.isSatisfiedBy(futureBanner), "Баннер с будущим периодом не должен удовлетворять");

        SqlCriteria criteria = spec.toSqlCriteria();
        String whereClause = criteria.getWhereClause();
        assertTrue(whereClause.contains("is_active"), "WHERE должен содержать is_active");
        assertTrue(whereClause.contains("type"), "WHERE должен содержать type");
        assertTrue(whereClause.contains("start_date"), "WHERE должен содержать start_date");
        assertTrue(whereClause.contains("display_order"), "WHERE должен содержать display_order");
    }
}
