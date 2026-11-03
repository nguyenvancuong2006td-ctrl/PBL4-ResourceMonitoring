package com.pbl4.server.exception;

import com.pbl4.server.controller.AccountController;
import com.pbl4.server.controller.AuthController;
import com.pbl4.server.controller.VerificationController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Chuyển lỗi của luồng đăng ký / xác thực email / quên mật khẩu
 * thành mã HTTP đúng nghĩa thay vì HTTP 500 cho mọi lỗi.
 *
 *   - 400: dữ liệu không hợp lệ, mã OTP sai / hết hạn
 *   - 409: username hoặc email đã tồn tại, email đã xác thực
 *   - 429: yêu cầu mã OTP quá nhanh hoặc quá nhiều lần
 *   - 503: không gửi được email
 *
 * Chỉ áp dụng cho AccountController, AuthController và
 * VerificationController.
 */
@RestControllerAdvice(assignableTypes = {
        AccountController.class,
        AuthController.class,
        VerificationController.class
})
public class AuthExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(AuthExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(
            ApiException e) {

        return body(e.getStatus(), e.getMessage());
    }

    /** Dữ liệu JSON không qua được @Valid. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException e) {

        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("Dữ liệu không hợp lệ");

        return body(HttpStatus.BAD_REQUEST, message);
    }

    /** JSON sai cú pháp hoặc thiếu body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(
            HttpMessageNotReadableException e) {

        return body(
                HttpStatus.BAD_REQUEST,
                "Dữ liệu gửi lên không hợp lệ"
        );
    }

    /** Thiếu @RequestParam (các endpoint /api/verification/**). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParam(
            MissingServletRequestParameterException e) {

        return body(
                HttpStatus.BAD_REQUEST,
                "Thiếu tham số: " + e.getParameterName()
        );
    }

    /**
     * Hai request đăng ký cùng lúc với cùng username/email:
     * ràng buộc UNIQUE của database chặn request đến sau.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(
            DataIntegrityViolationException e) {

        return body(
                HttpStatus.CONFLICT,
                "Username hoặc email đã được sử dụng"
        );
    }

    /**
     * SMTP lỗi (sai cấu hình Gmail, mất mạng...).
     *
     * Ghi log đầy đủ nguyên nhân gốc để biết lỗi thật là gì
     * (sai App Password -> AuthenticationFailedException 535,
     * mạng chặn cổng 587 -> MailConnectException / timeout...).
     * Người dùng chỉ nhận thông báo chung, không lộ chi tiết nội bộ.
     */
    @ExceptionHandler(MailException.class)
    public ResponseEntity<Map<String, String>> handleMail(
            MailException e) {

        log.error("Gửi email thất bại", e);

        return body(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Không thể gửi email lúc này, vui lòng thử lại sau"
        );
    }

    private ResponseEntity<Map<String, String>> body(
            HttpStatus status,
            String message) {

        return ResponseEntity
                .status(status)
                .body(Map.of("message", message));
    }
}