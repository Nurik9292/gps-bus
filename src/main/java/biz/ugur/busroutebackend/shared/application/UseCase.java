package biz.ugur.busroutebackend.shared.application;

public interface UseCase<REQUEST, RESPONSE> {

    RESPONSE execute(REQUEST request);
}