package com.non_coffee.mbd2thread.energy.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class EnergyFormatUtil {
    private static final long[] POW_1000 = {
            1L,
            1_000L,
            1_000_000L,
            1_000_000_000L,
            1_000_000_000_000L,
            1_000_000_000_000_000L,
            1_000_000_000_000_000_000L
    };

    private static final char[] SUFFIX = {' ', 'K', 'M', 'G', 'T', 'P', 'E'};

    private EnergyFormatUtil() {
    }

    public static String formatEnergy(long value) {
        return formatEnergy(value, 2);
    }

    public static String formatEnergy(long value, int decimals) {
        if (value == Long.MIN_VALUE) return "-9.22E18";
        boolean negative = value < 0;
        long abs = Math.abs(value);
        if (abs < 1_000L) return (negative ? "-" : "") + abs;

        int tier = 0;
        for (int i = POW_1000.length - 1; i >= 1; i--) {
            if (abs >= POW_1000[i]) {
                tier = i;
                break;
            }
        }

        BigDecimal scaled = BigDecimal.valueOf(abs).divide(BigDecimal.valueOf(POW_1000[tier]), decimals, RoundingMode.HALF_UP);
        String s = scaled.stripTrailingZeros().toPlainString();
        char suffix = SUFFIX[Math.min(tier, SUFFIX.length - 1)];
        return (negative ? "-" : "") + s + suffix;
    }

    public static long parseEnergy(String raw) {
        if (raw == null) throw new NumberFormatException("null");
        String s = raw.trim();
        if (s.isEmpty()) throw new NumberFormatException("empty");

        s = s.replace("_", "").replace(",", "");
        boolean negative = s.startsWith("-");
        if (negative) s = s.substring(1).trim();
        if (s.isEmpty()) throw new NumberFormatException("empty");

        char last = s.charAt(s.length() - 1);
        int tier = suffixTier(last);
        String numberPart = tier == 0 ? s : s.substring(0, s.length() - 1).trim();
        if (numberPart.isEmpty()) throw new NumberFormatException("empty");

        BigDecimal num = new BigDecimal(numberPart);
        if (num.signum() < 0) throw new NumberFormatException("negative");
        BigDecimal scaled = num.multiply(BigDecimal.valueOf(POW_1000[tier]));
        BigDecimal rounded = scaled.setScale(0, RoundingMode.HALF_UP);
        if (rounded.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) throw new NumberFormatException("overflow");
        long v = rounded.longValueExact();
        return negative ? -v : v;
    }

    private static int suffixTier(char suffix) {
        char c = Character.toUpperCase(suffix);
        return switch (c) {
            case 'K' -> 1;
            case 'M' -> 2;
            case 'G' -> 3;
            case 'T' -> 4;
            case 'P' -> 5;
            case 'E' -> 6;
            default -> 0;
        };
    }
}
