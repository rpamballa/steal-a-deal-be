package com.stealadeal.service.buyer;

import com.stealadeal.domain.Favorite;
import com.stealadeal.domain.Vehicle;
import com.stealadeal.repository.FavoriteRepository;
import com.stealadeal.repository.VehicleRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class FavoriteService {

    public record AddResult(Favorite favorite, boolean created) {
    }

    private final FavoriteRepository favoriteRepository;
    private final VehicleRepository vehicleRepository;

    public FavoriteService(FavoriteRepository favoriteRepository, VehicleRepository vehicleRepository) {
        this.favoriteRepository = favoriteRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public List<Favorite> list(Long userId) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public AddResult add(Long userId, Long vehicleId) {
        if (vehicleId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vehicleId is required");
        }
        Favorite existing = favoriteRepository.findByUserIdAndVehicleId(userId, vehicleId).orElse(null);
        if (existing != null) {
            return new AddResult(existing, false);
        }
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setVehicle(vehicle);
        favorite.setCreatedAt(OffsetDateTime.now());
        try {
            return new AddResult(favoriteRepository.saveAndFlush(favorite), true);
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            // Concurrent add of the same (user, vehicle): converge to idempotent.
            return new AddResult(
                    favoriteRepository.findByUserIdAndVehicleId(userId, vehicleId)
                            .orElseThrow(() -> race),
                    false);
        }
    }

    public void remove(Long userId, Long vehicleId) {
        favoriteRepository.findByUserIdAndVehicleId(userId, vehicleId)
                .ifPresent(favoriteRepository::delete);
    }
}
