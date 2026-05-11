package com.stealadeal.repository;

import com.stealadeal.domain.DealDocument;
import com.stealadeal.domain.DocumentStatus;
import com.stealadeal.domain.DocumentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealDocumentRepository extends JpaRepository<DealDocument, Long> {

    List<DealDocument> findByDealId(Long dealId);

    List<DealDocument> findByDealIdAndStatus(Long dealId, DocumentStatus status);

    List<DealDocument> findByDealVehicleDealerIdOrderByUpdatedAtDesc(Long dealerId);

    List<DealDocument> findByDealVehicleDealerIdAndStatusOrderByUpdatedAtDesc(Long dealerId, DocumentStatus status);

    Optional<DealDocument> findByDealIdAndType(Long dealId, DocumentType type);
}
