package com.hoanghnt.swiftpay.client;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoanghnt.swiftpay.client.dto.InternalCreditRequest;
import com.hoanghnt.swiftpay.client.dto.InternalDebitRequest;
import com.hoanghnt.swiftpay.client.dto.InternalTransferRequest;
import com.hoanghnt.swiftpay.client.dto.WalletOpResult;
import com.hoanghnt.swiftpay.config.WalletServiceProperties;
import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.exception.ErrorCode;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WalletServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WalletServiceClient(WalletServiceProperties props, ObjectMapper objectMapper,
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

    @CircuitBreaker(name = "walletService")
    @Retry(name = "walletService", fallbackMethod = "transferFallback")
    public WalletOpResult transfer(InternalTransferRequest request) {
        return post("/internal/wallets/transfer", request);
    }

    @CircuitBreaker(name = "walletService")
    @Retry(name = "walletService", fallbackMethod = "creditFallback")
    public WalletOpResult credit(InternalCreditRequest request) {
        return post("/internal/wallets/credit", request);
    }

    @CircuitBreaker(name = "walletService")
    @Retry(name = "walletService", fallbackMethod = "debitFallback")
    public WalletOpResult debit(InternalDebitRequest request) {
        return post("/internal/wallets/debit", request);
    }

    private WalletOpResult transferFallback(InternalTransferRequest request, WalletBusinessException e) {
        throw e;
    }

    private WalletOpResult transferFallback(InternalTransferRequest request, Throwable t) {
        throw unavailable("transfer", t);
    }

    private WalletOpResult creditFallback(InternalCreditRequest request, WalletBusinessException e) {
        throw e;
    }

    private WalletOpResult creditFallback(InternalCreditRequest request, Throwable t) {
        throw unavailable("credit", t);
    }

    private WalletOpResult debitFallback(InternalDebitRequest request, WalletBusinessException e) {
        throw e;
    }

    private WalletOpResult debitFallback(InternalDebitRequest request, Throwable t) {
        throw unavailable("debit", t);
    }

    private WalletUnavailableException unavailable(String op, Throwable t) {
        if (t instanceof WalletUnavailableException wue) {
            return wue;
        }
        log.warn("wallet-service {} fell back (circuit-breaker/retry exhausted): {}", op, t.toString());
        return new WalletUnavailableException("wallet-service " + op + " unavailable after resilience4j", t);
    }

    public boolean isOperationApplied(String opKey) {
        try {
            BaseResponse<JsonNode> resp = restClient.get()
                    .uri("/internal/wallets/operations/{opKey}", opKey)
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError(), (req, rsp) -> {
                        throw new WalletUnavailableException("wallet-service 5xx on operations lookup: " + rsp.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<BaseResponse<JsonNode>>() {});
            return resp != null && resp.data() != null && resp.data().path("applied").asBoolean(false);
        } catch (ResourceAccessException e) {
            throw new WalletUnavailableException("wallet-service unreachable on operations lookup", e);
        }
    }

    private WalletOpResult post(String uri, Object body) {
        try {
            BaseResponse<WalletOpResult> resp = restClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), (req, rsp) -> {
                        throw toBusinessException(rsp);
                    })
                    .onStatus(status -> status.is5xxServerError(), (req, rsp) -> {
                        throw new WalletUnavailableException("wallet-service 5xx: " + rsp.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<BaseResponse<WalletOpResult>>() {});
            if (resp == null || resp.data() == null) {
                throw new WalletUnavailableException("wallet-service returned empty body for " + uri);
            }
            return resp.data();
        } catch (ResourceAccessException e) {
            throw new WalletUnavailableException("wallet-service unreachable: " + uri, e);
        }
    }

    private WalletBusinessException toBusinessException(ClientHttpResponse rsp) {
        String code = null;
        String message = "Wallet operation rejected";
        try {
            JsonNode node = objectMapper.readTree(rsp.getBody());
            code = node.path("errorCode").asText(null);
            message = node.path("message").asText(message);
        } catch (IOException e) {
            log.warn("Cannot parse wallet-service error body", e);
        }
        return new WalletBusinessException(ErrorCode.fromCode(code), message);
    }
}
