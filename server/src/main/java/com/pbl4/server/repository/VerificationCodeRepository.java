package com.pbl4.server.repository;

import com.pbl4.server.enums.VerificationStatus;
import com.pbl4.server.enums.VerificationType;
import com.pbl4.server.model.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VerificationCodeRepository
        extends JpaRepository<VerificationCode, Long> {

    // Mã mới nhất của một loại xác thực
    // (EMAIL_VERIFICATION và PASSWORD_RESET không dùng lẫn của nhau)
    Optional<VerificationCode>
    findTopByAccountAccountIdAndVerificationTypeOrderByCreatedAtDesc(
            Long accountId,
            VerificationType verificationType
    );

    // Các mã còn hiệu lực, dùng để hủy khi phát hành mã mới
    List<VerificationCode>
    findByAccountAccountIdAndVerificationTypeAndStatus(
            Long accountId,
            VerificationType verificationType,
            VerificationStatus status
    );

    // Đếm số mã đã gửi trong một khoảng thời gian (giới hạn gửi lại)
    long countByAccountAccountIdAndVerificationTypeAndCreatedAtAfter(
            Long accountId,
            VerificationType verificationType,
            LocalDateTime after
    );
}