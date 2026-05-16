package com.stealadeal.web;

import com.stealadeal.domain.AuditEvent;
import com.stealadeal.service.AuditService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("@accessControl.isAdmin(authentication)")
    public List<AuditEventResponse> query(
            @RequestParam(required = false) Long dealId,
            @RequestParam(required = false) String actorReference,
            @RequestParam(required = false, defaultValue = "100") int limit
    ) {
        return auditService.query(dealId, actorReference, limit).stream()
                .map(AuditEventResponse::from)
                .toList();
    }

    public record AuditEventResponse(
            Long id,
            String actorType,
            String actorReference,
            String action,
            String entityType,
            Long entityId,
            Long dealId,
            String detail,
            OffsetDateTime createdAt
    ) {

        static AuditEventResponse from(AuditEvent event) {
            return new AuditEventResponse(
                    event.getId(),
                    event.getActorType(),
                    event.getActorReference(),
                    event.getAction(),
                    event.getEntityType(),
                    event.getEntityId(),
                    event.getDealId(),
                    event.getDetail(),
                    event.getCreatedAt()
            );
        }
    }
}
