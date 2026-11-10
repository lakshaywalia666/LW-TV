package com.zerotv.next;

import java.util.Arrays;

public final class IndianAqiTest {
    private static final long HOUR = 3_600_000L;
    private static final long NOW = 1_794_196_800_000L;
    private static int checks;

    public static void main(String[] args) {
        // CPCB examples use PM2.5 = 45 -> 75 and PM2.5 = 150 -> 323.
        expect(75, 0, 45);
        expect(323, 0, 150);
        expect(50, 0, 30);
        expect(100, 0, 60);
        expect(200, 0, 90);
        expect(300, 0, 120);
        expect(400, 0, 250);
        expect(500, 0, 1000);
        expect(100, 1, 100);
        expect(100, 2, 80);
        expect(100, 3, 80);
        expect(100, 4, 2000); // CO is returned in µg/m³, converted to mg/m³.
        expect(200, 5, 168);
        double[][] concentrations = samples(25);
        Arrays.fill(concentrations[0], 150);
        IndianAqi.Reading detailed = calculate(concentrations);
        equal(150, (int) detailed.averages[0], "Retain the PM2.5 mean for the details view");
        equal(0, detailed.dominantPollutant, "Identify the pollutant supplying the largest index");
        Arrays.fill(concentrations[4], 2000);
        equal(2, (int) calculate(concentrations).averages[4], "Details report CO in mg/m³");

        double[][] data = samples(25);
        Arrays.fill(data[0], 60);
        data[0][23] = 120;
        data[0][24] = 10_000; // Future forecast must not contribute.
        equal(108, calculate(data).index, "Use a 24-hour mean and exclude future samples");

        data = samples(25);
        Arrays.fill(data[4], 100_000);
        Arrays.fill(data[4], 16, 24, 2000);
        equal(100, calculate(data).index, "CO uses the latest eight hours");

        data = samples(25);
        data[5][23] = 748;
        equal(400, calculate(data).index, "High ozone uses the one-hour breakpoint");

        data = samples(25);
        Arrays.fill(data[0], 90);
        Arrays.fill(data[1], 350);
        equal(300, calculate(data).index, "Use the largest pollutant sub-index");

        data = samples(25);
        for (int pollutant = 2; pollutant < data.length; pollutant++) {
            Arrays.fill(data[pollutant], Double.NaN);
        }
        missing(calculate(data), "Require at least three valid pollutants");

        data = samples(25);
        Arrays.fill(data[0], Double.NaN);
        Arrays.fill(data[1], Double.NaN);
        missing(calculate(data), "Require at least one particulate pollutant");

        data = samples(25);
        for (int pollutant = 0; pollutant < data.length; pollutant++) {
            Arrays.fill(data[pollutant], 0, 9, Double.NaN);
        }
        missing(calculate(data), "Fifteen hourly samples are insufficient");

        data = samples(25);
        for (int pollutant = 0; pollutant < data.length; pollutant++) {
            Arrays.fill(data[pollutant], 0, 8, Double.NaN);
        }
        equal(0, calculate(data).index, "Sixteen hourly samples can produce an index");

        data = samples(25);
        Arrays.fill(data[0], Double.POSITIVE_INFINITY);
        Arrays.fill(data[1], -1);
        missing(calculate(data), "Invalid concentrations cannot supply the particulate requirement");

        long[] times = times(24);
        missing(IndianAqi.calculate(times, samples(24), NOW + 4 * HOUR), "Reject outdated series");
        times[1] = times[0];
        try {
            IndianAqi.calculate(times, samples(24), NOW);
            throw new AssertionError("Duplicate hours must not inflate coverage");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        System.out.println("Passed " + checks + " Indian AQI checks.");
    }

    private static void expect(int index, int pollutant, double concentration) {
        double[][] data = samples(25);
        Arrays.fill(data[pollutant], concentration);
        equal(index, calculate(data).index, "Pollutant " + pollutant + " at " + concentration);
    }

    private static IndianAqi.Reading calculate(double[][] data) {
        return IndianAqi.calculate(times(data[0].length), data, NOW + HOUR / 2);
    }

    private static long[] times(int count) {
        long[] result = new long[count];
        for (int i = 0; i < count; i++) result[i] = NOW - (23L - i) * HOUR;
        return result;
    }

    private static double[][] samples(int count) {
        return new double[IndianAqi.POLLUTANTS.length][count];
    }

    private static void equal(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        checks++;
    }

    private static void missing(IndianAqi.Reading result, String message) {
        if (result != null) throw new AssertionError(message);
        checks++;
    }
}
