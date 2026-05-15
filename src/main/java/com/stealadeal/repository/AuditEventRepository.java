package com.stealadeal.repository;

import com.stealadeal.domain.AuditEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByDealIdOrderByCreatedAtDesc(Long dealId);

    List<AuditEvent> findByActorReferenceOrderByCreatedAtDesc(String actorReference);

    List<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
