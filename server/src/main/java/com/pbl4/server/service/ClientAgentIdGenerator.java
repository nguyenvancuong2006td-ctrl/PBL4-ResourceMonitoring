package com.pbl4.server.service;

import com.pbl4.server.util.RegistrationIdUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sinh ID cho Client và Agent mới: CLIENT-001 / AGENT-001, ...
 *
 * Số thứ tự = (số lớn nhất đang có trong database) + 1, tính trên cả hai
 * bảng nên Client và Agent của một lần đăng ký luôn cùng số.
 *
 * Phải được gọi bên trong transaction đăng ký. Nếu hai Agent đăng ký cùng
 * lúc và nhận trùng số, khóa chính của database sẽ chặn lại, một yêu cầu
 * bị rollback và Agent tự thử lại sau (xem AgentRegistrationExceptionHandler).
 */
@Component
public class ClientAgentIdGenerator {

    public record GeneratedIds(String clientId, String agentId) {
    }

    @PersistenceContext
    private EntityManager entityManager;

    public GeneratedIds next() {

        List<String> clientIds = entityManager
                .createQuery(
                        "SELECT c.clientId FROM Client c "
                                + "WHERE c.clientId LIKE :prefix",
                        String.class
                )
                .setParameter(
                        "prefix",
                        RegistrationIdUtil.CLIENT_PREFIX + "%"
                )
                .getResultList();

        List<String> agentIds = entityManager
                .createQuery(
                        "SELECT a.agentId FROM ClientAgent a "
                                + "WHERE a.agentId LIKE :prefix",
                        String.class
                )
                .setParameter(
                        "prefix",
                        RegistrationIdUtil.AGENT_PREFIX + "%"
                )
                .getResultList();

        long next = Math.max(
                RegistrationIdUtil.highestNumber(
                        clientIds,
                        RegistrationIdUtil.CLIENT_PREFIX
                ),
                RegistrationIdUtil.highestNumber(
                        agentIds,
                        RegistrationIdUtil.AGENT_PREFIX
                )
        ) + 1;

        return new GeneratedIds(
                RegistrationIdUtil.format(
                        RegistrationIdUtil.CLIENT_PREFIX,
                        next
                ),
                RegistrationIdUtil.format(
                        RegistrationIdUtil.AGENT_PREFIX,
                        next
                )
        );
    }
}