package com.stealadeal.web;

import com.stealadeal.domain.DealTask;
import com.stealadeal.domain.DealTaskStatus;
import com.stealadeal.domain.Notification;
import com.stealadeal.domain.ParticipantType;
import com.stealadeal.service.DealService;
import com.stealadeal.service.TaskNotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class TaskNotificationController {

    private final DealService dealService;
    private final TaskNotificationService taskNotificationService;

    public TaskNotificationController(DealService dealService, TaskNotificationService taskNotificationService) {
        this.dealService = dealService;
        this.taskNotificationService = taskNotificationService;
    }

    @GetMapping("/deals/{dealId}/tasks")
    @PreAuthorize("@accessControl.canAccessDeal(authentication, #dealId)")
    public List<DealTaskResponse> getDealTasks(@PathVariable Long dealId) {
        return dealService.getDealTasks(dealId).stream().map(DealTaskResponse::from).toList();
    }

    @GetMapping("/tasks")
    @PreAuthorize("@accessControl.canAccessAssignee(authentication, #assigneeType, #assigneeReference)")
    public List<DealTaskResponse> getTasksForAssignee(
            @RequestParam ParticipantType assigneeType,
            @RequestParam String assigneeReference,
            @RequestParam(required = false) DealTaskStatus status
    ) {
        return taskNotificationService.getTasksForAssignee(assigneeType, assigneeReference, status)
                .stream()
                .map(DealTaskResponse::from)
                .toList();
    }

    @PatchMapping("/tasks/{taskId}/status")
    @PreAuthorize("@accessControl.canAccessTask(authentication, #taskId)")
    public DealTaskResponse updateTaskStatus(@PathVariable Long taskId, @Valid @RequestBody UpdateTaskStatusRequest request) {
        return DealTaskResponse.from(taskNotificationService.updateTaskStatus(taskId, request.status()));
    }

    @GetMapping("/notifications")
    @PreAuthorize("@accessControl.canAccessAssignee(authentication, #recipientType, #recipientReference)")
    public List<NotificationResponse> getNotifications(
            @RequestParam ParticipantType recipientType,
            @RequestParam String recipientReference,
            @RequestParam(required = false) Boolean unreadOnly
    ) {
        return taskNotificationService.getNotifications(recipientType, recipientReference, unreadOnly)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @PatchMapping("/notifications/{notificationId}/read")
    @PreAuthorize("@accessControl.canAccessNotification(authentication, #notificationId)")
    public NotificationResponse markNotificationRead(
            @PathVariable Long notificationId,
            @Valid @RequestBody MarkNotificationReadRequest request
    ) {
        Notification notification = taskNotificationService.markNotificationRead(notificationId, request.read());
        return NotificationResponse.from(notification);
    }

    public record UpdateTaskStatusRequest(@NotNull DealTaskStatus status) {
    }

    public record MarkNotificationReadRequest(boolean read) {
    }

    public record DealTaskResponse(
            Long id,
            Long dealId,
            String code,
            ParticipantType assigneeType,
            String assigneeReference,
            String title,
            String description,
            DealTaskStatus status,
            OffsetDateTime dueAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {

        static DealTaskResponse from(DealTask task) {
            return new DealTaskResponse(
                    task.getId(),
                    task.getDeal().getId(),
                    task.getCode(),
                    task.getAssigneeType(),
                    task.getAssigneeReference(),
                    task.getTitle(),
                    task.getDescription(),
                    task.getStatus(),
                    task.getDueAt(),
                    task.getCreatedAt(),
                    task.getUpdatedAt()
            );
        }
    }

    public record NotificationResponse(
            Long id,
            Long dealId,
            ParticipantType recipientType,
            String recipientReference,
            String title,
            String message,
            boolean read,
            OffsetDateTime dispatchedAt,
            String dispatchChannels,
            OffsetDateTime createdAt
    ) {

        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getDeal() == null ? null : notification.getDeal().getId(),
                    notification.getRecipientType(),
                    notification.getRecipientReference(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.isRead(),
                    notification.getDispatchedAt(),
                    notification.getDispatchChannels(),
                    notification.getCreatedAt()
            );
        }
    }
}
