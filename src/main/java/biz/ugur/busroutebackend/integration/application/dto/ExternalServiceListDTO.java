package biz.ugur.busroutebackend.integration.application.dto;

import lombok.Builder;

import java.util.List;


@Builder
public record ExternalServiceListDTO(
        List<ExternalServiceDTO> services,
        long totalCount,
        long activeCount
) {
}
