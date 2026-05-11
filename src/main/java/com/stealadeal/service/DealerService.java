package com.stealadeal.service;

import com.stealadeal.domain.Dealer;
import com.stealadeal.repository.DealerRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class DealerService {

    private final DealerRepository dealerRepository;

    public DealerService(DealerRepository dealerRepository) {
        this.dealerRepository = dealerRepository;
    }

    @Transactional(readOnly = true)
    public List<Dealer> getDealers() {
        return dealerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Dealer getDealer(Long dealerId) {
        return dealerRepository.findById(dealerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealer not found"));
    }

    public Dealer createDealer(String name, String licenseNumber, String city, String state) {
        Dealer dealer = new Dealer();
        dealer.setName(name);
        dealer.setLicenseNumber(licenseNumber);
        dealer.setCity(city);
        dealer.setState(state.toUpperCase());
        dealer.setApproved(false);
        return dealerRepository.save(dealer);
    }

    public Dealer updateDealerApproval(Long dealerId, boolean approved) {
        Dealer dealer = getDealer(dealerId);
        dealer.setApproved(approved);
        return dealerRepository.save(dealer);
    }

    public Dealer updateDealer(Long dealerId, String name, String licenseNumber, String city, String state) {
        Dealer dealer = getDealer(dealerId);
        dealer.setName(name);
        dealer.setLicenseNumber(licenseNumber);
        dealer.setCity(city);
        dealer.setState(state.toUpperCase());
        return dealerRepository.save(dealer);
    }
}
