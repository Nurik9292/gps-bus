package biz.ugur.busroutebackend.routing.domain.model;

import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.events.TripOptionsCalculatedEvent;
import biz.ugur.busroutebackend.routing.domain.events.TripPlanCreatedEvent;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripOption;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripPlanId;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripSearchCriteria;
import biz.ugur.busroutebackend.shared.domain.AggregateRoot;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Table("trip_plans")
public class TripPlan extends AggregateRoot<TripPlan, TripPlanId> {


    @Id
    private TripPlanId tripPlanId;

    private final Location originLocation;
    private final Location destinationLocation;
    private final List<TripOption> tripOptions;
    private final LocalDateTime searchTime;
    private final TripSearchCriteria searchCriteria;

    // Конструктор для создания нового плана поездки
    public TripPlan(TripPlanId tripPlanId, Location originLocation, Location destinationLocation,
                    TripSearchCriteria searchCriteria) {
        this.tripPlanId = tripPlanId != null ? tripPlanId : TripPlanId.generate();
        this.originLocation = validateLocation(originLocation, "Origin");
        this.destinationLocation = validateLocation(destinationLocation, "Destination");
        this.tripOptions = new ArrayList<>();
        this.searchTime = LocalDateTime.now();
        this.searchCriteria = searchCriteria != null ? searchCriteria : TripSearchCriteria.defaultCriteria();

        // Проверяем что точки не слишком близко (минимум 100м)
        if (originLocation.distanceTo(destinationLocation) < 100) {
            throw new IllegalArgumentException("Origin and destination are too close. Minimum distance: 100m");
        }

        registerEvent(new TripPlanCreatedEvent(
                this.tripPlanId.getValue(),
                originLocation.getLatitude(),
                originLocation.getLongitude(),
                destinationLocation.getLatitude(),
                destinationLocation.getLongitude()
        ));
    }

    // Convenience constructor с дефолтными критериями
    public TripPlan(Location originLocation, Location destinationLocation) {
        this(TripPlanId.generate(), originLocation, destinationLocation, TripSearchCriteria.defaultCriteria());
    }

    /**
     * Добавить вариант поездки в план
     * Business Rule: максимум 10 вариантов для производительности
     */
    public void addTripOption(TripOption option) {
        if (option == null) {
            throw new IllegalArgumentException("Trip option cannot be null");
        }

        // Валидация что вариант подходит для данного плана
        if (!option.isValidForTrip(originLocation, destinationLocation)) {
            throw new IllegalArgumentException("Trip option is not valid for this trip plan");
        }

        // Проверяем соответствие критериям поиска
        if (!isOptionAcceptable(option)) {
            return; // Молча отвергаем неподходящие варианты
        }

        // Ограничиваем количество вариантов
        if (tripOptions.size() >= 10) {
            // Заменяем худший вариант если новый лучше
            TripOption worstOption = findWorstOption();
            if (worstOption != null && isOptionBetter(option, worstOption)) {
                tripOptions.remove(worstOption);
                tripOptions.add(option);
            }
        } else {
            tripOptions.add(option);
        }

        registerEvent(new TripOptionsCalculatedEvent(
                this.tripPlanId.getValue(),
                tripOptions.size(),
                option.getTripType().name(),
                option.getTotalTravelMinutes()
        ));
    }

    /**
     * Получить лучшие варианты поездки отсортированные по качеству
     */
    public List<TripOption> getBestOptions(int maxCount) {
        return tripOptions.stream()
                .sorted(this::compareOptions)
                .limit(maxCount)
                .collect(Collectors.toList());
    }

    /**
     * Получить самый быстрый вариант
     */
    public TripOption getFastestOption() {
        return tripOptions.stream()
                .min(Comparator.comparing(TripOption::getTotalTravelMinutes))
                .orElse(null);
    }

    /**
     * Получить вариант с наименьшим количеством пересадок
     */
    public TripOption getOptionWithFewestTransfers() {
        return tripOptions.stream()
                .min(Comparator.comparing(TripOption::getTransfersCount)
                        .thenComparing(TripOption::getTotalTravelMinutes))
                .orElse(null);
    }

    /**
     * Получить самый дешевый вариант
     */
    public TripOption getCheapestOption() {
        return tripOptions.stream()
                .min(Comparator.comparing(option -> (option.getTransfersCount() + 1) * 1.0)) // 1 манат за поездку
                .orElse(null);
    }

    /**
     * Проверить есть ли подходящие варианты
     */
    public boolean hasViableOptions() {
        return !tripOptions.isEmpty();
    }

    /**
     * Получить только прямые маршруты (без пересадок)
     */
    public List<TripOption> getDirectOptions() {
        return tripOptions.stream()
                .filter(option -> option.getTripType() == TripType.DIRECT)
                .sorted(Comparator.comparing(TripOption::getTotalTravelMinutes))
                .collect(Collectors.toList());
    }

    /**
     * Получить варианты с пересадками
     */
    public List<TripOption> getTransferOptions() {
        return tripOptions.stream()
                .filter(option -> option.getTripType() != TripType.DIRECT)
                .sorted(this::compareOptions)
                .collect(Collectors.toList());
    }

    /**
     * Получить статистику по найденным вариантам
     */
    public TripPlanStatistics getStatistics() {
        if (tripOptions.isEmpty()) {
            return new TripPlanStatistics(0, 0, 0, 0, 0);
        }

        int directCount = (int) tripOptions.stream().filter(o -> o.getTripType() == TripType.DIRECT).count();
        int transferCount = tripOptions.size() - directCount;
        int fastestTime = tripOptions.stream().mapToInt(TripOption::getTotalTravelMinutes).min().orElse(0);
        int averageTime = (int) tripOptions.stream().mapToInt(TripOption::getTotalTravelMinutes).average().orElse(0);
        double averageCost = tripOptions.stream().mapToDouble(o -> (o.getTransfersCount() + 1) * 1.0).average().orElse(0);

        return new TripPlanStatistics(directCount, transferCount, fastestTime, averageTime, averageCost);
    }

    /**
     * Проверить достижима ли поездка пешком (для очень коротких расстояний)
     */
    public boolean isWalkable() {
        double distanceMeters = originLocation.distanceTo(destinationLocation);
        return distanceMeters <= searchCriteria.getMaxWalkingDistanceMeters();
    }

    /**
     * Рассчитать время пешком до пункта назначения
     */
    public int getWalkingTimeMinutes() {
        if (!isWalkable()) return -1;

        double distanceMeters = originLocation.distanceTo(destinationLocation);
        // Средняя скорость ходьбы: 5 км/ч = 83.33 м/мин
        return (int) Math.ceil(distanceMeters / 83.33);
    }

    @Override
    public TripPlanId getId() {
        return tripPlanId;
    }

    public List<TripOption> getTripOptions() {
        return new ArrayList<>(tripOptions);
    }

    // Приватные методы

    private Location validateLocation(Location location, String type) {
        if (location == null) {
            throw new IllegalArgumentException(type + " location cannot be null");
        }
        location.validateTurkmenistanBounds();
        return location;
    }

    private boolean isOptionAcceptable(TripOption option) {
        // Проверяем критерии поиска
        if (option.getTransfersCount() > searchCriteria.getMaxTransfers()) {
            return false;
        }

        if (option.getTotalWalkingMinutes() > searchCriteria.getMaxWalkingDistanceMeters() / 83.33) {
            return false;
        }

        // Отвергаем слишком долгие поездки (больше 4 часов)
        if (option.getTotalTravelMinutes() > 240) {
            return false;
        }

        return true;
    }

    private TripOption findWorstOption() {
        return tripOptions.stream()
                .max(this::compareOptions) // Максимум = худший при данной сортировке
                .orElse(null);
    }

    private boolean isOptionBetter(TripOption option1, TripOption option2) {
        return compareOptions(option1, option2) < 0;
    }

    /**
     * Основная логика сравнения вариантов поездки
     * Приоритет: 1) меньше пересадок, 2) быстрее, 3) меньше ходьбы
     */
    private int compareOptions(TripOption a, TripOption b) {
        if (searchCriteria.isPrioritizeFewerTransfers()) {
            int transfersComparison = Integer.compare(a.getTransfersCount(), b.getTransfersCount());
            if (transfersComparison != 0) return transfersComparison;
        }

        if (searchCriteria.isPrioritizeSpeed()) {
            int timeComparison = Integer.compare(a.getTotalTravelMinutes(), b.getTotalTravelMinutes());
            if (timeComparison != 0) return timeComparison;
        }

        if (!searchCriteria.isPrioritizeFewerTransfers()) {
            int transfersComparison = Integer.compare(a.getTransfersCount(), b.getTransfersCount());
            if (transfersComparison != 0) return transfersComparison;
        }

        if (!searchCriteria.isPrioritizeSpeed()) {
            int timeComparison = Integer.compare(a.getTotalTravelMinutes(), b.getTotalTravelMinutes());
            if (timeComparison != 0) return timeComparison;
        }

        return Integer.compare(a.getTotalWalkingMinutes(), b.getTotalWalkingMinutes());
    }

    public record TripPlanStatistics(
            int directOptionsCount,
            int transferOptionsCount,
            int fastestTimeMinutes,
            int averageTimeMinutes,
            double averageCostManat
    ) {}
}