package biz.ugur.busroutebackend.replay.core;

public interface InnovationAware {

    double lastInnovation();

    double lastInnovationVariance();

    boolean lastUpdateAccepted();
}
