package com.pbl4.server.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationCode(
            String email,
            String code) {

        System.out.println("========== SEND OTP ==========");
        System.out.println("Email nhận: " + email);
        System.out.println("OTP: " + code);

        SimpleMailMessage message =
                new SimpleMailMessage();

        // Email người nhận
        message.setTo(email);

        // Tiêu đề
        message.setSubject(
                "[PBL4 Monitor] Mã xác thực tài khoản"
        );

        // Nội dung email
        message.setText(
                "Xin chào,\n\n"
                + "Bạn vừa thực hiện yêu cầu đăng ký tài khoản "
                + "trên hệ thống PBL4 Monitor.\n\n"

                + "MÃ XÁC THỰC (OTP): " + code + "\n\n"

                + "Mã xác thực có hiệu lực trong 5 phút.\n"
                + "Vui lòng không cung cấp mã này cho bất kỳ ai.\n\n"

                + "Nếu bạn không thực hiện yêu cầu này, "
                + "vui lòng bỏ qua email.\n\n"

                + "Trân trọng,\n"
                + "PBL4 Monitor System"
        );

        // Gửi email
        mailSender.send(message);

        System.out.println("========== EMAIL SENT ==========");
    }
}