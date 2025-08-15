package biz.ugur.busroutebackend.migration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LegacyRoute {
    private Long id;
    private String name;
    private Integer number;
    private String interval;
    private String frontLineWkt; // Geometry as WKT string
    private String backLineWkt;  // Geometry as WKT string
    private Integer routingTime;
    private Integer cityId;

    public String getRouteNumber() {
        return number != null ? number.toString() : "N" + id;
    }

    public String getRouteColor() {
        if (number == null) return "#1976D2";

        String[] colors = {
                "#1976D2", "#388E3C", "#F57C00", "#7B1FA2",
                "#C62828", "#00796B", "#5D4037", "#424242",
                "#E65100", "#1565C0", "#2E7D32", "#6A1B9A"
        };

        return colors[number % colors.length];
    }
}