package com.stealadeal.web;

import com.stealadeal.domain.Favorite;
import com.stealadeal.domain.SavedSearch;
import com.stealadeal.domain.VehicleStatus;
import com.stealadeal.security.AuthenticatedUser;
import com.stealadeal.service.buyer.BuyerSearchQuery;
import com.stealadeal.service.buyer.FavoriteService;
import com.stealadeal.service.buyer.SavedSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token-scoped buyer engagement: favorites/garage and saved searches.
 * Identity is derived from the JWT — never from the path/body — so a
 * buyer can only see/modify their own rows. BUYER role required.
 */
@RestController
@RequestMapping("/api/me")
@Validated
@PreAuthorize("hasRole('BUYER')")
public class BuyerEngagementController {

    private final FavoriteService favoriteService;
    private final SavedSearchService savedSearchService;

    public BuyerEngagementController(FavoriteService favoriteService, SavedSearchService savedSearchService) {
        this.favoriteService = favoriteService;
        this.savedSearchService = savedSearchService;
    }

    // ---- Favorites ----

    @GetMapping("/favorites")
    public List<FavoriteResponse> listFavorites(@AuthenticationPrincipal AuthenticatedUser user) {
        return favoriteService.list(user.id()).stream().map(FavoriteResponse::from).toList();
    }

    @PostMapping("/favorites")
    public ResponseEntity<FavoriteResponse> addFavorite(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddFavoriteRequest request
    ) {
        FavoriteService.AddResult result = favoriteService.add(user.id(), request.vehicleId());
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(FavoriteResponse.from(result.favorite()));
    }

    @DeleteMapping("/favorites/{vehicleId}")
    public ResponseEntity<Void> removeFavorite(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long vehicleId
    ) {
        favoriteService.remove(user.id(), vehicleId);
        return ResponseEntity.noContent().build();
    }

    // ---- Saved searches ----

    @GetMapping("/saved-searches")
    public List<SavedSearchResponse> listSavedSearches(@AuthenticationPrincipal AuthenticatedUser user) {
        return savedSearchService.list(user.id()).stream().map(SavedSearchResponse::from).toList();
    }

    @PostMapping("/saved-searches")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public SavedSearchResponse createSavedSearch(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SavedSearchInput input
    ) {
        return SavedSearchResponse.from(savedSearchService.create(
                user.id(), input.name(), input.toQuery(), input.alertOnPriceDrop()));
    }

    @PatchMapping("/saved-searches/{id}")
    public SavedSearchResponse updateSavedSearch(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody SavedSearchInput input
    ) {
        return SavedSearchResponse.from(savedSearchService.update(
                user.id(), id, input.name(), input.toQuery(), input.alertOnPriceDrop()));
    }

    @DeleteMapping("/saved-searches/{id}")
    public ResponseEntity<Void> deleteSavedSearch(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        savedSearchService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    // ---- DTOs ----

    public record AddFavoriteRequest(@NotNull Long vehicleId) {
    }

    public record FavoriteResponse(
            Long id,
            Long vehicleId,
            VehicleController.VehicleResponse vehicle,
            String createdAt
    ) {
        static FavoriteResponse from(Favorite f) {
            return new FavoriteResponse(
                    f.getId(),
                    f.getVehicle().getId(),
                    VehicleController.VehicleResponse.from(f.getVehicle()),
                    f.getCreatedAt().toString());
        }
    }

    public record QueryInput(
            String q,
            String make,
            String model,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minYear,
            Integer maxMileage,
            VehicleStatus status
    ) {
    }

    public record SavedSearchInput(
            @NotBlank String name,
            QueryInput query,
            boolean alertOnPriceDrop
    ) {
        BuyerSearchQuery toQuery() {
            QueryInput q = query == null ? new QueryInput(null, null, null, null, null, null, null, null) : query;
            return new BuyerSearchQuery(q.q(), q.make(), q.model(), q.minPrice(),
                    q.maxPrice(), q.minYear(), q.maxMileage(), q.status());
        }
    }

    public record SavedSearchResponse(
            Long id,
            String name,
            QueryInput query,
            boolean alertOnPriceDrop,
            String createdAt,
            int lastMatchedCount
    ) {
        static SavedSearchResponse from(SavedSearch s) {
            return new SavedSearchResponse(
                    s.getId(),
                    s.getName(),
                    new QueryInput(s.getQ(), s.getMake(), s.getModel(), s.getMinPrice(),
                            s.getMaxPrice(), s.getMinYear(), s.getMaxMileage(), s.getStatus()),
                    s.isAlertOnPriceDrop(),
                    s.getCreatedAt().toString(),
                    s.getLastMatchedCount());
        }
    }
}
