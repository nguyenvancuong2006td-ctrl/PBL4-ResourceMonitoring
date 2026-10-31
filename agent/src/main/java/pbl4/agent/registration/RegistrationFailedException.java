package pbl4.agent.registration;

/**
 * Đăng ký thất bại và Agent không thể tự khắc phục
 * (ví dụ Admin từ chối, Server từ chối thông tin đăng ký).
 *
 * Khi gặp lỗi này Agent dừng lại và báo cho người vận hành.
 */
public class RegistrationFailedException extends Exception {

    private static final long serialVersionUID = 1L;

    public RegistrationFailedException(String message) {
        super(message);
    }

    public RegistrationFailedException(
            String message,
            Throwable cause) {

        super(message, cause);
    }
}