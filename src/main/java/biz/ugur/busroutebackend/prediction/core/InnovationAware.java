package biz.ugur.busroutebackend.prediction.core;

public interface InnovationAware {

    double lastInnovation();

    double lastInnovationVariance();

    boolean lastUpdateAccepted();
}
