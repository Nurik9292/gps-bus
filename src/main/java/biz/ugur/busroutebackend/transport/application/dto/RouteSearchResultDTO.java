package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RouteSearchResultDTO {

    @JsonProperty("route_id")
    private String routeId;

    @JsonProperty("route_number")
    private String routeNumber;

    @JsonProperty("route_name")
    private String routeName;

    @JsonProperty("route_color")
    private String routeColor;

    @JsonProperty("active_vehicles_count")
    private Long activeVehiclesCount;

    @JsonProperty("match_type")
    private String matchType;

    @JsonProperty("relevance_score")
    private Double relevanceScore;

    public RouteSearchResultDTO(String routeId, String routeNumber, String routeName,
                                String routeColor, Long activeVehiclesCount) {
        this.routeId = routeId;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.routeColor = routeColor;
        this.activeVehiclesCount = activeVehiclesCount;
        this.matchType = "unknown";
        this.relevanceScore = 0.0;
    }

    public void calculateRelevance(String searchQuery) {
        String query = searchQuery.toLowerCase().trim();
        String routeNameLower = routeName.toLowerCase();
        String routeNumberLower = routeNumber.toLowerCase();

        if (routeNumberLower.equals(query)) {
            this.relevanceScore = 100.0;
            this.matchType = "number";
            return;
        }

        if (routeNumberLower.contains(query)) {
            this.relevanceScore = 90.0;
            this.matchType = "number";
            return;
        }

        if (routeNameLower.startsWith(query)) {
            this.relevanceScore = 80.0;
            this.matchType = "name";
            return;
        }

        if (routeNameLower.contains(query)) {
            this.relevanceScore = 70.0;
            this.matchType = "name";
            return;
        }

        String[] queryWords = query.split("\\s+");
        int matchedWords = 0;
        for (String word : queryWords) {
            if (routeNameLower.contains(word)) {
                matchedWords++;
            }
        }

        if (matchedWords > 0) {
            this.relevanceScore = 50.0 + (matchedWords * 10.0 / queryWords.length);
            this.matchType = "partial";
        } else {
            this.relevanceScore = 0.0;
            this.matchType = "none";
        }
    }
}