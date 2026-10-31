package pbl4.agent.registration;

/**
 * Lỗi khi Agent giao tiếp với các API đăng ký của Server.
 *
 * retryable = true  : lỗi tạm thời (mất mạng, Server bận, HTTP 5xx...),
 *                     Agent nên thử lại ở chu kỳ sau.
 * retryable = false : Server đã từ chối yêu cầu (HTTP 4xx),
 *                     thử lại y nguyên sẽ không thay đổi kết quả.
 */
public class RegistrationException extends Exception {

    private static final long serialVersionUID = 1L;

    // 0 nếu không nhận được phản hồi HTTP
    private final int httpStatus;

    private final boolean retryable;

    public RegistrationException(
            String message,
            int httpStatus,
            boolean retryable,
            Throwable cause) {

        super(message, cause);

        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    /**
     * Lỗi không nhận được phản hồi từ Server (mạng, timeout, JSON hỏng).
     */
    public static RegistrationException transientError(
            String message,
            Throwable cause) {

        return new RegistrationException(
                message,
                0,
                true,
                cause
        );
    }

    /**
     * Lỗi dựa trên mã HTTP.
     * 5xx, 408, 425, 429 là lỗi tạm thời; các mã 4xx còn lại là lỗi dứt khoát.
     */
    public static RegistrationException fromHttp(
            int httpStatus,
            String message) {

        boolean retryable = httpStatus >= 500
                || httpStatus == 408
                || httpStatus == 425
                || httpStatus == 429;

        return new RegistrationException(
                message,
                httpStatus,
                retryable,
                null
        );
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }
}