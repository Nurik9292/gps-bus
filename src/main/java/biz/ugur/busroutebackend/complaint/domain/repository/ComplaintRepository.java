package biz.ugur.busroutebackend.complaint.domain.repository;

import biz.ugur.busroutebackend.complaint.domain.model.Complaint;
import biz.ugur.busroutebackend.complaint.domain.model.ComplaintStatus;
import biz.ugur.busroutebackend.complaint.domain.model.ComplaintType;
import biz.ugur.busroutebackend.complaint.domain.valueobjects.ComplaintId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ComplaintRepository extends BaseRepository<Complaint, ComplaintId> {

    Flux<Complaint> findByFilters(ComplaintStatus status, ComplaintType type, Pageable pageable);

    Mono<Long> countByFilters(ComplaintStatus status, ComplaintType type);

    Flux<Complaint> findByClientId(String clientId, Pageable pageable);

    Mono<Long> countByClientId(String clientId);
}
