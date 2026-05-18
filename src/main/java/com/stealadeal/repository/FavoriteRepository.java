package com.stealadeal.repository;

import com.stealadeal.domain.Favorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Favorite> findByUserIdAndVehicleId(Long userId, Long vehicleId);
    List<Favorite> findByVehicleId(Long vehicleId);
}
