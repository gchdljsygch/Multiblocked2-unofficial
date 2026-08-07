package com.lowdragmc.mbd2.performance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Shared timing and reporting support for opt-in MBD runtime stress tests.
 */
public final class StressTestSupport {

    public static final int MACHINE_COUNT = Integer.getInteger("mbd2.stress.machineCount", 100_001);
    private static final Path REPORT_DIRECTORY = Path.of(System.getProperty(
            "mbd2.stress.reportDirectory", "build/reports/mbd2-stress"));
    private static final Map<String, Measurement> MEASUREMENTS = new TreeMap<>();

    private StressTestSupport() {
    }

    public static void requireStressScale() {
        if (MACHINE_COUNT <= 100_000 && !Boolean.getBoolean("mbd2.stress.allowBelow100k")) {
            throw new IllegalStateException("Stress tests require more than 100,000 machines; configured " + MACHINE_COUNT);
        }
    }

    public static Measurement measure(String scenario, int rounds, long operations, Runnable work) {
        return measure(scenario, MACHINE_COUNT, rounds, operations, work);
    }

    public static Measurement measure(String scenario, int machines, int rounds, long operations, Runnable work) {
        long started = System.nanoTime();
        work.run();
        long elapsedNanos = System.nanoTime() - started;
        var measurement = new Measurement(scenario, machines, rounds, operations, elapsedNanos);
        record(measurement);
        return measurement;
    }

    public static void record(Measurement measurement) {
        synchronized (MEASUREMENTS) {
            MEASUREMENTS.put(measurement.scenario(), measurement);
            writeReports();
        }
        System.out.printf(Locale.ROOT,
                "[MBD2-STRESS] %-34s machines=%d rounds=%d operations=%d elapsed=%.2f ms avgRound=%.2f ms throughput=%.0f ops/s ns/op=%.1f%n",
                measurement.scenario(), measurement.machines(), measurement.rounds(), measurement.operations(),
                measurement.elapsedMillis(), measurement.averageRoundMillis(), measurement.operationsPerSecond(),
                measurement.nanosecondsPerOperation());
    }

    private static void writeReports() {
        try {
            Files.createDirectories(REPORT_DIRECTORY);
            Files.writeString(REPORT_DIRECTORY.resolve("summary.csv"), csvReport(), StandardCharsets.UTF_8);
            Files.writeString(REPORT_DIRECTORY.resolve("summary.md"), markdownReport(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write MBD stress report to " + REPORT_DIRECTORY, exception);
        }
    }

    private static String csvReport() {
        var report = new StringBuilder("scenario,machines,rounds,operations,elapsed_ms,average_round_ms,operations_per_second,nanoseconds_per_operation\n");
        for (Measurement measurement : MEASUREMENTS.values()) {
            report.append(String.format(Locale.ROOT, "%s,%d,%d,%d,%.3f,%.3f,%.3f,%.3f%n",
                    measurement.scenario(), measurement.machines(), measurement.rounds(), measurement.operations(),
                    measurement.elapsedMillis(), measurement.averageRoundMillis(), measurement.operationsPerSecond(),
                    measurement.nanosecondsPerOperation()));
        }
        return report.toString();
    }

    private static String markdownReport() {
        var report = new StringBuilder("# MBD2 Stress Results\n\n")
                .append("| Scenario | Machines | Rounds | Operations | Elapsed (ms) | Avg. round (ms) | Throughput (ops/s) | ns/op |\n")
                .append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (Measurement measurement : MEASUREMENTS.values()) {
            report.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %.3f | %.3f | %.0f | %.1f |%n",
                    measurement.scenario(), measurement.machines(), measurement.rounds(), measurement.operations(),
                    measurement.elapsedMillis(), measurement.averageRoundMillis(), measurement.operationsPerSecond(),
                    measurement.nanosecondsPerOperation()));
        }
        return report.toString();
    }

    public record Measurement(String scenario, int machines, int rounds, long operations, long elapsedNanos) {

        public double elapsedMillis() {
            return elapsedNanos / 1_000_000.0;
        }

        public double averageRoundMillis() {
            return elapsedMillis() / rounds;
        }

        public double operationsPerSecond() {
            return operations * 1_000_000_000.0 / elapsedNanos;
        }

        public double nanosecondsPerOperation() {
            return elapsedNanos / (double) operations;
        }
    }
}
