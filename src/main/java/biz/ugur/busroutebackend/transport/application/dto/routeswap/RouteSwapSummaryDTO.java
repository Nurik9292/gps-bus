package biz.ugur.busroutebackend.transport.application.dto.routeswap;

import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository.VerdictCount;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record RouteSwapSummaryDTO(
        @JsonProperty("operational_date") LocalDate operationalDate,
        String shift,
        @JsonProperty("swap_suspected") long swapSuspected,
        @JsonProperty("no_axis_fits") long noAxisFits,
        @JsonProperty("intra_family_mismatch") long intraFamilyMismatch,
        @JsonProperty("provider_mismatch") long providerMismatch,
        long total) {

    public static RouteSwapSummaryDTO of(LocalDate operationalDate, String shift, List<VerdictCount> counts) {
        Map<String, Long> byVerdict = counts.stream()
                .collect(Collectors.toMap(VerdictCount::verdict, VerdictCount::count));
        long swap = byVerdict.getOrDefault("SWAP_SUSPECTED", 0L) + byVerdict.getOrDefault("SWAP_CONFIRMED", 0L);
        long noAxis = byVerdict.getOrDefault("NO_AXIS_FITS", 0L);
        long intra = byVerdict.getOrDefault("INTRA_FAMILY_MISMATCH", 0L);
        long provider = byVerdict.getOrDefault("PROVIDER_MISMATCH", 0L);
        return new RouteSwapSummaryDTO(operationalDate, shift, swap, noAxis, intra, provider,
                swap + noAxis + intra + provider);
    }
}
