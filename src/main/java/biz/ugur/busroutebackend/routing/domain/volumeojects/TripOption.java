package biz.ugur.busroutebackend.routing.domain.volumeojects;

import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * TripOption - конкретный вариант поездки
 *
 * Представляет один из способов добраться от точки A до точки B:
 * - Последовательность сегментов (ходьба, автобус, пересадка)
 * - Расчет времени и стоимости
 * - Оценка качества варианта
 *
 * Business Rules:
 * - Начинается и заканчивается пешими сегментами (если нужно)
 * - Автобусные сегменты только между остановками
 * - Пересадки только на остановках где пересекаются маршруты
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class TripOption extends ValueObject {

    private final String optionId;
    private final TripType tripType;
    private final List<RouteSegment> routeSegments;
    private final int totalTravelMinutes;
    private final int totalWalkingMinutes;
    private final int totalBusRideMinutes;
    private final int totalWaitingMinutes;
    private final int transfersCount;
    private final double estimatedCostManat;
    private final LocalDateTime estimatedDeparture;
    private final LocalDateTime estimatedArrival;
    private final double comfortScore; // 0-100, где 100 = максимальный комфорт

    public TripOption(TripType tripType, List<RouteSegment> routeSegments) {
        this.optionId = UUID.randomUUID().toString();
        this.tripType = validateTripType(tripType);
        this.routeSegments = validateAndCopySegments(routeSegments);

        // Рассчитываем метрики
        this.totalWalkingMinutes = calculateTotalWalkingTime();
        this.totalBusRideMinutes = calculateTotalBusRideTime();
        this.totalWaitingMinutes = calculateTotalWaitingTime();
        this.totalTravelMinutes = totalWalkingMinutes + totalBusRideMinutes + totalWaitingMinutes;
        this.transfersCount = calculateTransfersCount();
        this.estimatedCostManat = calculateEstimatedCost();
        this.estimatedDeparture = calculateDeparture();
        this.estimatedArrival = calculateArrival();
        this.comfortScore = calculateComfortScore();

        // Валидация целостности
        validateTripLogic();
    }

    /**
     * Сравнить с другим вариантом по скорости
     */
    public boolean isFasterThan(TripOption other) {
        return this.totalTravelMinutes < other.totalTravelMinutes;
    }

    /**
     * Сравнить с другим вариантом по количеству пересадок
     */
    public boolean hasFewerTransfersThan(TripOption other) {
        return this.transfersCount < other.transfersCount;
    }

    /**
     * Сравнить с другим вариантом по стоимости
     */
    public boolean isCheaperThan(TripOption other) {
        return this.estimatedCostManat < other.estimatedCostManat;
    }

    /**
     * Сравнить с другим вариантом по комфорту
     */
    public boolean isMoreComfortableThan(TripOption other) {
        return this.comfortScore > other.comfortScore;
    }

    /**
     * Получить краткое описание варианта
     */
    public String getSummary() {
        if (tripType == TripType.DIRECT) {
            String routeNumbers = getUsedRouteNumbers();
            return String.format("Прямой маршрут %s - %d мин", routeNumbers, totalTravelMinutes);
        } else {
            return String.format("%d пересадк%s - %d мин",
                    transfersCount,
                    getTransferSuffix(transfersCount),
                    totalTravelMinutes);
        }
    }

    /**
     * Получить детальное описание варианта
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Поездка займет %d мин", totalTravelMinutes));

        if (totalWalkingMinutes > 0) {
            sb.append(String.format(" (пешком %d мин)", totalWalkingMinutes));
        }

        if (transfersCount > 0) {
            sb.append(String.format(", %d пересадк%s", transfersCount, getTransferSuffix(transfersCount)));
        }

        sb.append(String.format(", стоимость %.1f маната", estimatedCostManat));

        return sb.toString();
    }

    /**
     * Получить список используемых маршрутов
     */
    public List<String> getUsedRoutes() {
        return routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.BUS_RIDE)
                .map(RouteSegment::getRouteNumber)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Получить строку с номерами маршрутов
     */
    public String getUsedRouteNumbers() {
        List<String> routes = getUsedRoutes();
        if (routes.isEmpty()) return "пешком";
        return String.join(", ", routes);
    }

    /**
     * Проверить валидность варианта для заданной поездки
     */
    public boolean isValidForTrip(Location origin, Location destination) {
        if (routeSegments.isEmpty()) return false;

        RouteSegment firstSegment = routeSegments.getFirst();
        RouteSegment lastSegment = routeSegments.getLast();

        // Проверяем что первый сегмент начинается рядом с точкой отправления
        double startDistance = firstSegment.getFromLocation().distanceTo(origin);
        if (startDistance > 1000) { // Максимум 1км от точки отправления
            return false;
        }

        // Проверяем что последний сегмент заканчивается рядом с пунктом назначения
        double endDistance = lastSegment.getToLocation().distanceTo(destination);
        if (endDistance > 1000) { // Максимум 1км до пункта назначения
            return false;
        }

        return true;
    }

    /**
     * Получить оценку качества варианта (0-100)
     */
    public double getQualityScore() {
        double speedScore = Math.max(0, 100 - totalTravelMinutes); // Меньше времени = лучше
        double transferScore = Math.max(0, 100 - transfersCount * 25); // Меньше пересадок = лучше
        double walkingScore = Math.max(0, 100 - totalWalkingMinutes * 3); // Меньше ходьбы = лучше

        // Взвешенная оценка
        return (speedScore * 0.4 + transferScore * 0.3 + walkingScore * 0.2 + comfortScore * 0.1);
    }

    /**
     * Проверить подходит ли для людей с ограниченными возможностями
     */
    public boolean isAccessible() {
        // Проверяем что пешие сегменты не слишком длинные
        boolean walkingOk = routeSegments.stream()
                .filter(s -> s.getType() == SegmentType.WALKING)
                .allMatch(s -> s.getDurationMinutes() <= 5); // Максимум 5 минут ходьбы

        // Проверяем что не слишком много пересадок
        boolean transfersOk = transfersCount <= 1;

        return walkingOk && transfersOk;
    }

    /**
     * Получить прогноз надежности варианта (вероятность успешной поездки)
     */
    public double getReliabilityScore() {
        double baseReliability = 0.95; // Базовая надежность 95%

        // Каждая пересадка снижает надежность на 5%
        double transferPenalty = transfersCount * 0.05;

        // Длинные пешие переходы снижают надежность
        double walkingPenalty = Math.max(0, (totalWalkingMinutes - 10) * 0.01);

        return Math.max(0.5, baseReliability - transferPenalty - walkingPenalty);
    }

    // Приватные методы расчета

    private TripType validateTripType(TripType tripType) {
        if (tripType == null) {
            throw new IllegalArgumentException("Trip type cannot be null");
        }
        return tripType;
    }

    private List<RouteSegment> validateAndCopySegments(List<RouteSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("Route segments cannot be null or empty");
        }

        // Проверяем связность сегментов
        for (int i = 1; i < segments.size(); i++) {
            Location prevEnd = segments.get(i - 1).getToLocation();
            Location currentStart = segments.get(i).getFromLocation();

            if (prevEnd.distanceTo(currentStart) > 100) { // Максимум 100м между сегментами
                throw new IllegalArgumentException("Route segments are not connected");
            }
        }

        return new ArrayList<>(segments);
    }

    private void validateTripLogic() {
        // Проверяем что поездка начинается с ходьбы или автобуса (не с пересадки)
        if (!routeSegments.isEmpty()) {
            SegmentType firstType = routeSegments.getFirst().getType();
            if (firstType == SegmentType.TRANSFER) {
                throw new IllegalArgumentException("Trip cannot start with a transfer");
            }
        }

        // Проверяем что после каждой пересадки идет автобус
        for (int i = 0; i < routeSegments.size() - 1; i++) {
            if (routeSegments.get(i).getType() == SegmentType.TRANSFER) {
                SegmentType nextType = routeSegments.get(i + 1).getType();
                if (nextType != SegmentType.BUS_RIDE) {
                    throw new IllegalArgumentException("Transfer must be followed by bus ride");
                }
            }
        }
    }

    private int calculateTotalWalkingTime() {
        return routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.WALKING)
                .mapToInt(RouteSegment::getDurationMinutes)
                .sum();
    }

    private int calculateTotalBusRideTime() {
        return routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.BUS_RIDE)
                .mapToInt(RouteSegment::getDurationMinutes)
                .sum();
    }

    private int calculateTotalWaitingTime() {
        return routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.TRANSFER)
                .mapToInt(RouteSegment::getDurationMinutes)
                .sum();
    }

    private int calculateTransfersCount() {
        return (int) routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.TRANSFER)
                .count();
    }

    private double calculateEstimatedCost() {
        long busRides = routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.BUS_RIDE)
                .map(RouteSegment::getRouteNumber)
                .distinct()
                .count();

        return busRides * 1.0; // 1 манат за каждый уникальный маршрут
    }

    private LocalDateTime calculateDeparture() {
        return LocalDateTime.now().plusMinutes(5); // Предполагаем 5 минут на подготовку
    }

    private LocalDateTime calculateArrival() {
        return estimatedDeparture.plusMinutes(totalTravelMinutes);
    }

    private double calculateComfortScore() {
        double baseScore = 100.0;

        // Штрафы за дискомфорт
        baseScore -= transfersCount * 15; // -15 за каждую пересадку
        baseScore -= Math.max(0, totalWalkingMinutes - 5) * 2; // -2 за каждую минуту ходьбы свыше 5
        baseScore -= Math.max(0, totalTravelMinutes - 30) * 0.5; // -0.5 за каждую минуту поездки свыше 30

        return Math.max(0, Math.min(100, baseScore));
    }

    private String getTransferSuffix(int count) {
        if (count == 1) return "а";
        if (count >= 2 && count <= 4) return "и";
        return "";
    }

    @Override
    public String toString() {
        return String.format("TripOption[%s, %d min, %d transfers, %.1f manat]",
                tripType, totalTravelMinutes, transfersCount, estimatedCostManat);
    }
}