package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class ProcessTreeTerminator {

    private ProcessTreeTerminator() {
    }

    static void terminate(Process process, Duration gracePeriod) {
        if (process == null) {
            return;
        }
        List<ProcessHandle> descendants = process.descendants()
                .sorted(Comparator.comparingLong(ProcessHandle::pid).reversed())
                .toList();
        descendants.forEach(ProcessTreeTerminator::destroy);
        destroy(process.toHandle());
        waitUntilDead(process, descendants, gracePeriod);
        descendants.forEach(ProcessTreeTerminator::destroyForcibly);
        destroyForcibly(process.toHandle());
        if (isWindows() && process.isAlive()) {
            taskkill(process.pid());
        }
    }

    private static void waitUntilDead(
            Process process,
            List<ProcessHandle> descendants,
            Duration gracePeriod
    ) {
        long deadline = System.nanoTime() + gracePeriod.toNanos();
        while (System.nanoTime() < deadline
                && (process.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive))) {
            try {
                TimeUnit.MILLISECONDS.sleep(25);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void destroy(ProcessHandle process) {
        if (process.isAlive()) {
            process.destroy();
        }
    }

    private static void destroyForcibly(ProcessHandle process) {
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void taskkill(long pid) {
        try {
            Process killer = new ProcessBuilder(
                    "taskkill",
                    "/PID",
                    Long.toString(pid),
                    "/T",
                    "/F")
                    .redirectErrorStream(true)
                    .start();
            killer.getOutputStream().close();
            killer.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // ProcessHandle remains the portable best-effort termination path.
        }
    }
}
