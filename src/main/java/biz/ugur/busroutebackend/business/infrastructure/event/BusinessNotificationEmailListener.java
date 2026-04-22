package biz.ugur.busroutebackend.business.infrastructure.event;

import biz.ugur.busroutebackend.business.domain.events.BusinessApprovedEvent;
import biz.ugur.busroutebackend.business.domain.events.BusinessCreatedEvent;
import biz.ugur.busroutebackend.business.domain.events.BusinessRejectedEvent;
import biz.ugur.busroutebackend.business.domain.events.BusinessSuspendedEvent;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@Slf4j
public class BusinessNotificationEmailListener {

    private final BusinessRepository businessRepository;
    private final EmailNotificationService emailService;

    public BusinessNotificationEmailListener(BusinessRepository businessRepository,
                                              EmailNotificationService emailService) {
        this.businessRepository = businessRepository;
        this.emailService = emailService;
    }

    @Async
    @EventListener
    public void onCreated(BusinessCreatedEvent event) {
        dispatch(event.getBusinessId(), business ->
                emailService.sendBusinessSubmittedAlert(
                        business.getName().getValue(),
                        business.getType().name(),
                        business.getContactInfo() != null ? business.getContactInfo().getPhone() : null,
                        business.getContactInfo() != null ? business.getContactInfo().getEmail() : null));
    }

    @Async
    @EventListener
    public void onApproved(BusinessApprovedEvent event) {
        dispatch(event.getBusinessId(), business ->
                emailService.sendBusinessApprovedNotification(
                        emailOf(business),
                        business.getName().getValue()));
    }

    @Async
    @EventListener
    public void onRejected(BusinessRejectedEvent event) {
        dispatch(event.getBusinessId(), business ->
                emailService.sendBusinessRejectedNotification(
                        emailOf(business),
                        business.getName().getValue(),
                        event.getReason()));
    }

    @Async
    @EventListener
    public void onSuspended(BusinessSuspendedEvent event) {
        dispatch(event.getBusinessId(), business ->
                emailService.sendBusinessSuspendedNotification(
                        emailOf(business),
                        business.getName().getValue(),
                        event.getReason()));
    }


    private void dispatch(String businessId,
                          java.util.function.Function<
                                  biz.ugur.busroutebackend.business.domain.model.Business,
                                  Mono<Void>> op) {
        businessRepository.findById(BusinessId.of(businessId))
                .flatMap(op::apply)
                .onErrorResume(e -> {
                    log.warn("Failed to dispatch business email for {}: {}",
                            businessId, e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    private static String emailOf(biz.ugur.busroutebackend.business.domain.model.Business business) {
        return business.getContactInfo() != null ? business.getContactInfo().getEmail() : null;
    }
}
