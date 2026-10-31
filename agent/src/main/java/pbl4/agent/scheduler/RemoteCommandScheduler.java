package pbl4.agent.scheduler;

import pbl4.agent.client.ServerClient;
import pbl4.agent.model.RemoteCommandData;
import pbl4.agent.service.RemoteCommandExecutor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoteCommandScheduler implements AutoCloseable {

    private static final long INITIAL_DELAY_SECONDS = 5;
    private static final long POLLING_INTERVAL_SECONDS = 5;

    private final ServerClient serverClient;
    private final RemoteCommandExecutor commandExecutor;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "remote-command-scheduler"
                );

                thread.setDaemon(true);

                return thread;
            });

    /*
     * Ngăn nhiều lần polling chạy đồng thời.
     */
    private final AtomicBoolean polling =
            new AtomicBoolean(false);

    /*
     * Bảo đảm start() chỉ đăng ký tác vụ polling một lần.
     */
    private final AtomicBoolean started =
            new AtomicBoolean(false);

    /*
     * Đánh dấu các lệnh đã được Server xác nhận RUNNING
     * trong vòng đời của Agent hiện tại.
     *
     * Không tự động thực thi lại các lệnh này nếu việc
     * báo kết quả về Server gặp lỗi.
     *
     * Đây là bộ nhớ tạm, không chống trùng qua lần
     * khởi động lại Agent.
     */
    private final Set<Long> processingCommands =
            ConcurrentHashMap.newKeySet();

    public RemoteCommandScheduler(
            ServerClient serverClient,
            RemoteCommandExecutor commandExecutor) {

        if (serverClient == null) {
            throw new IllegalArgumentException(
                    "ServerClient không được null."
            );
        }

        if (commandExecutor == null) {
            throw new IllegalArgumentException(
                    "RemoteCommandExecutor không được null."
            );
        }

        this.serverClient = serverClient;
        this.commandExecutor = commandExecutor;
    }

    /**
     * Khởi động quá trình lấy lệnh từ Server.
     */
    public void start() {

        if (scheduler.isShutdown()) {
            throw new IllegalStateException(
                    "Remote Command Scheduler đã dừng."
            );
        }

        if (!started.compareAndSet(false, true)) {
            System.out.println(
                    "Remote Command Scheduler đã được khởi động."
            );
            return;
        }

        try {
            scheduler.scheduleWithFixedDelay(
                    this::pollCommandsSafely,
                    INITIAL_DELAY_SECONDS,
                    POLLING_INTERVAL_SECONDS,
                    TimeUnit.SECONDS
            );

            System.out.println(
                    "Remote Command Scheduler đã khởi động. "
                            + "Chu kỳ polling: "
                            + POLLING_INTERVAL_SECONDS
                            + " giây."
            );

        } catch (RuntimeException e) {
            started.set(false);

            throw new IllegalStateException(
                    "Không thể khởi động Remote Command Scheduler.",
                    e
            );
        }
    }

    /**
     * Bao ngoài tác vụ polling để một lỗi thông thường
     * không làm dừng các lần polling tiếp theo.
     */
    private void pollCommandsSafely() {

        try {
            pollCommands();

        } catch (Exception e) {
            System.err.println(
                    "[REMOTE] Lỗi polling: "
                            + safeMessage(e)
            );
        }
    }

    /**
     * Lấy danh sách lệnh đang chờ từ Server.
     */
    private void pollCommands() {

        if (!polling.compareAndSet(false, true)) {
            return;
        }

        try {
            List<RemoteCommandData> commands =
                    serverClient.getPendingCommands();

            if (commands == null || commands.isEmpty()) {
                return;
            }

            for (RemoteCommandData command : commands) {

                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                if (!isValidCommand(command)) {
                    System.err.println(
                            "[REMOTE] Bỏ qua lệnh không hợp lệ."
                    );
                    continue;
                }

                /*
                 * Mỗi lệnh được xử lý độc lập.
                 * Một lệnh lỗi không ngăn các lệnh khác
                 * trong cùng danh sách được kiểm tra.
                 */
                try {
                    processCommand(command);

                } catch (Exception e) {
                    System.err.println(
                            "[REMOTE] Lỗi xử lý commandId="
                                    + command.getCommandId()
                                    + ": "
                                    + safeMessage(e)
                    );
                }
            }

        } finally {
            polling.set(false);
        }
    }

    /**
     * Kiểm tra dữ liệu lệnh tối thiểu.
     */
    private boolean isValidCommand(RemoteCommandData command) {

        return command != null
                && command.getCommandId() != null
                && command.getCommandId() > 0
                && command.getCommandType() != null
                && !command.getCommandType().isBlank();
    }

    /**
     * Xử lý một lệnh Remote.
     */
    private void processCommand(RemoteCommandData command) {

        Long commandId = command.getCommandId();

        /*
         * Nếu lệnh đã được thực thi hoặc đã được đánh dấu
         * xử lý trong phiên hiện tại thì không chạy lại.
         */
        if (processingCommands.contains(commandId)) {
            return;
        }

        /*
         * Chưa thực thi lệnh cho đến khi Server xác nhận
         * chuyển trạng thái sang RUNNING.
         */
        boolean markedRunning =
                serverClient.markCommandRunning(commandId);

        if (!markedRunning) {
            /*
             * Chưa biết chắc Server có chấp nhận chuyển trạng
             * thái hay không. Không thực thi lệnh.
             *
             * Không giữ ID trong Set ở trường hợp này:
             * lần polling sau có thể kiểm tra lại với Server.
             */
            System.err.println(
                    "[REMOTE] Không xác nhận được trạng thái RUNNING "
                            + "cho commandId=" + commandId
                            + ". Không thực thi lệnh."
            );

            return;
        }

        /*
         * Chỉ ghi nhận sau khi Server xác nhận RUNNING.
         */
        if (!processingCommands.add(commandId)) {
            System.err.println(
                    "[REMOTE] commandId=" + commandId
                            + " đã được xử lý trong phiên hiện tại."
            );
            return;
        }

        System.out.println(
                "[REMOTE] Bắt đầu thực thi commandId="
                        + commandId
                        + ", type="
                        + command.getCommandType()
        );

        try {
            /*
             * Thực thi lệnh trên máy Client đang chạy Agent.
             */
            String result = commandExecutor.execute(command);

            /*
             * Gửi kết quả thành công về Server.
             */
            boolean reported =
                    serverClient.reportCommandResult(
                            commandId,
                            true,
                            result,
                            null
                    );

            if (!reported) {
                /*
                 * Không chạy lại lệnh.
                 * Kết quả có thể đã được Server lưu nhưng
                 * phản hồi xác nhận bị mất.
                 */
                System.err.println(
                        "[REMOTE] Chưa nhận được xác nhận kết quả "
                                + "từ Server cho commandId="
                                + commandId
                                + ". Không thực thi lại."
                );

                return;
            }

            System.out.println(
                    "[REMOTE] Server đã xác nhận kết quả "
                            + "commandId=" + commandId
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            System.err.println(
                    "[REMOTE] Tác vụ commandId="
                            + commandId
                            + " bị gián đoạn."
            );

            reportFailure(
                    commandId,
                    "Tác vụ Agent bị gián đoạn. "
                            + "Cần kiểm tra trạng thái thực tế "
                            + "nếu đây là lệnh Shutdown hoặc Restart."
            );

        } catch (Exception e) {

            String errorMessage = safeMessage(e);

            System.err.println(
                    "[REMOTE] Thực thi commandId="
                            + commandId
                            + " gặp lỗi: "
                            + errorMessage
            );

            /*
             * Báo lỗi về Server.
             *
             * Với lệnh Shutdown/Restart bị timeout, trạng thái
             * thực tế có thể chưa xác định. Server không nên
             * hiểu FAILED là bằng chứng chắc chắn máy chưa
             * thực hiện thao tác.
             */
            reportFailure(
                    commandId,
                    errorMessage
            );
        }

        /*
         * Cố ý không xóa commandId khỏi processingCommands.
         *
         * Nếu việc báo kết quả thất bại, không tự động
         * thực thi lại lệnh nguy hiểm trong phiên này.
         */
    }

    /**
     * Báo kết quả thất bại về Server.
     */
    private void reportFailure(
            Long commandId,
            String errorMessage) {

        try {
            boolean reported =
                    serverClient.reportCommandResult(
                            commandId,
                            false,
                            null,
                            errorMessage
                    );

            if (reported) {
                System.out.println(
                        "[REMOTE] Server đã xác nhận kết quả lỗi "
                                + "commandId=" + commandId
                );

            } else {
                System.err.println(
                        "[REMOTE] Chưa xác nhận được kết quả lỗi "
                                + "commandId=" + commandId
                                + ". Không tự động chạy lại lệnh."
                );
            }

        } catch (Exception e) {
            System.err.println(
                    "[REMOTE] Không thể báo lỗi commandId="
                            + commandId
                            + ": "
                            + safeMessage(e)
            );
        }
    }

    /**
     * Lấy thông báo lỗi an toàn để ghi log.
     */
    private String safeMessage(Exception e) {

        String message = e.getMessage();

        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }

        return message;
    }

    /**
     * Dừng quá trình polling.
     */
    public void stop() {

        if (scheduler.isShutdown()) {
            return;
        }

        scheduler.shutdownNow();

        System.out.println(
                "[REMOTE] Remote Command Scheduler đã dừng."
        );
    }

    /**
     * Cho phép đóng Scheduler bằng try-with-resources
     * nếu kiến trúc khởi tạo Agent cần sử dụng cách này.
     */
    @Override
    public void close() {
        stop();
    }
}