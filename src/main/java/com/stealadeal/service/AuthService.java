package com.stealadeal.service;

import com.stealadeal.config.AuthProperties;
import com.stealadeal.domain.RefreshToken;
import com.stealadeal.domain.UserAccount;
import com.stealadeal.domain.UserRole;
import com.stealadeal.repository.DealerRepository;
import com.stealadeal.repository.RefreshTokenRepository;
import com.stealadeal.repository.UserAccountRepository;
import com.stealadeal.security.AuthenticatedUser;
import com.stealadeal.security.JwtService;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
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
            String refreshToken,
            OffsetDateTime expiresAt,
            Long userId,
            String displayName,
            String email,
            UserRole role,
            Long dealerId
    ) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DealerRepository dealerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;
    private final JwtService jwtService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            RefreshTokenRepository refreshTokenRepository,
            DealerRepository dealerRepository,
            PasswordEncoder passwordEncoder,
            AuthProperties authProperties,
            JwtService jwtService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.dealerRepository = dealerRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
        this.jwtService = jwtService;
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
        return issue(user);
    }

    public AuthResult login(LoginCommand command) {
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(command.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return issue(user);
    }

    public AuthResult refresh(String refreshTokenValue) {
        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken existing = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (existing.isRevoked() || existing.getExpiresAt().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }
        // Rotate: revoke the presented token, issue a fresh pair.
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
        return issue(existing.getUserAccount());
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> authenticate(String tokenValue) {
        return jwtService.parse(tokenValue);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser getCurrentUser(String email) {
        return userAccountRepository.findByEmailIgnoreCase(email)
                .map(this::toPrincipal)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private AuthResult issue(UserAccount user) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(user);

        byte[] raw = new byte[48];
        RANDOM.nextBytes(raw);
        String refreshValue = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        RefreshToken refresh = new RefreshToken();
        refresh.setToken(refreshValue);
        refresh.setUserAccount(user);
        refresh.setExpiresAt(OffsetDateTime.now().plusDays(authProperties.refreshTtlDays()));
        refresh.setRevoked(false);
        refresh.setCreatedAt(OffsetDateTime.now());
        refreshTokenRepository.save(refresh);

        return new AuthResult(
                access.token(),
                refreshValue,
                access.expiresAt(),
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
