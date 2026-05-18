package com.stealadeal.security;

import com.stealadeal.config.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP fixed-window rate limit on {@code /api/auth/*} (§10 MANDATORY:
 * 5 req/min/IP). Limit is configurable via
 * {@code app.auth.rate-limit-per-minute} so the test profile can lift
 * it. In-memory and zero-dependency; for multi-instance prod this
 * should move to a shared store (Redis), tracked in the go-live plan.
 */
@Component
@Order(0)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private record Window(long minute, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxPerMinute;

    public AuthRateLimitFilter(AuthProperties properties) {
        this.maxPerMinute = properties.rateLimitPerMinute();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = clientIp(request);
        long minute = Instant.now().getEpochSecond() / 60;
        Window w = windows.compute(ip, (k, cur) ->
                (cur == null || cur.minute() != minute)
                        ? new Window(minute, new AtomicInteger(0))
                        : cur);
        if (w.count().incrementAndGet() > maxPerMinute) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded for "
                            + "/api/auth — try again shortly\",\"timestamp\":\""
                            + java.time.OffsetDateTime.now() + "\"}");
            return;
        }
        if (windows.size() > 50_000) {
            windows.clear();
        }
        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
