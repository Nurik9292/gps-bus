package biz.ugur.busroutebackend.interfaces.rest.routing.V1.response;

import biz.ugur.busroutebackend.routing.application.dto.TripOptionDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class TripSearchResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("search_time")
    private LocalDateTime searchTime;

    @JsonProperty("trip_options")
    private List<TripOptionDTO> tripOptions;

    @JsonProperty("summary")
    private SearchSummary summary;

    public TripSearchResponse(String status, String message, List<TripOptionDTO> tripOptions) {
        this.status = status;
        this.message = message;
        this.searchTime = LocalDateTime.now();
        this.tripOptions = tripOptions != null ? tripOptions : Collections.emptyList();
        this.summary = new SearchSummary(this.tripOptions);
    }

    @Data
    public static class SearchSummary {
        @JsonProperty("total_options")
        private int totalOptions;

        @JsonProperty("direct_routes")
        private int directRoutes;

        @JsonProperty("transfer_routes")
        private int transferRoutes;

        @JsonProperty("fastest_option_minutes")
        private Integer fastestOptionMinutes;

        @JsonProperty("option_with_fewest_transfers")
        private Integer optionWithFewestTransfers;

        public SearchSummary(List<TripOptionDTO> options) {
            List<TripOptionDTO> safeOptions = options != null ? options : Collections.emptyList();

            this.totalOptions = safeOptions.size();
            this.directRoutes = (int) safeOptions.stream()
                    .filter(o -> o.getTripType() != null && "direct".equals(o.getTripType()))
                    .count();
            this.transferRoutes = totalOptions - directRoutes;


            this.fastestOptionMinutes = safeOptions.stream()
                    .mapToInt(TripOptionDTO::getTotalTravelMinutes)
                    .min().orElse(0);

            this.optionWithFewestTransfers = safeOptions.stream()
                    .mapToInt(TripOptionDTO::getTransfersCount)
                    .min().orElse(0);
        }
    }
}
