package biz.ugur.busroutebackend.complaint.application.usecase;

import biz.ugur.busroutebackend.complaint.application.dto.ComplaintList;
import biz.ugur.busroutebackend.complaint.application.dto.ComplaintResult;
import biz.ugur.busroutebackend.complaint.application.dto.GetAllComplaintsInput;
import biz.ugur.busroutebackend.complaint.domain.repository.ComplaintRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetAllComplaintsUseCase extends BaseUseCase<Mono<GetAllComplaintsInput>, ComplaintList> {

    private final ComplaintRepository complaintRepository;

    public GetAllComplaintsUseCase(ComplaintRepository complaintRepository,
                                    CorrelationContextService correlationService,
                                    EventBus eventBus) {
        super(correlationService, eventBus);
        this.complaintRepository = complaintRepository;
    }

    @Override
    protected Mono<ComplaintList> process(Mono<GetAllComplaintsInput> request) {
        return request.flatMap(input -> {
            Sort sort = Sort.by(
                    Sort.Direction.fromString(input.getOrder()),
                    input.getSort()
            );
            Pageable pageable = PageRequest.of(input.getPage() - 1, input.getSize(), sort);

            return Mono.zip(
                    complaintRepository.findByFilters(input.getStatus(), input.getType(), pageable)
                            .map(ComplaintResult::fromDomain)
                            .collectList(),
                    complaintRepository.countByFilters(input.getStatus(), input.getType())
            ).map(tuple -> new ComplaintList(
                    tuple.getT1(),
                    tuple.getT2(),
                    input.getPage(),
                    input.getSize(),
                    tuple.getT2()
            ));
        });
    }

    @Override
    protected String getBoundContext() {
        return "complaint";
    }
}
