package com.hoanghnt.swiftpay.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoanghnt.swiftpay.config.RateLimitProperties;
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

    private static final RedisScript<List> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local now    = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit  = tonumber(ARGV[3])

            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)
            local count = redis.call('ZCARD', KEYS[1])

            if count >= limit then
                local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
                local retry = window - (now - tonumber(oldest[2]))
                if retry < 0 then retry = 0 end
                return {0, 0, retry}
            end

            redis.call('ZADD', KEYS[1], now, ARGV[4])
            redis.call('PEXPIRE', KEYS[1], window)
            return {1, limit - count - 1, 0}
            """, List.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitProperties properties;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String endpointGroup = resolveEndpointGroup(request.getRequestURI());
        if ("default".equals(endpointGroup)) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = resolveLimit(endpointGroup);
        long windowMs = properties.getWindow().toMillis();
        String key = RATE_PREFIX + resolveIdentifier(request) + ":" + endpointGroup;

        List<?> result = redisTemplate.execute(SLIDING_WINDOW_SCRIPT, List.of(key),
                Long.toString(System.currentTimeMillis()),
                Long.toString(windowMs),
                Integer.toString(limit),
                UUID.randomUUID().toString());

        if (result == null || result.size() < 3) {
            filterChain.doFilter(request, response);
            return;
        }

        long allowed = ((Number) result.get(0)).longValue();
        long remaining = ((Number) result.get(1)).longValue();
        long retryAfterMs = ((Number) result.get(2)).longValue();

        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(remaining, 0)));

        if (allowed == 0) {
            response.setHeader("Retry-After", Long.toString((retryAfterMs + 999) / 1000));
            writeRateLimitExceeded(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveEndpointGroup(String uri) {
        if (uri.endsWith("/transactions/transfer")) {
            return "transfer";
        }
        if (uri.endsWith("/wallet/topup")) {
            return "topup";
        }
        if (uri.endsWith("/wallet/withdraw")) {
            return "withdraw";
        }
        return "default";
    }

    private int resolveLimit(String endpointGroup) {
        return switch (endpointGroup) {
            case "transfer" -> properties.getTransfer();
            case "topup" -> properties.getTopup();
            case "withdraw" -> properties.getWithdraw();
            default -> properties.getDefaultLimit();
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
