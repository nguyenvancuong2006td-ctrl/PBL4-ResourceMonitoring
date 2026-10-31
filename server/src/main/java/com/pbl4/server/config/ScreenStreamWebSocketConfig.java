package com.pbl4.server.config;

import com.pbl4.server.websocket.ScreenStreamWebSocketHandler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Đăng ký endpoint WebSocket thuần /ws/screen (song song với STOMP /ws).
 */
@Configuration
@EnableWebSocket
public class ScreenStreamWebSocketConfig implements WebSocketConfigurer {

    // Khung hình JPEG tối đa 8 MB (khớp MAX_FRAME_BYTES của Agent)
    private static final int MAX_BINARY_MESSAGE_BYTES = 8 * 1024 * 1024 + 1024;

    private static final int MAX_TEXT_MESSAGE_BYTES = 64 * 1024;

    private final ScreenStreamWebSocketHandler handler;

    public ScreenStreamWebSocketConfig(ScreenStreamWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        // Agent (Java) không gửi header Origin nên không bị chặn;
        // trình duyệt của Admin phải đến từ các origin dưới đây.
        registry.addHandler(handler, "/ws/screen")
                .setAllowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*");
    }

    /**
     * Tomcat mặc định chỉ nhận tin nhắn nhị phân ~8 KB, nhỏ hơn nhiều so với
     * một khung hình JPEG. Không tăng giới hạn này thì kết nối bị đóng với
     * mã 1009 ngay khung hình đầu tiên.
     */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {

        ServletServerContainerFactoryBean container =
                new ServletServerContainerFactoryBean();

        container.setMaxBinaryMessageBufferSize(MAX_BINARY_MESSAGE_BYTES);
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BYTES);

        return container;
    }
}