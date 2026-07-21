package com.hoanghnt.swiftpay.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.hoanghnt.swiftpay.client.dto.TxnSummaryView;
import com.hoanghnt.swiftpay.config.TransactionServiceProperties;
import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.dto.response.PageResponse;
import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.entity.TransactionType;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TransactionAdminClient {

    private final RestClient restClient;

    public TransactionAdminClient(TransactionServiceProperties props, RestClient.Builder restClientBuilder) {
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

    public PageResponse<TransactionResponse> listAll(TransactionType type, TransactionStatus status,
            LocalDateTime from, LocalDateTime to, int page, int size) {
        try {
            BaseResponse<PageResponse<TransactionResponse>> resp = restClient.get()
                    .uri(uri -> {
                        var b = uri.path("/internal/transactions").queryParam("page", page).queryParam("size", size);
                        if (type != null) b.queryParam("type", type.name());
                        if (status != null) b.queryParam("status", status.name());
                        if (from != null) b.queryParam("from", from.toString());
                        if (to != null) b.queryParam("to", to.toString());
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<BaseResponse<PageResponse<TransactionResponse>>>() {});
            return resp != null && resp.data() != null ? resp.data()
                    : new PageResponse<>(List.of(), page, size, 0, 0, true);
        } catch (ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    public TransactionResponse getById(UUID id) {
        try {
            BaseResponse<TransactionResponse> resp = restClient.get()
                    .uri("/internal/transactions/{id}", id)
                    .retrieve()
                    .body(new ParameterizedTypeReference<BaseResponse<TransactionResponse>>() {});
            if (resp == null || resp.data() == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            return resp.data();
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        } catch (ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    @CircuitBreaker(name = "txnService")
    public TxnSummaryView summary() {
        try {
            BaseResponse<TxnSummaryView> resp = restClient.get()
                    .uri("/internal/transactions/summary")
                    .retrieve()
                    .body(new ParameterizedTypeReference<BaseResponse<TxnSummaryView>>() {});
            return resp != null && resp.data() != null ? resp.data()
                    : new TxnSummaryView(0, java.math.BigDecimal.ZERO);
        } catch (ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    private DownstreamUnavailableException unavailable(Exception e) {
        log.warn("transaction-service unreachable", e);
        return new DownstreamUnavailableException("transaction-service", "Transaction service is temporarily unavailable", e);
    }
}
