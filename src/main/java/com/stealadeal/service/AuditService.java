package com.stealadeal.service;

import com.stealadeal.domain.AuditEvent;
import com.stealadeal.repository.AuditEventRepository;
import com.stealadeal.security.AuthenticatedUser;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit trail for high-value mutations (deal stage changes,
 * dealer approval, subscription changes, deposit settlement, fee
 * settlement). The actor is resolved from the security context so call
 * sites only describe what happened.
 */
@Service
@Transactional
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public void record(String action, String entityType, Long entityId, Long dealId, String detail) {
        AuditEvent event = new AuditEvent();
        AuthenticatedUser actor = currentActor();
        if (actor == null) {
            event.setActorType("SYSTEM");
            event.setActorReference("system");
        } else {
            event.setActorType(actor.role().name());
            event.setActorReference(actor.role().name().equals("DEALER") && actor.dealerId() != null
                    ? String.valueOf(actor.dealerId())
                    : actor.email());
        }
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setDealId(dealId);
        event.setDetail(detail == null ? "" : detail.length() > 1000 ? detail.substring(0, 1000) : detail);
        event.setCreatedAt(OffsetDateTime.now());
        auditEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> query(Long dealId, String actorReference, int limit) {
        if (dealId != null) {
            return auditEventRepository.findByDealIdOrderByCreatedAtDesc(dealId);
        }
        if (actorReference != null && !actorReference.isBlank()) {
            return auditEventRepository.findByActorReferenceOrderByCreatedAtDesc(actorReference);
        }
        int capped = Math.min(Math.max(limit, 1), 500);
        return auditEventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, capped));
    }

    private AuthenticatedUser currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
