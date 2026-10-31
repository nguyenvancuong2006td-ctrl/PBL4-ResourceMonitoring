package pbl4.agent.scheduler;

import pbl4.agent.registration.RegistrationFailedException;
import pbl4.agent.registration.RegistrationManager;
import pbl4.agent.registration.RegistrationManager.StepResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Poll trạng thái yêu cầu đăng ký theo chu kỳ cố định.
 *
 * Mỗi nhịp gọi RegistrationManager.processStep(). Khi đăng ký hoàn tất
 * hoặc thất bại dứt khoát, Scheduler tự dừng và báo kết quả qua
 * CompletableFuture trả về từ start().
 *
 * Chạy trên luồng daemon: không giữ JVM sống nếu Agent bị tắt khi
 * đang chờ phê duyệt.
 */
public class RegistrationScheduler implements AutoCloseable {

    private final RegistrationManager manager;
    private final long intervalSeconds;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "registration-scheduler"
                );

                thread.setDaemon(true);

                return thread;
            });

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final CompletableFuture<Void> completion =
            new CompletableFuture<>();

    public RegistrationScheduler(
            RegistrationManager manager,
            long intervalSeconds) {

        if (manager == null) {
            throw new IllegalArgumentException(
                    "RegistrationManager không được null."
            );
        }

        if (intervalSeconds < 1) {
            throw new IllegalArgumentException(
                    "Chu kỳ poll phải >= 1 giây."
            );
        }

        this.manager = manager;
        this.intervalSeconds = intervalSeconds;
    }

    /**
     * Bắt đầu poll (nhịp đầu tiên chạy ngay lập tức).
     *
     * @return hoàn tất khi đăng ký thành công; hoàn tất với
     *         RegistrationFailedException khi đăng ký thất bại
     */
    public CompletableFuture<Void> start() {

        if (!started.compareAndSet(false, true)) {
            return completion;
        }

        scheduler.scheduleWithFixedDelay(
                this::tick,
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );

        System.out.println(
                "[REGISTRATION] Registration Scheduler đã khởi động. "
                        + "Chu kỳ poll: " + intervalSeconds + " giây."
        );

        return completion;
    }

    private void tick() {

        if (completion.isDone()) {
            return;
        }

        try {
            StepResult result = manager.processStep();

            if (result == StepResult.COMPLETED) {
                completion.complete(null);
                scheduler.shutdown();

            } else if (result == StepResult.FAILED) {
                completion.completeExceptionally(
                        new RegistrationFailedException(
                                manager.getFailureReason()
                        )
                );
                scheduler.shutdown();
            }

        } catch (Throwable t) {
            // Một lỗi bất ngờ không được làm dừng các nhịp poll sau
            System.err.println(
                    "[REGISTRATION] Lỗi khi poll đăng ký: "
                            + t.getClass().getSimpleName()
                            + (t.getMessage() == null
                            ? ""
                            : ": " + t.getMessage())
            );
        }
    }

    public void stop() {

        if (scheduler.isShutdown()) {
            return;
        }

        scheduler.shutdownNow();
    }

    @Override
    public void close() {
        stop();
    }
}