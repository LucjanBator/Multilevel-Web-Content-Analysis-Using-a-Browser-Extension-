package utils;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

public class CPUMonitor {
    private static final OperatingSystemMXBean osBean =
            ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

    private long lastProcessCpuTime = 0;
    private long lastSystemTime = 0;

    /**
     * Pobiera aktualne zużycie CPU dla procesu JVM w procentach
     */
    public double getProcessCpuUsage() {
        try {
            long processCpuTime = osBean.getProcessCpuTime();
            long systemTime = System.nanoTime();

            if (lastProcessCpuTime == 0) {
                lastProcessCpuTime = processCpuTime;
                lastSystemTime = systemTime;
                return 0.0;
            }

            long processCpuTimeDiff = processCpuTime - lastProcessCpuTime;
            long systemTimeDiff = systemTime - lastSystemTime;

            int availableProcessors = osBean.getAvailableProcessors();

            double cpuUsage = (double) processCpuTimeDiff / (systemTimeDiff * availableProcessors);
            cpuUsage = Math.min(1.0, Math.max(0.0, cpuUsage));

            lastProcessCpuTime = processCpuTime;
            lastSystemTime = systemTime;

            return cpuUsage * 100.0;

        } catch (Exception e) {
            System.err.println("Błąd pomiaru CPU: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Pobiera systemowe zużycie CPU
     */
    public double getSystemCpuUsage() {
        try {
            return osBean.getSystemCpuLoad() * 100.0;
        } catch (Exception e) {
            System.err.println("Błąd pomiaru systemowego CPU: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Mierzy zużycie CPU podczas wykonywania zadania
     */
    public CPUMetrics measureCpuDuringTask(Runnable task) {
        double cpuBefore = getProcessCpuUsage();
        double systemCpuBefore = getSystemCpuUsage();

        long startTime = System.nanoTime();
        task.run();
        long endTime = System.nanoTime();

        try { Thread.sleep(100); } catch (InterruptedException e) { }

        double cpuAfter = getProcessCpuUsage();
        double systemCpuAfter = getSystemCpuUsage();

        long taskDuration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        return new CPUMetrics(
                Math.max(0, cpuAfter - cpuBefore),
                Math.max(0, systemCpuAfter - systemCpuBefore),
                taskDuration
        );
    }

    public record CPUMetrics(double processCpuUsage, double systemCpuUsage, long durationMs) {}
}