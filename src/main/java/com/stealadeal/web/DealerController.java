package com.stealadeal.web;

import com.stealadeal.domain.Dealer;
import com.stealadeal.service.DealerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dealers")
@Validated
public class DealerController {

    private final DealerService dealerService;

    public DealerController(DealerService dealerService) {
        this.dealerService = dealerService;
    }

    @GetMapping
    public List<DealerResponse> getDealers() {
        return dealerService.getDealers().stream().map(DealerResponse::from).toList();
    }

    @GetMapping("/{dealerId}")
    public DealerResponse getDealer(@PathVariable Long dealerId) {
        return DealerResponse.from(dealerService.getDealer(dealerId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DealerResponse createDealer(@Valid @RequestBody CreateDealerRequest request) {
        return DealerResponse.from(dealerService.createDealer(
                request.name(),
                request.licenseNumber(),
                request.city(),
                request.state()
        ));
    }

    @PatchMapping("/{dealerId}/approval")
    @PreAuthorize("@accessControl.isAdmin(authentication)")
    public DealerResponse updateApproval(@PathVariable Long dealerId, @Valid @RequestBody UpdateDealerApprovalRequest request) {
        return DealerResponse.from(dealerService.updateDealerApproval(dealerId, request.approved()));
    }

    @PutMapping("/{dealerId}")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public DealerResponse updateDealer(@PathVariable Long dealerId, @Valid @RequestBody UpdateDealerRequest request) {
        return DealerResponse.from(dealerService.updateDealer(
                dealerId,
                request.name(),
                request.licenseNumber(),
                request.city(),
                request.state()
        ));
    }

    public record CreateDealerRequest(
            @NotBlank String name,
            @NotBlank String licenseNumber,
            @NotBlank String city,
            @Pattern(regexp = "^[A-Za-z]{2}$") String state
    ) {
    }

    public record UpdateDealerApprovalRequest(boolean approved) {
    }

    public record UpdateDealerRequest(
            @NotBlank String name,
            @NotBlank String licenseNumber,
            @NotBlank String city,
            @Pattern(regexp = "^[A-Za-z]{2}$") String state
    ) {
    }

    public record DealerResponse(Long id, String name, String licenseNumber, String city, String state, boolean approved) {

        static DealerResponse from(Dealer dealer) {
            return new DealerResponse(
                    dealer.getId(),
                    dealer.getName(),
                    dealer.getLicenseNumber(),
                    dealer.getCity(),
                    dealer.getState(),
                    dealer.isApproved()
            );
        }
    }
}
