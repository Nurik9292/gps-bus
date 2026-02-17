package biz.ugur.busroutebackend.complaint.application.usecase;

import biz.ugur.busroutebackend.complaint.application.dto.ComplaintResult;
import biz.ugur.busroutebackend.complaint.application.dto.UpdateComplaintStatusInput;
import biz.ugur.busroutebackend.complaint.domain.exceptions.ComplaintNotFoundException;
import biz.ugur.busroutebackend.complaint.domain.repository.ComplaintRepository;
import biz.ugur.busroutebackend.complaint.domain.valueobjects.ComplaintId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class MarkComplaintInProgressUseCase extends BaseUseCase<Mono<UpdateComplaintStatusInput>, ComplaintResult> {

    private final ComplaintRepository complaintRepository;

    public MarkComplaintInProgressUseCase(ComplaintRepository complaintRepository,
                                           CorrelationContextService correlationService,
                                           EventBus eventBus) {
        super(correlationService, eventBus);
        this.complaintRepository = complaintRepository;
    }

    @Override
    protected Mono<ComplaintResult> process(Mono<UpdateComplaintStatusInput> request) {
        return request.flatMap(input ->
                complaintRepository.findById(ComplaintId.of(input.complaintId()))
                        .switchIfEmpty(Mono.error(new ComplaintNotFoundException(input.complaintId())))
                        .map(complaint -> complaint.markInProgress())
                        .flatMap(complaintRepository::save)
                        .map(ComplaintResult::fromDomain)
        );
    }

    @Override
    protected String getBoundContext() {
        return "complaint";
    }
}
