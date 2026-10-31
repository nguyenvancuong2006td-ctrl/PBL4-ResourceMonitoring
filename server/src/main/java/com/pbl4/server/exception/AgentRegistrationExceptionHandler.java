package com.pbl4.server.exception;

import com.pbl4.server.controller.AgentRegistrationController;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Chuyển lỗi nghiệp vụ của luồng đăng ký Agent thành mã HTTP đúng nghĩa.
 *
 * Trước đây IllegalArgumentException / IllegalStateException không được
 * xử lý nên Agent nhận HTTP 500 cho mọi lỗi và không phân biệt được
 * "Server đang lỗi tạm thời" với "yêu cầu đăng ký không còn hợp lệ".
 *
 *   - 400: dữ liệu không hợp lệ (Registration Token sai, thiếu thông tin...)
 *   - 409: trạng thái không cho phép (yêu cầu hết hạn, Credential không
 *          còn hoạt động...)
 *   - 503: nhiều Agent đăng ký cùng lúc và trùng ID, Agent sẽ thử lại
 *   - 5xx: lỗi thật sự của Server (Agent sẽ thử lại)
 *
 * Chỉ áp dụng cho AgentRegistrationController, không ảnh hưởng
 * các controller khác.
 */
@RestControllerAdvice(assignableTypes = AgentRegistrationController.class)
public class AgentRegistrationExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException e) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", messageOf(e)));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(
            IllegalStateException e) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", messageOf(e)));
    }

    /**
     * Hai Agent đăng ký đúng lúc nhau nhận trùng số thứ tự ID.
     * Transaction này đã rollback, Agent chỉ cần thử lại.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleConcurrentRegistration(
            DataIntegrityViolationException e) {

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "message",
                        "Hệ thống đang xử lý nhiều yêu cầu đăng ký "
                                + "cùng lúc. Vui lòng thử lại."
                ));
    }

    private String messageOf(RuntimeException e) {

        String message = e.getMessage();

        return message == null || message.isBlank()
                ? "Yêu cầu đăng ký Agent không hợp lệ."
                : message;
    }
}