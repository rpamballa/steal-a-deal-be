package com.stealadeal.web;

import com.stealadeal.domain.DealFAndIProduct;
import com.stealadeal.domain.FAndIProduct;
import com.stealadeal.domain.FAndIProductType;
import com.stealadeal.service.FAndIService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class FAndIController {

    private final FAndIService fAndIService;

    public FAndIController(FAndIService fAndIService) {
        this.fAndIService = fAndIService;
    }

    @GetMapping("/fni/products")
    @PreAuthorize("@accessControl.isAuthenticated(authentication)")
    public List<ProductResponse> listProducts(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return fAndIService.listProducts(activeOnly).stream().map(ProductResponse::from).toList();
    }

    @PostMapping("/fni/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accessControl.isAdmin(authentication)")
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ProductResponse.from(fAndIService.createProduct(
                request.type(),
                request.name(),
                request.retailPrice(),
                request.revenueShareRate()
        ));
    }

    @PatchMapping("/fni/products/{productId}/active")
    @PreAuthorize("@accessControl.isAdmin(authentication)")
    public ProductResponse setProductActive(
            @PathVariable Long productId,
            @Valid @RequestBody SetActiveRequest request
    ) {
        return ProductResponse.from(fAndIService.setProductActive(productId, request.active()));
    }

    @GetMapping("/deals/{dealId}/fni")
    @PreAuthorize("@accessControl.canAccessDeal(authentication, #dealId)")
    public FAndISummaryResponse getDealFAndI(@PathVariable Long dealId) {
        return FAndISummaryResponse.from(fAndIService.getDealFAndI(dealId));
    }

    @PostMapping("/deals/{dealId}/fni")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accessControl.canAccessDeal(authentication, #dealId)")
    public AttachmentResponse attach(
            @PathVariable Long dealId,
            @Valid @RequestBody AttachRequest request
    ) {
        return AttachmentResponse.from(fAndIService.attach(dealId, request.productId()));
    }

    @DeleteMapping("/deals/{dealId}/fni/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@accessControl.canAccessDeal(authentication, #dealId)")
    public void detach(@PathVariable Long dealId, @PathVariable Long attachmentId) {
        fAndIService.detach(dealId, attachmentId);
    }

    public record CreateProductRequest(
            @NotNull FAndIProductType type,
            @NotBlank String name,
            @NotNull @DecimalMin("0.0") BigDecimal retailPrice,
            @NotNull @DecimalMin("0.0") BigDecimal revenueShareRate
    ) {
    }

    public record SetActiveRequest(@NotNull Boolean active) {
    }

    public record AttachRequest(@NotNull Long productId) {
    }

    public record ProductResponse(
            Long id,
            FAndIProductType type,
            String name,
            BigDecimal retailPrice,
            BigDecimal revenueShareRate,
            boolean active
    ) {

        static ProductResponse from(FAndIProduct product) {
            return new ProductResponse(
                    product.getId(),
                    product.getType(),
                    product.getName(),
                    product.getRetailPrice(),
                    product.getRevenueShareRate(),
                    product.isActive()
            );
        }
    }

    public record AttachmentResponse(
            Long id,
            Long dealId,
            Long productId,
            String productName,
            FAndIProductType type,
            BigDecimal price,
            BigDecimal revenueShareRate,
            BigDecimal revenueShareAmount,
            OffsetDateTime createdAt
    ) {

        static AttachmentResponse from(DealFAndIProduct attachment) {
            return new AttachmentResponse(
                    attachment.getId(),
                    attachment.getDeal().getId(),
                    attachment.getProduct().getId(),
                    attachment.getProduct().getName(),
                    attachment.getProduct().getType(),
                    attachment.getPrice(),
                    attachment.getRevenueShareRate(),
                    attachment.getRevenueShareAmount(),
                    attachment.getCreatedAt()
            );
        }
    }

    public record FAndISummaryResponse(
            Long dealId,
            BigDecimal totalRetail,
            BigDecimal totalPlatformRevenue,
            List<AttachmentResponse> items
    ) {

        static FAndISummaryResponse from(FAndIService.FAndISummary summary) {
            return new FAndISummaryResponse(
                    summary.dealId(),
                    summary.totalRetail(),
                    summary.totalPlatformRevenue(),
                    summary.items().stream().map(AttachmentResponse::from).toList()
            );
        }
    }
}
