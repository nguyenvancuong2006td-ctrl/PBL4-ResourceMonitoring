package com.pbl4.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authInterceptor;

    /**
     * Danh sách origin được phép kết nối WebSocket, phân cách bằng dấu phẩy.
     * Dùng chung khóa cấu hình với ScreenStreamWebSocketConfig.
     *
     * Mặc định chỉ cho phép localhost. Khi triển khai, thêm vào
     * application.properties, ví dụ:
     *   app.ws.allowed-origins=https://ten-mien-frontend
     */
    @Value("${app.ws.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private String[] allowedOrigins;

    public WebSocketConfig(
            WebSocketAuthChannelInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * Cấu hình Message Broker.
     */
    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry) {

        // Các client gửi message đến server thông qua /app
        registry.setApplicationDestinationPrefixes("/app");

        // Server gửi message đến các client thông qua /topic
        registry.enableSimpleBroker("/topic");

        // Định tuyến message riêng cho từng người dùng
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Đăng ký WebSocket Endpoint.
     */
    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry) {

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins);
    }

    /**
     * Đăng ký Interceptor để xác thực STOMP.
     */
    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration) {

        registration.interceptors(authInterceptor);
    }
}