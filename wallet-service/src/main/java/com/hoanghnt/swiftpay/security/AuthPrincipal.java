package com.hoanghnt.swiftpay.security;

import java.security.Principal;
import java.util.UUID;

import org.springframework.security.core.Authentication;

public record AuthPrincipal(UUID userId, String username) implements Principal {

    @Override
    public String getName() {
        return username;
    }

    public static UUID userId(Authentication authentication) {
        return ((AuthPrincipal) authentication.getPrincipal()).userId();
    }
}
