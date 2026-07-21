package com.hoanghnt.swiftpay.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.hoanghnt.swiftpay.client.dto.UserSummaryView;
import com.hoanghnt.swiftpay.client.dto.UserView;
import com.hoanghnt.swiftpay.config.AuthServiceProperties;
import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.dto.response.PageResponse;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class UserAdminClient {

    private final RestClient restClient;

    public UserAdminClient(AuthServiceProperties props, RestClient.Builder restClientBuilder) {
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

    public PageResponse<UserView> listUsers(String search, int page, int size) {
        try {
            BaseResponse<PageResponse<UserView>> resp = restClient.get()
                    .uri(uri -> {
                        var b = uri.path("/internal/users").queryParam("page", page).queryParam("size", size);
                        if (search != null && !search.isBlank()) {
                            b.queryParam("search", search);
                        }
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<BaseResponse<PageResponse<UserView>>>() {});
            return resp != null && resp.data() != null ? resp.data()
                    : new PageResponse<>(List.of(), page, size, 0, 0, true);
        } catch (ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    @CircuitBreaker(name = "authService")
    public Optional<UserView> getUser(UUID id) {
        try {
            BaseResponse<UserView> resp = restClient.get()
                    .uri("/internal/users/{id}", id)
                    .retrieve()
                    .body(new ParameterizedTypeReference<BaseResponse<UserView>>() {});
            return Optional.ofNullable(resp != null ? resp.data() : null);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    @CircuitBreaker(name = "authService")
    public UserSummaryView summary() {
        try {
            BaseResponse<UserSummaryView> resp = restClient.get()
                    .uri("/internal/users/summary")
                    .retrieve()
                    .body(new ParameterizedTypeReference<BaseResponse<UserSummaryView>>() {});
            return resp != null && resp.data() != null ? resp.data() : new UserSummaryView(0, 0, 0);
        } catch (ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    public void enable(UUID id) {
        patch("/internal/users/" + id + "/enable");
    }

    public void disable(UUID id) {
        patch("/internal/users/" + id + "/disable");
    }

    public void unlock(UUID id) {
        patch("/internal/users/" + id + "/unlock");
    }

    private void patch(String uri) {
        try {
            restClient.post().uri(uri).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        } catch (ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    private DownstreamUnavailableException unavailable(Exception e) {
        log.warn("auth-service unreachable", e);
        return new DownstreamUnavailableException("auth-service", "Auth service is temporarily unavailable", e);
    }
}
