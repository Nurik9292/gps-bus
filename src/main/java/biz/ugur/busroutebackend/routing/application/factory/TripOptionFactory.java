package biz.ugur.busroutebackend.routing.application.factory;

import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripScoring;
import biz.ugur.busroutebackend.routing.infrastructure.config.TripScoringProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;


@Component
public class TripOptionFactory {

    private final TripScoringProperties scoringProperties;

    public TripOptionFactory(TripScoringProperties scoringProperties) {
        this.scoringProperties = scoringProperties;
    }

    public TripOption createDirectOption(List<RouteSegment> segments,
                                         int initialWaitingMinutes,
                                         LocalDateTime departureTime) {
        return new TripOption(TripType.DIRECT, segments, initialWaitingMinutes, departureTime, snapshot());
    }

    public TripOption createOneTransferOption(List<RouteSegment> segments,
                                              int initialWaitingMinutes,
                                              LocalDateTime departureTime) {
        return new TripOption(TripType.ONE_TRANSFER, segments, initialWaitingMinutes, departureTime, snapshot());
    }

    public TripOption createTwoTransferOption(List<RouteSegment> segments,
                                              int initialWaitingMinutes,
                                              LocalDateTime departureTime) {
        return new TripOption(TripType.TWO_TRANSFER, segments, initialWaitingMinutes, departureTime, snapshot());
    }

    public TripOption createWalkingOption(List<RouteSegment> segments, LocalDateTime departureTime) {
        return new TripOption(TripType.WALKING, segments, 0, departureTime, snapshot());
    }

    public TripOption createWalkingOnlyOption(List<RouteSegment> segments, LocalDateTime departureTime) {
        return new TripOption(TripType.WALKING_ONLY, segments, 0, departureTime, snapshot());
    }

    private TripScoring snapshot() {
        TripScoringProperties.QualityScore q = scoringProperties.getQualityScore();
        TripScoringProperties.ComfortScore c = scoringProperties.getComfortScore();
        TripScoringProperties.ReliabilityScore r = scoringProperties.getReliabilityScore();
        return new TripScoring(
                scoringProperties.getMaxInitialWaitingMinutes(),
                new TripScoring.QualityCoefficients(
                        q.getSpeedWeight(), q.getTransferWeight(),
                        q.getWalkingWeight(), q.getComfortWeight(),
                        q.getSpeedBaselineMinutes(),
                        q.getTransferPenalty(),
                        q.getWalkingPenaltyPerMinute()),
                new TripScoring.ComfortCoefficients(
                        c.getBase(), c.getTransferPenalty(),
                        c.getWalkingGraceMinutes(),
                        c.getWalkingPenaltyPerMinute(),
                        c.getTravelGraceMinutes(),
                        c.getTravelPenaltyPerMinute()),
                new TripScoring.ReliabilityCoefficients(
                        r.getBase(), r.getTransferPenalty(),
                        r.getWalkingGraceMinutes(),
                        r.getWalkingPenaltyPerMinute(),
                        r.getFloor()));
    }
}
