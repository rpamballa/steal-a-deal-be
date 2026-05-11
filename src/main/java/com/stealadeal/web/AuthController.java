package com.stealadeal.web;

import com.stealadeal.domain.UserRole;
import com.stealadeal.security.AuthenticatedUser;
import com.stealadeal.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return AuthResponse.from(authService.register(new AuthService.RegisterCommand(
                request.displayName(),
                request.email(),
                request.password(),
                request.role(),
                request.dealerId()
        )));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return AuthResponse.from(authService.login(new AuthService.LoginCommand(request.email(), request.password())));
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return new CurrentUserResponse(user.id(), user.displayName(), user.email(), user.role(), user.dealerId());
    }

    public record RegisterRequest(
            @NotBlank String displayName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8) String password,
            @NotNull UserRole role,
            Long dealerId
    ) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {
    }

    public record AuthResponse(
            String token,
            String expiresAt,
            Long userId,
            String displayName,
            String email,
            UserRole role,
            Long dealerId
    ) {

        static AuthResponse from(AuthService.AuthResult result) {
            return new AuthResponse(
                    result.token(),
                    result.expiresAt().toString(),
                    result.userId(),
                    result.displayName(),
                    result.email(),
                    result.role(),
                    result.dealerId()
            );
        }
    }

    public record CurrentUserResponse(
            Long userId,
            String displayName,
            String email,
            UserRole role,
            Long dealerId
    ) {
    }
}
