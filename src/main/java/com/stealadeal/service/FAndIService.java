package com.stealadeal.service;

import com.stealadeal.domain.Deal;
import com.stealadeal.domain.DealFAndIProduct;
import com.stealadeal.domain.FAndIProduct;
import com.stealadeal.domain.FAndIProductType;
import com.stealadeal.repository.DealFAndIProductRepository;
import com.stealadeal.repository.DealRepository;
import com.stealadeal.repository.FAndIProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * F&I (finance & insurance) product catalog and per-deal attachment.
 * Implements the pitch's revenue stream #3: dealers sell warranty / GAP
 * / protection products and the platform captures a revenue-share
 * percentage, snapshotted per attachment.
 */
@Service
@Transactional
public class FAndIService {

    public record FAndISummary(
            Long dealId,
            BigDecimal totalRetail,
            BigDecimal totalPlatformRevenue,
            List<DealFAndIProduct> items
    ) {
    }

    private final FAndIProductRepository productRepository;
    private final DealFAndIProductRepository dealProductRepository;
    private final DealRepository dealRepository;
    private final AuditService auditService;

    public FAndIService(
            FAndIProductRepository productRepository,
            DealFAndIProductRepository dealProductRepository,
            DealRepository dealRepository,
            AuditService auditService
    ) {
        this.productRepository = productRepository;
        this.dealProductRepository = dealProductRepository;
        this.dealRepository = dealRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<FAndIProduct> listProducts(boolean activeOnly) {
        return activeOnly
                ? productRepository.findByActiveOrderByIdAsc(true)
                : productRepository.findAll();
    }

    public FAndIProduct createProduct(
            FAndIProductType type,
            String name,
            BigDecimal retailPrice,
            BigDecimal revenueShareRate
    ) {
        if (revenueShareRate.compareTo(BigDecimal.ZERO) < 0 || revenueShareRate.compareTo(BigDecimal.ONE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "revenueShareRate must be between 0 and 1");
        }
        FAndIProduct product = new FAndIProduct();
        product.setType(type);
        product.setName(name);
        product.setRetailPrice(retailPrice.setScale(2, RoundingMode.HALF_UP));
        product.setRevenueShareRate(revenueShareRate);
        product.setActive(true);
        product.setCreatedAt(OffsetDateTime.now());
        product.setUpdatedAt(OffsetDateTime.now());
        FAndIProduct saved = productRepository.save(product);
        auditService.record("FNI_PRODUCT_CREATED", "FAndIProduct", saved.getId(), null,
                type + " " + name + " @ " + saved.getRetailPrice());
        return saved;
    }

    public FAndIProduct setProductActive(Long productId, boolean active) {
        FAndIProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        product.setActive(active);
        product.setUpdatedAt(OffsetDateTime.now());
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public FAndISummary getDealFAndI(Long dealId) {
        findDeal(dealId);
        List<DealFAndIProduct> items = dealProductRepository.findByDealIdOrderByCreatedAtAsc(dealId);
        BigDecimal totalRetail = items.stream()
                .map(DealFAndIProduct::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRevenue = items.stream()
                .map(DealFAndIProduct::getRevenueShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new FAndISummary(dealId, totalRetail, totalRevenue, items);
    }

    public DealFAndIProduct attach(Long dealId, Long productId) {
        Deal deal = findDeal(dealId);
        FAndIProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if (!product.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product is not active");
        }
        if (dealProductRepository.findByDealIdAndProductId(dealId, productId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product already attached to this deal");
        }
        BigDecimal price = product.getRetailPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal revenueShareAmount = price
                .multiply(product.getRevenueShareRate())
                .setScale(2, RoundingMode.HALF_UP);

        DealFAndIProduct attachment = new DealFAndIProduct();
        attachment.setDeal(deal);
        attachment.setProduct(product);
        attachment.setPrice(price);
        attachment.setRevenueShareRate(product.getRevenueShareRate());
        attachment.setRevenueShareAmount(revenueShareAmount);
        attachment.setCreatedAt(OffsetDateTime.now());
        DealFAndIProduct saved = dealProductRepository.save(attachment);
        auditService.record("FNI_PRODUCT_ATTACHED", "DealFAndIProduct", saved.getId(), dealId,
                product.getName() + " price=" + price + " share=" + revenueShareAmount);
        return saved;
    }

    public void detach(Long dealId, Long attachmentId) {
        findDeal(dealId);
        DealFAndIProduct attachment = dealProductRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
        if (!attachment.getDeal().getId().equals(dealId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment does not belong to this deal");
        }
        dealProductRepository.delete(attachment);
        auditService.record("FNI_PRODUCT_DETACHED", "DealFAndIProduct", attachmentId, dealId,
                "Detached attachment " + attachmentId);
    }

    private Deal findDeal(Long dealId) {
        return dealRepository.findById(dealId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal not found"));
    }
}
