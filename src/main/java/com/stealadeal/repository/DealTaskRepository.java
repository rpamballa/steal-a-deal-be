package com.stealadeal.repository;

import com.stealadeal.domain.DealTask;
import com.stealadeal.domain.DealTaskStatus;
import com.stealadeal.domain.ParticipantType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealTaskRepository extends JpaRepository<DealTask, Long> {

    List<DealTask> findByDealIdOrderByCreatedAtAsc(Long dealId);

    List<DealTask> findByAssigneeTypeAndAssigneeReferenceAndStatusOrderByCreatedAtDesc(
            ParticipantType assigneeType,
            String assigneeReference,
            DealTaskStatus status
    );

    List<DealTask> findByAssigneeTypeAndAssigneeReferenceOrderByCreatedAtDesc(
            ParticipantType assigneeType,
            String assigneeReference
    );

    List<DealTask> findByDealIdInOrderByUpdatedAtDesc(List<Long> dealIds);

    Optional<DealTask> findByDealIdAndCode(Long dealId, String code);
}
