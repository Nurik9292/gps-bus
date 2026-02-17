package biz.ugur.busroutebackend.complaint.application.usecase;

import biz.ugur.busroutebackend.complaint.application.dto.ComplaintResult;
import biz.ugur.busroutebackend.complaint.application.dto.CreateComplaintInput;
import biz.ugur.busroutebackend.complaint.domain.model.Complaint;
import biz.ugur.busroutebackend.complaint.domain.repository.ComplaintRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateComplaintUseCase extends BaseUseCase<Mono<CreateComplaintInput>, ComplaintResult> {

    private final ComplaintRepository complaintRepository;
    private final EmailNotificationService emailNotificationService;

    public CreateComplaintUseCase(ComplaintRepository complaintRepository,
                                   EmailNotificationService emailNotificationService,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.complaintRepository = complaintRepository;
        this.emailNotificationService = emailNotificationService;
    }

    @Override
    protected Mono<ComplaintResult> process(Mono<CreateComplaintInput> request) {
        return request.flatMap(cmd -> {
            Complaint complaint = Complaint.create(cmd.type(), cmd.title(), cmd.description(), cmd.clientId());
            return complaintRepository.save(complaint)
                    .flatMap(saved -> emailNotificationService
                            .sendComplaintNotification(saved.getTitle(), saved.getType().name(), saved.getDescription())
                            .thenReturn(saved))
                    .map(ComplaintResult::fromDomain);
        });
    }

    @Override
    protected String getBoundContext() {
        return "complaint";
    }
}
