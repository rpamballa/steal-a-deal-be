package com.stealadeal.repository;

import com.stealadeal.domain.DealerInvoice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealerInvoiceRepository extends JpaRepository<DealerInvoice, Long> {

    List<DealerInvoice> findByDealerIdOrderByCreatedAtDesc(Long dealerId);
}
