package com.stealadeal.web;

import com.stealadeal.domain.Dealer;
import com.stealadeal.repository.DealerRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/dealers")
@Validated
public class DealerController {

    private final DealerRepository dealerRepository;

    public DealerController(DealerRepository dealerRepository) {
        this.dealerRepository = dealerRepository;
    }

    @GetMapping
    public List<DealerResponse> getDealers() {
        return dealerRepository.findAll().stream().map(DealerResponse::from).toList();
    }

    @GetMapping("/{dealerId}")
    public DealerResponse getDealer(@PathVariable Long dealerId) {
        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealer not found"));
        return DealerResponse.from(dealer);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DealerResponse createDealer(@Valid @RequestBody CreateDealerRequest request) {
        Dealer dealer = new Dealer();
        dealer.setName(request.name());
        dealer.setLicenseNumber(request.licenseNumber());
        dealer.setCity(request.city());
        dealer.setState(request.state().toUpperCase());
        dealer.setApproved(false);
        return DealerResponse.from(dealerRepository.save(dealer));
    }

    @PatchMapping("/{dealerId}/approval")
    public DealerResponse updateApproval(@PathVariable Long dealerId, @Valid @RequestBody UpdateDealerApprovalRequest request) {
        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealer not found"));
        dealer.setApproved(request.approved());
        return DealerResponse.from(dealerRepository.save(dealer));
    }

    @PutMapping("/{dealerId}")
    public DealerResponse updateDealer(@PathVariable Long dealerId, @Valid @RequestBody UpdateDealerRequest request) {
        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealer not found"));
        dealer.setName(request.name());
        dealer.setLicenseNumber(request.licenseNumber());
        dealer.setCity(request.city());
        dealer.setState(request.state().toUpperCase());
        return DealerResponse.from(dealerRepository.save(dealer));
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
