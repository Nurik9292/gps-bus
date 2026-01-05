package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * Repository interface for {@link RouteAssignment} aggregate root.
 *
 * <p>This replaces both {@code VehicleShiftAssignmentRepository} and
 * {@code ImmediateRouteAssignmentRepository} with a unified interface.
 *
 * <p><b>Key Query Patterns:</b>
 * <ul>
 *   <li>Find current assignments (today's date + current shift)</li>
 *   <li>Find assignments for specific date/shift (used by scheduler)</li>
 *   <li>Find all assignments for a vehicle (assignment history)</li>
 *   <li>Find expired assignments (cleanup job)</li>
 * </ul>
 */
public interface RouteAssignmentRepository extends BaseRepository<RouteAssignment, RouteAssignmentId> {

    /**
     * Finds the active assignment for a vehicle on a specific date and shift.
     *
     * <p>Returns at most one result due to unique constraint on (vehicle_id, effective_date, shift_type).
     *
     * @param vehicleId     vehicle ID
     * @param effectiveDate date of assignment
     * @param shiftType     shift type (FIRST or SECOND)
     * @return Mono of RouteAssignment, or empty if no active assignment found
     */
    Mono<RouteAssignment> findActiveByVehicleAndDateAndShift(
            VehicleId vehicleId,
            LocalDate effectiveDate,
            ShiftType shiftType
    );

    /**
     * Finds all active assignments for a vehicle across all dates/shifts.
     *
     * <p>Useful for displaying assignment history or checking for conflicts.
     *
     * @param vehicleId vehicle ID
     * @return Flux of active RouteAssignments for this vehicle
     */
    Flux<RouteAssignment> findActiveByVehicleId(VehicleId vehicleId);

    /**
     * Finds all active assignments for a specific date and shift.
     *
     * <p>Used by scheduler to apply all assignments when a shift starts.
     *
     * <p>Example: At 07:00, find all assignments for today's FIRST shift.
     *
     * @param effectiveDate date of assignments
     * @param shiftType     shift type (FIRST or SECOND)
     * @return Flux of active RouteAssignments for this date/shift
     */
    Flux<RouteAssignment> findActiveByDateAndShift(
            LocalDate effectiveDate,
            ShiftType shiftType
    );

    /**
     * Finds all active assignments for a route.
     *
     * <p>Useful for checking which vehicles are assigned to a specific route.
     *
     * @param routeId route ID
     * @return Flux of active RouteAssignments for this route
     */
    Flux<RouteAssignment> findActiveByRouteId(BusRouteId routeId);

    /**
     * Finds all assignments (active or expired) that have an expiration time set
     * and have expired (expiresAt is in the past).
     *
     * <p>Used by cleanup job to deactivate expired assignments.
     *
     * @return Flux of expired RouteAssignments
     */
    Flux<RouteAssignment> findExpiredAssignments();

    /**
     * Finds all active assignments for dates before the specified date.
     *
     * <p>Used by midnight cleanup job to delete old assignments.
     *
     * <p>Example: Find all assignments with effective_date < today
     *
     * @param date cutoff date (exclusive)
     * @return Flux of active RouteAssignments with effectiveDate before specified date
     */
    Flux<RouteAssignment> findActiveByEffectiveDateBefore(LocalDate date);

    /**
     * Finds all active assignments created by a specific admin.
     *
     * <p>Useful for audit purposes.
     *
     * @param assignedBy username of admin
     * @return Flux of active RouteAssignments created by this admin
     */
    Flux<RouteAssignment> findActiveByAssignedBy(String assignedBy);

    /**
     * Checks if an active assignment exists for a vehicle on a specific date/shift.
     *
     * @param vehicleId     vehicle ID
     * @param effectiveDate date of assignment
     * @param shiftType     shift type (FIRST or SECOND)
     * @return Mono of Boolean (true if exists, false otherwise)
     */
    Mono<Boolean> existsActiveByVehicleAndDateAndShift(
            VehicleId vehicleId,
            LocalDate effectiveDate,
            ShiftType shiftType
    );

    /**
     * Deactivates (soft deletes) all active assignments for a vehicle.
     *
     * <p>Used when removing all assignments for a vehicle.
     *
     * @param vehicleId vehicle ID
     * @return Mono of Integer (number of assignments deactivated)
     */
    Mono<Integer> deactivateAllByVehicleId(VehicleId vehicleId);

    /**
     * Deactivates (soft deletes) an assignment.
     *
     * <p>Convenience method equivalent to:
     * <pre>{@code
     * findById(id)
     *   .map(RouteAssignment::deactivate)
     *   .flatMap(this::save)
     * }</pre>
     *
     * @param id assignment ID
     * @return Mono of RouteAssignment (deactivated)
     */
    Mono<RouteAssignment> deactivateById(RouteAssignmentId id);

    /**
     * Deletes (hard deletes) all assignments with effective_date before the specified date.
     *
     * <p>Used by midnight cleanup job to permanently remove old assignments.
     *
     * <p><b>WARNING:</b> This is a hard delete (no soft delete). Use with caution.
     *
     * @param date cutoff date (exclusive)
     * @return Mono of Integer (number of assignments deleted)
     */
    Mono<Integer> deleteByEffectiveDateBefore(LocalDate date);

    Mono<Integer> deleteByEffectiveDate(LocalDate date);

    Mono<Integer> deleteByEffectiveDateAndShift(LocalDate date, ShiftType shiftType);
}
