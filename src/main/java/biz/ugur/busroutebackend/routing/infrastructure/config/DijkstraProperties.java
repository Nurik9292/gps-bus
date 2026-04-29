package biz.ugur.busroutebackend.routing.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Dijkstra routing tuning. Defaults chosen for ~1121-stop network (Ashgabat).
 *
 * <p>Iteration / cost limits were tightened in 2026-04 after the audit
 * documented in {@code docs/DIJKSTRA_AUDIT_REPORT.md §7}. Far-pair requests
 * (unreachable transit destinations such as Gökdere from Gurtly) used to
 * burn 50_000 iterations × 22+ stop pairs ≈ 9 sec wall-time before failing.
 * 10_000 iterations is enough for any reachable path within this network
 * (worst case ≈ 5_000 ops for full BFS over reachable component); see §7.9.
 *
 * <p>{@code maxCostMinutes} of 120 reflects the longest realistic urban
 * route (city buses cap at 90–145 min per {@code bus_routes.estimated_duration_minutes}).
 * 180 was overkill and let dead-end exploration drag on.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "routing.dijkstra")
public class DijkstraProperties {

    private int transferPenaltyMinutes = 5;

    private int maxTransfers = 2;

    private int kPaths = 3;

    private int routePenaltyMinutes = 30;

    private int maxIterations = 10_000;

    private int maxCostMinutes = 120;
}
