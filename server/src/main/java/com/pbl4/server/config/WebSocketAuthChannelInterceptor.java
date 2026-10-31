package com.pbl4.server.config;

import com.pbl4.server.enums.AccountStatus;
import com.pbl4.server.model.Account;
import com.pbl4.server.model.ClientAgent;
import com.pbl4.server.model.Permission;
import com.pbl4.server.service.AccountDetailsService;
import com.pbl4.server.service.AgentCredentialService;
import com.pbl4.server.service.ScreenSharingWebSocketAccessService;
import com.pbl4.server.repository.ClientAgentRepository;
import com.pbl4.server.util.JwtUtil;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WebSocketAuthChannelInterceptor
        implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final AccountDetailsService accountDetailsService;
    private final AgentCredentialService agentCredentialService;
    private final ClientAgentRepository clientAgentRepository;
    private final ScreenSharingWebSocketAccessService accessService;

    public WebSocketAuthChannelInterceptor(
            JwtUtil jwtUtil,
            AccountDetailsService accountDetailsService,
            AgentCredentialService agentCredentialService,
            ClientAgentRepository clientAgentRepository,
            ScreenSharingWebSocketAccessService accessService) {

        this.jwtUtil = jwtUtil;
        this.accountDetailsService = accountDetailsService;
        this.agentCredentialService = agentCredentialService;
        this.clientAgentRepository = clientAgentRepository;
        this.accessService = accessService;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {

            authenticateConnection(message, accessor);

        } else if (command != null
                && !StompCommand.DISCONNECT.equals(command)) {

            if (accessor.getUser() == null) {
                throw new MessageDeliveryException(
                        "WebSocket chưa được xác thực."
                );
            }

            // Kiểm tra quyền truy cập khi đăng ký nhận tín hiệu
            if (StompCommand.SUBSCRIBE.equals(command)) {
                authorizeSubscribe(accessor);
            }

            // Kiểm tra quyền truy cập khi gửi tín hiệu
            if (StompCommand.SEND.equals(command)) {
                authorizeSend(accessor);
            }
        }

        return message;
    }

    /**
     * Xác thực Admin hoặc Agent khi kết nối STOMP.
     */
    private void authenticateConnection(
            Message<?> message,
            StompHeaderAccessor accessor) {

        String authorization =
                accessor.getFirstNativeHeader("Authorization");

        String agentId =
                accessor.getFirstNativeHeader("X-Agent-Id");

        String agentCredential =
                accessor.getFirstNativeHeader("X-Agent-Credential");

        if (authorization != null
                && authorization.startsWith("Bearer ")) {

            authenticateAdmin(message, accessor, authorization);
            return;
        }

        if (agentId != null && agentCredential != null) {

            authenticateAgent(
                    message,
                    accessor,
                    agentId,
                    agentCredential
            );

            return;
        }

        throw new MessageDeliveryException(
                "Thiếu thông tin xác thực WebSocket."
        );
    }

    /**
     * Xác thực Admin bằng JWT.
     */
    private void authenticateAdmin(
            Message<?> message,
            StompHeaderAccessor accessor,
            String authorization) {

        String token = authorization.substring(7).trim();

        if (token.isEmpty()) {
            throw new MessageDeliveryException(
                    "JWT không được để trống."
            );
        }

        try {
            if (!jwtUtil.isTokenValid(token)) {
                throw new MessageDeliveryException(
                        "JWT không hợp lệ hoặc đã hết hạn."
                );
            }

            String username = jwtUtil.extractUsername(token);

            if (username == null || username.isBlank()) {
                throw new MessageDeliveryException(
                        "JWT không chứa username."
                );
            }

            Account account =
                    accountDetailsService.getAccountByUsername(username);

            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new MessageDeliveryException(
                        "Tài khoản chưa được kích hoạt hoặc đã bị khóa."
                );
            }

            if (!Boolean.TRUE.equals(account.getEmailVerified())) {
                throw new MessageDeliveryException(
                        "Email của tài khoản chưa được xác thực."
                );
            }

            if (account.getRole() == null
                    || account.getRole().getRoleName() == null) {

                throw new MessageDeliveryException(
                        "Tài khoản chưa được gán vai trò."
                );
            }

            if (!"ADMIN".equals(
                    account.getRole().getRoleName().name())) {

                throw new MessageDeliveryException(
                        "Chỉ Admin mới được phép kết nối chia sẻ màn hình."
                );
            }

            List<SimpleGrantedAuthority> authorities =
                    new ArrayList<>();

            authorities.add(
                    new SimpleGrantedAuthority(
                            "ROLE_" + account.getRole()
                                    .getRoleName()
                                    .name()
                    )
            );

            if (account.getRole().getPermissions() != null) {

                for (Permission permission :
                        account.getRole().getPermissions()) {

                    if (permission == null
                            || permission.getPermissionCode() == null) {
                        continue;
                    }

                    authorities.add(
                            new SimpleGrantedAuthority(
                                    permission.getPermissionCode().name()
                            )
                    );
                }
            }

            boolean hasRemoteCommandPermission =
                    authorities.stream()
                            .anyMatch(authority ->
                                    "REMOTE_COMMAND".equals(
                                            authority.getAuthority()
                                    )
                            );

            if (!hasRemoteCommandPermission) {
                throw new MessageDeliveryException(
                        "Admin không có quyền REMOTE_COMMAND."
                );
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            authorities
                    );

            accessor.setUser(authentication);

        } catch (MessageDeliveryException e) {
            throw e;

        } catch (Exception e) {
            throw new MessageDeliveryException(
                    "Không thể xác thực Admin: " + e.getMessage()
            );
        }
    }

    /**
     * Xác thực Agent bằng Agent ID và Credential.
     */
    private void authenticateAgent(
            Message<?> message,
            StompHeaderAccessor accessor,
            String agentId,
            String agentCredential) {

        try {
            boolean valid =
                    agentCredentialService.validateCredential(
                            agentId,
                            agentCredential
                    );

            if (!valid) {
                throw new MessageDeliveryException(
                        "Agent Credential không hợp lệ hoặc đã hết hạn."
                );
            }

            ClientAgent agent =
                    clientAgentRepository.findById(agentId)
                            .orElseThrow(() ->
                                    new MessageDeliveryException(
                                            "Không tìm thấy Agent."
                                    )
                            );

            List<SimpleGrantedAuthority> authorities =
                    List.of(
                            new SimpleGrantedAuthority("ROLE_AGENT")
                    );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            "AGENT:" + agent.getAgentId(),
                            null,
                            authorities
                    );

            accessor.setUser(authentication);

        } catch (MessageDeliveryException e) {
            throw e;

        } catch (Exception e) {
            throw new MessageDeliveryException(
                    "Không thể xác thực Agent: " + e.getMessage()
            );
        }
    }

    /**
     * Kiểm tra quyền SUBSCRIBE vào topic của phiên chia sẻ.
     *
     * Định dạng:
     * /topic/screen-sharing/{sessionCode}
     */
    private void authorizeSubscribe(
            StompHeaderAccessor accessor) {

        String destination = accessor.getDestination();

        String sessionCode = extractSessionCode(
                destination,
                "/topic/screen-sharing/"
        );

        checkSessionAccess(accessor, sessionCode);
    }

    /**
     * Kiểm tra quyền SEND tín hiệu vào phiên chia sẻ.
     *
     * Định dạng:
     * /app/screen-sharing/{sessionCode}/signal
     */
    private void authorizeSend(
            StompHeaderAccessor accessor) {

        String destination = accessor.getDestination();

        String prefix = "/app/screen-sharing/";
        String suffix = "/signal";

        if (destination == null
                || !destination.startsWith(prefix)
                || !destination.endsWith(suffix)) {

            throw new MessageDeliveryException(
                    "Destination gửi tín hiệu không hợp lệ."
            );
        }

        String sessionCode = destination.substring(
                prefix.length(),
                destination.length() - suffix.length()
        );

        if (sessionCode.isBlank() || sessionCode.contains("/")) {
            throw new MessageDeliveryException(
                    "Session code không hợp lệ."
            );
        }

        checkSessionAccess(accessor, sessionCode);
    }

    /**
     * Lấy sessionCode từ destination.
     */
    private String extractSessionCode(
            String destination,
            String prefix) {

        if (destination == null
                || !destination.startsWith(prefix)) {

            throw new MessageDeliveryException(
                    "Destination đăng ký nhận tín hiệu không hợp lệ."
            );
        }

        String sessionCode =
                destination.substring(prefix.length());

        if (sessionCode.isBlank() || sessionCode.contains("/")) {
            throw new MessageDeliveryException(
                    "Session code không hợp lệ."
            );
        }

        return sessionCode;
    }

    /**
     * Kiểm tra người gửi có thuộc phiên chia sẻ hay không.
     */
    private void checkSessionAccess(
            StompHeaderAccessor accessor,
            String sessionCode) {

        if (!(accessor.getUser() instanceof org.springframework.security.core.Authentication authentication)
        || !accessService.hasAccess(authentication, sessionCode)) {
            throw new MessageDeliveryException(
                    "Bạn không có quyền truy cập phiên chia sẻ màn hình này."
            );
        }
    }
}