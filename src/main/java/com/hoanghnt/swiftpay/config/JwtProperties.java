package com.hoanghnt.swiftpay.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    private String secret;

    private String algorithm;

    private Duration accessTokenExpiration;

    private Duration refreshTokenExpiration;

    private String issuer = "swiftpay-api";
}