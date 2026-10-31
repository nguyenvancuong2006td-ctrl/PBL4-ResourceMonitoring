package pbl4.agent.registration;

public enum RegistrationState {

    // Agent chưa gửi yêu cầu đăng ký
    NOT_REGISTERED,

    // Đã gửi yêu cầu, đang chờ Admin phê duyệt
    PENDING,

    // Admin đã phê duyệt, đang nhận Credential
    APPROVED,

    // Yêu cầu đăng ký bị Admin từ chối
    REJECTED,

    // Yêu cầu đăng ký đã hết hạn
    EXPIRED,

    // Agent đã nhận Credential và hoàn tất đăng ký
    REGISTERED,

    // Xảy ra lỗi trong quá trình đăng ký
    ERROR
}