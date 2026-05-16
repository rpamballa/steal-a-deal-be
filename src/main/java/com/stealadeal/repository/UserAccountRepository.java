package com.stealadeal.repository;

import com.stealadeal.domain.UserAccount;
import com.stealadeal.domain.UserRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByDealerIdAndRole(Long dealerId, UserRole role);
}
