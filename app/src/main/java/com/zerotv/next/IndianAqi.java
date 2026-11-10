package com.zerotv.next;

final class IndianAqi {
    static final String[] POLLUTANTS = {
            "pm2_5", "pm10", "nitrogen_dioxide", "sulphur_dioxide", "carbon_monoxide", "ozone"
    };
    private static final int[] AVERAGING_HOURS = {24, 24, 24, 24, 8, 8};
    private static final int[] INDEX_BREAKPOINTS = {0, 50, 100, 200, 300, 400, 500};
    // CPCB concentration breakpoints in µg/m³, except CO in mg/m³.
    // Extend the final finite interval into Severe, capped at the 0–500 scale.
    private static final double[][] CONCENTRATION_BREAKPOINTS = {
            {0, 30, 60, 90, 120, 250, 380},
            {0, 50, 100, 250, 350, 430, 510},
            {0, 40, 80, 180, 280, 400, 520},
            {0, 40, 80, 380, 800, 1600, 2400},
            {0, 1, 2, 10, 17, 34, 51},
            {0, 50, 100, 168, 208, 748, 1288}
    };
    private static final long HOUR_MS = 60 * 60 * 1000L;

    private IndianAqi() {}

    static Reading calculate(long[] times, double[][] concentrations, long now) {
        if (concentrations.length != POLLUTANTS.length) {
            throw new IllegalArgumentException("Unexpected pollutant count");
        }
        long observedAt = 0;
        for (int i = 0; i < times.length; i++) {
            if (times[i] % HOUR_MS != 0 || (i > 0 && times[i] <= times[i - 1])) {
                throw new IllegalArgumentException("Expected distinct, ordered hourly samples");
            }
            if (times[i] <= now) observedAt = times[i];
        }
        if (observedAt == 0 || now - observedAt > 3 * HOUR_MS) return null;

        double worst = 0;
        double[] averages = new double[POLLUTANTS.length];
        java.util.Arrays.fill(averages, Double.NaN);
        int dominantPollutant = -1;
        int available = 0;
        boolean hasParticulates = false;
        for (int pollutant = 0; pollutant < POLLUTANTS.length; pollutant++) {
            double[] samples = concentrations[pollutant];
            if (samples.length != times.length) {
                throw new IllegalArgumentException("Pollutant samples do not match timestamps");
            }
            double average = average(times, samples, AVERAGING_HOURS[pollutant], observedAt);
            if (Double.isNaN(average)) continue;
            available++;
            if (pollutant < 2) hasParticulates = true;
            // Open-Meteo returns CO in µg/m³. CPCB uses mg/m³ for CO.
            if (pollutant == 4) average /= 1000;
            averages[pollutant] = average;
            double subIndex = interpolate(average, CONCENTRATION_BREAKPOINTS[pollutant]);
            if (pollutant == 5) {
                // CPCB uses one-hour ozone concentrations above the Poor category.
                double current = average(times, samples, 1, observedAt);
                subIndex = Math.min(300, subIndex);
                if (current > 208) {
                    subIndex = Math.max(subIndex, interpolate(current, CONCENTRATION_BREAKPOINTS[pollutant]));
                }
            }
            if (subIndex >= worst) {
                worst = subIndex;
                dominantPollutant = pollutant;
            }
        }
        return available < 3 || !hasParticulates ? null
                : new Reading((int) Math.round(worst), observedAt, averages, dominantPollutant);
    }

    private static double average(long[] times, double[] samples, int hours, long end) {
        double sum = 0;
        int count = 0;
        long latest = 0;
        for (int i = 0; i < times.length; i++) {
            double value = samples[i];
            if (times[i] <= end - hours * HOUR_MS || times[i] > end
                    || Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
                continue;
            }
            sum += value;
            count++;
            latest = times[i];
        }
        int required = hours == 24 ? 16 : hours;
        return count < required || end - latest >= 3 * HOUR_MS ? Double.NaN : sum / count;
    }

    private static double interpolate(double concentration, double[] breakpoints) {
        for (int band = 1; band < breakpoints.length; band++) {
            if (concentration <= breakpoints[band]) {
                double fraction = (concentration - breakpoints[band - 1])
                        / (breakpoints[band] - breakpoints[band - 1]);
                return INDEX_BREAKPOINTS[band - 1]
                        + fraction * (INDEX_BREAKPOINTS[band] - INDEX_BREAKPOINTS[band - 1]);
            }
        }
        return 500;
    }

    static final class Reading {
        final int index;
        final long observedAt;
        final double[] averages;
        final int dominantPollutant;

        Reading(int index, long observedAt, double[] averages, int dominantPollutant) {
            this.index = index;
            this.observedAt = observedAt;
            this.averages = averages;
            this.dominantPollutant = dominantPollutant;
        }
    }
}
