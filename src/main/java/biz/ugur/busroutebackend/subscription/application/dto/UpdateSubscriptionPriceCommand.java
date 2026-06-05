package biz.ugur.busroutebackend.subscription.application.dto;

public record UpdateSubscriptionPriceCommand(
        String period,
        long amountMinor,
        String updatedBy
) {}
