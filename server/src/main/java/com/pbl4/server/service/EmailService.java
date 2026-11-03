package com.pbl4.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log =
            LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    /**
     * Chỉ bật khi dev/test (không có SMTP): in OTP ra console.
     * Mặc định TẮT để OTP không lọt vào file log.
     * Bật bằng: app.mail.log-otp=true trong application.properties
     */
    @Value("${app.mail.log-otp:false}")
    private boolean logOtp;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ==============================
    // OTP XÁC THỰC EMAIL (ĐĂNG KÝ)
    // ==============================
    public void sendVerificationCode(
            String email,
            String code) {

        send(
                email,
                "[PBL4 Monitor] Mã xác thực tài khoản",
                "Xin chào,\n\n"
                + "Bạn vừa thực hiện yêu cầu đăng ký tài khoản "
                + "trên hệ thống PBL4 Monitor.\n\n"

                + "MÃ XÁC THỰC (OTP): " + code + "\n\n"

                + "Mã xác thực có hiệu lực trong 5 phút.\n"
                + "Vui lòng không cung cấp mã này cho bất kỳ ai.\n\n"

                + "Nếu bạn không thực hiện yêu cầu này, "
                + "vui lòng bỏ qua email.\n\n"

                + "Trân trọng,\n"
                + "PBL4 Monitor System",
                code
        );
    }

    // ==============================
    // OTP ĐẶT LẠI MẬT KHẨU
    // ==============================
    public void sendPasswordResetCode(
            String email,
            String code) {

        send(
                email,
                "[PBL4 Monitor] Mã đặt lại mật khẩu",
                "Xin chào,\n\n"
                + "Hệ thống PBL4 Monitor nhận được yêu cầu "
                + "đặt lại mật khẩu cho tài khoản liên kết "
                + "với email này.\n\n"

                + "MÃ ĐẶT LẠI MẬT KHẨU (OTP): " + code + "\n\n"

                + "Mã có hiệu lực trong 5 phút.\n"
                + "Vui lòng không cung cấp mã này cho bất kỳ ai.\n\n"

                + "Nếu bạn không yêu cầu đặt lại mật khẩu, "
                + "hãy bỏ qua email này, mật khẩu của bạn "
                + "vẫn được giữ nguyên.\n\n"

                + "Trân trọng,\n"
                + "PBL4 Monitor System",
                code
        );
    }

    private void send(
            String to,
            String subject,
            String text,
            String code) {

        if (logOtp) {
            log.warn("[DEV] OTP gửi tới {}: {}", to, code);
        }

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        mailSender.send(message);

        log.info("Đã gửi email '{}' tới {}", subject, to);
    }
}