package com.hoanghnt.swiftpay.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoanghnt.swiftpay.audit.AuditEventRepository;
import com.hoanghnt.swiftpay.dto.response.AuditEventResponse;
import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.dto.response.PageResponse;
import com.hoanghnt.swiftpay.security.AuthPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit log lookup (own history, or any user's history for admins)")
public class AuditController {

    private final AuditEventRepository auditEventRepository;

    @Operation(summary = "Get audit log for a user", description = "Paginated audit log for any user (ADMIN only)")
    @ApiResponse(responseCode = "200", description = "Audit log retrieved")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<AuditEventResponse>>> getAuditLog(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("occurredAt").descending());
        Page<AuditEventResponse> result = auditEventRepository
                .findByActorUserIdOrderByOccurredAtDesc(userId, pageable)
                .map(AuditEventResponse::from);
        return ResponseEntity.ok(BaseResponse.success(PageResponse.from(result)));
    }

    @Operation(summary = "Get my audit log", description = "Paginated audit log for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Audit log retrieved")
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<PageResponse<AuditEventResponse>>> getMyAuditLog(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = AuthPrincipal.userId(authentication);
        Pageable pageable = PageRequest.of(page, size, Sort.by("occurredAt").descending());
        Page<AuditEventResponse> result = auditEventRepository
                .findByActorUserIdOrderByOccurredAtDesc(userId, pageable)
                .map(AuditEventResponse::from);
        return ResponseEntity.ok(BaseResponse.success(PageResponse.from(result)));
    }
}
