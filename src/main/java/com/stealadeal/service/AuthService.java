package com.stealadeal.service;

import com.stealadeal.config.AuthProperties;
import com.stealadeal.domain.AuthToken;
import com.stealadeal.domain.UserAccount;
import com.stealadeal.domain.UserRole;
import com.stealadeal.repository.AuthTokenRepository;
import com.stealadeal.repository.DealerRepository;
import com.stealadeal.repository.UserAccountRepository;
import com.stealadeal.security.AuthenticatedUser;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AuthService {

    public record RegisterCommand(
            String displayName,
            String email,
            String password,
            UserRole role,
            Long dealerId
    ) {
    }

    public record LoginCommand(String email, String password) {
    }

    public record AuthResult(
            String token,
            OffsetDateTime expiresAt,
            Long userId,
            String displayName,
            String email,
            UserRole role,
            Long dealerId
    ) {
    }

    private final UserAccountRepository userAccountRepository;
    private final AuthTokenRepository authTokenRepository;
    private final DealerRepository dealerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    public AuthService(
            UserAccountRepository userAccountRepository,
            AuthTokenRepository authTokenRepository,
            DealerRepository dealerRepository,
            PasswordEncoder passwordEncoder,
            AuthProperties authProperties
    ) {
        this.userAccountRepository = userAccountRepository;
        this.authTokenRepository = authTokenRepository;
        this.dealerRepository = dealerRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
    }

    public void ensureBootstrapAdmin() {
        userAccountRepository.findByEmailIgnoreCase(authProperties.bootstrapAdminEmail()).orElseGet(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            UserAccount admin = new UserAccount();
            admin.setDisplayName("Platform Admin");
            admin.setEmail(authProperties.bootstrapAdminEmail().toLowerCase());
            admin.setPasswordHash(passwordEncoder.encode(authProperties.bootstrapAdminPassword()));
            admin.setRole(UserRole.ADMIN);
            admin.setDealerId(null);
            admin.setCreatedAt(now);
            admin.setUpdatedAt(now);
            return userAccountRepository.save(admin);
        });
    }

    public AuthResult register(RegisterCommand command) {
        if (command.role() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin accounts cannot be self-registered");
        }
        if (userAccountRepository.findByEmailIgnoreCase(command.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already registered");
        }
        if (command.role() == UserRole.DEALER) {
            if (command.dealerId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dealerId is required for dealer accounts");
            }
            var dealer = dealerRepository.findById(command.dealerId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealer not found"));
            if (!dealer.isApproved()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dealer must be approved before creating a dealer account");
            }
        }
        if (command.role() == UserRole.BUYER && command.dealerId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dealerId is not allowed for buyer accounts");
        }

        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = new UserAccount();
        user.setDisplayName(command.displayName());
        user.setEmail(command.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(command.password()));
        user.setRole(command.role());
        user.setDealerId(command.role() == UserRole.DEALER ? command.dealerId() : null);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userAccountRepository.save(user);
        return createToken(user);
    }

    public AuthResult login(LoginCommand command) {
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(command.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return createToken(user);
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> authenticate(String tokenValue) {
        OffsetDateTime now = OffsetDateTime.now();
        return authTokenRepository.findByToken(tokenValue)
                .filter(token -> token.getExpiresAt().isAfter(now))
                .map(AuthToken::getUserAccount)
                .map(this::toPrincipal);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser getCurrentUser(String email) {
        return userAccountRepository.findByEmailIgnoreCase(email)
                .map(this::toPrincipal)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private AuthResult createToken(UserAccount user) {
        OffsetDateTime now = OffsetDateTime.now();
        AuthToken token = new AuthToken();
        token.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        token.setUserAccount(user);
        token.setCreatedAt(now);
        token.setLastUsedAt(now);
        token.setExpiresAt(now.plusHours(authProperties.tokenTtlHours()));
        authTokenRepository.save(token);
        return new AuthResult(
                token.getToken(),
                token.getExpiresAt(),
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole(),
                user.getDealerId()
        );
    }

    private AuthenticatedUser toPrincipal(UserAccount user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.getDealerId());
    }
}
