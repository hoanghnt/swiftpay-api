package com.hoanghnt.swiftpay.security;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.exception.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_PREFIX = "rate:";
    private static final long WINDOW_SECONDS = 60;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String endpointGroup = resolveEndpointGroup(request.getRequestURI());
        int limit = resolveLimit(endpointGroup);
        String identifier = resolveIdentifier(request);

        long currentWindow = Instant.now().getEpochSecond() / WINDOW_SECONDS;
        String key = RATE_PREFIX + identifier + ":" + endpointGroup + ":" + currentWindow;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS));
        }

        if (count != null && count > limit) {
            writeRateLimitExceeded(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveEndpointGroup(String uri) {
        if (uri.endsWith("/wallet/transfer") || uri.contains("/transactions/transfer")) {
            return "transfer";
        }
        if (uri.endsWith("/wallet/topup")) {
            return "topup";
        }
        if (uri.endsWith("/wallet/withdraw")) {
            return "withdraw";
        }
        if (uri.endsWith("/auth/login")) {
            return "login";
        }
        return "default";
    }

    private int resolveLimit(String endpointGroup) {
        return switch (endpointGroup) {
            case "transfer" -> 10;
            case "topup" -> 5;
            case "withdraw" -> 5;
            case "login" -> 10;
            default -> 100;
        };
    }

    private String resolveIdentifier(HttpServletRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitExceeded(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.RATE_LIMIT_EXCEEDED.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        BaseResponse<Void> body = BaseResponse.error(
                ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                ErrorCode.RATE_LIMIT_EXCEEDED.getDefaultMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
