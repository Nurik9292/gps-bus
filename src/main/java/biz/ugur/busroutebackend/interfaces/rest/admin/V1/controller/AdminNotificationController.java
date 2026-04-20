package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.notification.application.dto.NotificationList;
import biz.ugur.busroutebackend.notification.application.dto.NotificationPaginationQuery;
import biz.ugur.busroutebackend.notification.application.dto.NotificationResponse;
import biz.ugur.busroutebackend.notification.application.usecase.admin.*;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.notification.NotificationCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.notification.NotificationUpdateRequest;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_NOTIFICATIONS;

@RestController
@RequestMapping(V1_ADMIN_NOTIFICATIONS)
public class AdminNotificationController extends BasePaginatedController {

    private final CreateNotificationUseCase createNotificationUseCase;
    private final GetNotificationsWithPaginationUseCase getNotificationsWithPaginationUseCase;
    private final GetNotificationByIdUseCase getNotificationByIdUseCase;
    private final UpdateNotificationUseCase updateNotificationUseCase;
    private final DeleteNotificationUseCase deleteNotificationUseCase;
    private final ToggleStatusNotificationUseCase toggleStatusNotificationUseCase;

    public AdminNotificationController(CreateNotificationUseCase createNotificationUseCase,
                                       GetNotificationsWithPaginationUseCase getNotificationsWithPaginationUseCase,
                                       GetNotificationByIdUseCase getNotificationByIdUseCase,
                                       UpdateNotificationUseCase updateNotificationUseCase,
                                       DeleteNotificationUseCase deleteNotificationUseCase,
                                       ToggleStatusNotificationUseCase toggleStatusNotificationUseCase,
                                       MessageSource messageSource) {
        super(messageSource);
        this.createNotificationUseCase = createNotificationUseCase;
        this.getNotificationsWithPaginationUseCase = getNotificationsWithPaginationUseCase;
        this.getNotificationByIdUseCase = getNotificationByIdUseCase;
        this.updateNotificationUseCase = updateNotificationUseCase;
        this.deleteNotificationUseCase = deleteNotificationUseCase;
        this.toggleStatusNotificationUseCase = toggleStatusNotificationUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminNotificationController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<NotificationList>>> getAllNotifications(
            @RequestParam(required = false, defaultValue = "false") Boolean active,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "display_order") String sort,
            @RequestParam(required = false, defaultValue = "asc") String order,
            @RequestParam(required = false) String query) {

        validatePagination(page, size);

        NotificationPaginationQuery paginationQuery = NotificationPaginationQuery.create(
            page,
            size,
            camelToSnake(sort),
            order,
            active,
            query
        );

        return okPaginated(Mono.just(paginationQuery)
                .as(getNotificationsWithPaginationUseCase::execute));
    }

    @GetMapping("/{notificationId}")
    public Mono<ResponseEntity<ApiResponse<NotificationResponse>>> getNotificationById(@PathVariable String notificationId) {
        return ok(Mono.just(notificationId)
                .as(getNotificationByIdUseCase::execute));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<NotificationResponse>>> createNotification(@Valid @RequestBody NotificationCreateRequest request) {
        return created(Mono.just(request.toCommand())
                .as(createNotificationUseCase::execute));
    }

    @PutMapping("/{notificationId}")
    public Mono<ResponseEntity<ApiResponse<NotificationResponse>>> updateNotification(@PathVariable String notificationId,
            @Valid @RequestBody NotificationUpdateRequest request) {
        return ok(Mono.just(request.toCommand(notificationId)).as(updateNotificationUseCase::execute));
    }


    @DeleteMapping("/{notificationId}")
    public Mono<ResponseEntity<Void>> deleteNotification(@PathVariable String notificationId) {
        return Mono.just(notificationId)
                .as(deleteNotificationUseCase::execute)
                .then(noContent());
    }

    @GetMapping("/toggle-status/{id}")
    public Mono<ResponseEntity<ApiResponse<NotificationResponse>>> toggleStatus(@PathVariable String id, @RequestParam Boolean active) {
        return ok(Mono.just(new ToggleStatusNotificationUseCase.Request(id, active))
                .as(toggleStatusNotificationUseCase::execute));
    }


}
