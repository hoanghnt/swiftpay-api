package com.hoanghnt.swiftpay.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.dto.response.InternalUserSummary;
import com.hoanghnt.swiftpay.dto.response.InternalUserView;
import com.hoanghnt.swiftpay.dto.response.PageResponse;
import com.hoanghnt.swiftpay.service.AdminUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Tag(name = "Internal Users", description = "Service-to-service admin user management")
public class InternalUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "List users (admin, via monolith)")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<InternalUserView>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(BaseResponse.success(PageResponse.from(adminUserService.list(search, pageable))));
    }

    @Operation(summary = "User summary (admin, via monolith)")
    @GetMapping("/summary")
    public ResponseEntity<BaseResponse<InternalUserSummary>> summary() {
        return ResponseEntity.ok(BaseResponse.success(adminUserService.summary()));
    }

    @Operation(summary = "Get user by id (admin, via monolith)")
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<InternalUserView>> getById(@PathVariable UUID id) {
        return adminUserService.getById(id)
                .map(v -> ResponseEntity.ok(BaseResponse.success(v)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        new BaseResponse<InternalUserView>(false, "User not found", null, "RES_001", LocalDateTime.now())));
    }

    @Operation(summary = "Enable user (admin, via monolith)")
    @PostMapping("/{id}/enable")
    public ResponseEntity<BaseResponse<Void>> enable(@PathVariable UUID id) {
        return adminUserService.enable(id) ? ResponseEntity.ok(BaseResponse.ok("User enabled successfully")) : notFound();
    }

    @Operation(summary = "Disable user (admin, via monolith)")
    @PostMapping("/{id}/disable")
    public ResponseEntity<BaseResponse<Void>> disable(@PathVariable UUID id) {
        return adminUserService.disable(id) ? ResponseEntity.ok(BaseResponse.ok("User disabled successfully")) : notFound();
    }

    @Operation(summary = "Unlock user (admin, via monolith)")
    @PostMapping("/{id}/unlock")
    public ResponseEntity<BaseResponse<Void>> unlock(@PathVariable UUID id) {
        return adminUserService.unlock(id) ? ResponseEntity.ok(BaseResponse.ok("User unlocked successfully")) : notFound();
    }

    private ResponseEntity<BaseResponse<Void>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(BaseResponse.error("RES_001", "User not found"));
    }
}
