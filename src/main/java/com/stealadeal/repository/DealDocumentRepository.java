package com.stealadeal.repository;

import com.stealadeal.domain.DealDocument;
import com.stealadeal.domain.DocumentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealDocumentRepository extends JpaRepository<DealDocument, Long> {

    List<DealDocument> findByDealId(Long dealId);

    List<DealDocument> findByDealIdAndStatus(Long dealId, DocumentStatus status);
}
