package com.pbl4.server.dto.response;

/**
 * Số lượng cảnh báo theo trạng thái / mức độ, dùng cho huy hiệu và thẻ thống kê
 * mà không cần tải toàn bộ danh sách cảnh báo.
 */
public record AlertSummaryResponse(
        long total,
        long active,
        long acknowledged,
        long resolved,
        long open,
        long critical,
        long warning
) {
}