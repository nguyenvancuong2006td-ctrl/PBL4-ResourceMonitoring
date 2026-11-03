package com.pbl4.server.controller;

import com.pbl4.server.dto.request.StartScreenSharingRequest;
import com.pbl4.server.dto.response.PageResponse;
import com.pbl4.server.dto.response.ScreenSharingHistoryResponse;
import com.pbl4.server.dto.response.ScreenSharingSessionResponse;
import com.pbl4.server.enums.ScreenSharingStatus;
import com.pbl4.server.model.Account;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.service.ScreenSharingSessionService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/screen-sharing/sessions")
public class ScreenSharingSessionController {

    private final ScreenSharingSessionService sessionService;
    private final AccountRepository accountRepository;

    public ScreenSharingSessionController(
            ScreenSharingSessionService sessionService,
            AccountRepository accountRepository) {

        this.sessionService = sessionService;
        this.accountRepository = accountRepository;
    }

    /**
     * Admin yêu cầu bắt đầu chia sẻ màn hình.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('REMOTE_COMMAND')")
    public ResponseEntity<?> startSession(
            @Valid @RequestBody StartScreenSharingRequest request,
            Authentication authentication) {

        try {
            Account account = getAuthenticatedAccount(authentication);

            ScreenSharingSessionResponse response =
                    sessionService.startSession(
                            account.getAccountId(),
                            request.getClientId()
                    );

            return ResponseEntity.accepted().body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of("message", e.getMessage())
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("message", "Không thể bắt đầu chia sẻ màn hình.")
            );
        }
    }

    /**
     * Admin yêu cầu dừng chia sẻ màn hình.
     */
    @PostMapping("/{sessionCode}/stop")
    @PreAuthorize("hasAuthority('REMOTE_COMMAND')")
    public ResponseEntity<?> stopSession(
            @PathVariable String sessionCode,
            Authentication authentication) {

        try {
            Account account = getAuthenticatedAccount(authentication);

            ScreenSharingSessionResponse response =
                    sessionService.stopSession(
                            account.getAccountId(),
                            sessionCode
                    );

            return ResponseEntity.accepted().body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("message", e.getMessage())
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("message", "Không thể dừng phiên chia sẻ.")
            );
        }
    }

    /**
     * Lấy phiên chia sẻ hiện tại của Admin đăng nhập.
     */
    @GetMapping("/current")
    @PreAuthorize("hasAuthority('REMOTE_COMMAND')")
    public ResponseEntity<?> getCurrentSession(
            Authentication authentication) {

        try {
            Account account = getAuthenticatedAccount(authentication);

            ScreenSharingSessionResponse response =
                    sessionService.getCurrentSession(
                            account.getAccountId()
                    );

            if (response == null) {
                return ResponseEntity.noContent().build();
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("message", "Không thể lấy phiên chia sẻ hiện tại.")
            );
        }
    }

    /**
     * Lịch sử các phiên chia sẻ màn hình (mới nhất trước).
     *
     * GET /api/screen-sharing/sessions/history
     *     ?page=0&size=20&clientId=...&status=ENDED
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyAuthority('REMOTE_COMMAND','HISTORY_VIEW')")
    public ResponseEntity<PageResponse<ScreenSharingHistoryResponse>>
    getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) ScreenSharingStatus status) {

        return ResponseEntity.ok(
                sessionService.getHistory(page, size, clientId, status)
        );
    }

    /**
     * Lấy tài khoản từ JWT Authentication.
     */
    private Account getAuthenticatedAccount(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {

            throw new SecurityException(
                    "Tài khoản chưa được xác thực."
            );
        }

        return accountRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new SecurityException(
                        "Không tìm thấy tài khoản đăng nhập."
                ));
    }
}