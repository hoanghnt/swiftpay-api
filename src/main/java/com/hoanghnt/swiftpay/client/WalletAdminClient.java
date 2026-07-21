package com.hoanghnt.swiftpay.client;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoanghnt.swiftpay.client.dto.WalletSummaryView;
import com.hoanghnt.swiftpay.client.dto.WalletView;
import com.hoanghnt.swiftpay.config.WalletServiceProperties;
import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WalletAdminClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WalletAdminClient(WalletServiceProperties props, ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder) {
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        this.restClient = restClientBuilder
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("X-Internal-Api-Key", props.getInternalApiKey())
                .build();
    }

    public void freeze(UUID userId) {
        post("/internal/wallets/" + userId + "/freeze");
    }

    public void unfreeze(UUID userId) {
        post("/internal/wallets/" + userId + "/unfreeze");
    }

    @CircuitBreaker(name = "walletService")
    public Optional<WalletView> getWallet(UUID userId) {
        try {
            BaseResponse<WalletView> resp = restClient.get()
                    .uri("/internal/wallets/{userId}", userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<BaseResponse<WalletView>>() {});
            return Optional.ofNullable(resp != null ? resp.data() : null);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (ResourceAccessException e) {
            log.warn("wallet-service unreachable on getWallet", e);
            throw new DownstreamUnavailableException("wallet-service", "Wallet service is temporarily unavailable", e);
        }
    }

    @CircuitBreaker(name = "walletService")
    public WalletSummaryView summary() {
        try {
            BaseResponse<WalletSummaryView> resp = restClient.get()
                    .uri("/internal/wallets/summary")
                    .retrieve()
                    .body(new ParameterizedTypeReference<BaseResponse<WalletSummaryView>>() {});
            return resp != null && resp.data() != null ? resp.data()
                    : new WalletSummaryView(0, 0, java.math.BigDecimal.ZERO);
        } catch (ResourceAccessException e) {
            log.warn("wallet-service unreachable on summary", e);
            throw new DownstreamUnavailableException("wallet-service", "Wallet service is temporarily unavailable", e);
        }
    }

    private void post(String uri) {
        try {
            restClient.post()
                    .uri(uri)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, rsp) -> {
                        throw toBusinessException(rsp);
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {
            log.warn("wallet-service unreachable on {}", uri, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Wallet service is temporarily unavailable");
        }
    }

    private BusinessException toBusinessException(ClientHttpResponse rsp) {
        String code = null;
        String message = "Wallet operation rejected";
        try {
            JsonNode node = objectMapper.readTree(rsp.getBody());
            code = node.path("errorCode").asText(null);
            message = node.path("message").asText(message);
        } catch (IOException e) {
            log.warn("Cannot parse wallet-service error body", e);
        }
        return new BusinessException(mapCode(code), message);
    }

    private ErrorCode mapCode(String code) {
        if (code == null) {
            return ErrorCode.BUSINESS_ERROR;
        }
        return switch (code) {
            case "WAL_001" -> ErrorCode.WALLET_NOT_FOUND;
            case "WAL_002" -> ErrorCode.WALLET_FROZEN;
            case "WAL_005" -> ErrorCode.WALLET_ALREADY_FROZEN;
            case "WAL_006" -> ErrorCode.WALLET_NOT_FROZEN;
            default -> ErrorCode.BUSINESS_ERROR;
        };
    }
}
