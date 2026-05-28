package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.gui.util.TextFormattingUtil;
import com.lowdragmc.lowdraglib.side.fluid.FluidHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

@Mixin(TextFormattingUtil.class)
public abstract class TextFormattingUtilMixin {
    private static final NavigableMap<Long, String> MBD2TOOLS_SUFFIXES_BUCKET = new TreeMap<>();

    static {
        MBD2TOOLS_SUFFIXES_BUCKET.put(1L, "m");
        MBD2TOOLS_SUFFIXES_BUCKET.put(1_000L, "");
        MBD2TOOLS_SUFFIXES_BUCKET.put(1_000_000L, "k");
        MBD2TOOLS_SUFFIXES_BUCKET.put(1_000_000_000L, "M");
        MBD2TOOLS_SUFFIXES_BUCKET.put(1_000_000_000_000L, "G");
        MBD2TOOLS_SUFFIXES_BUCKET.put(1_000_000_000_000_000L, "T");
        MBD2TOOLS_SUFFIXES_BUCKET.put(1_000_000_000_000_000_000L, "P");
    }

    /**
     * @author
     * @reason Prevent long overflow when formatting large fluid amounts for GUI rendering.
     */
    @Overwrite(remap = false)
    public static String formatLongToCompactStringBuckets(long value, int precision) {
        if (value == 0L) {
            return "0";
        }

        long bucket = Math.max(1L, FluidHelper.getBucket());
        double milliBuckets = (double) value * 1000.0D / (double) bucket;
        if (Math.abs(milliBuckets) < 1.0D) {
            return new DecimalFormat("0.####").format(milliBuckets) + "m";
        }

        if (milliBuckets < 0.0D) {
            return "-" + formatLongToCompactStringBuckets(-value, precision);
        }

        if (milliBuckets < Math.pow(10, precision)) {
            long rounded = Math.round(milliBuckets);
            Map.Entry<Long, String> suffixEntry = MBD2TOOLS_SUFFIXES_BUCKET.floorEntry(Math.max(1L, rounded));
            return rounded + suffixEntry.getValue();
        }

        Map.Entry<Long, String> entry = MBD2TOOLS_SUFFIXES_BUCKET.floorEntry((long) milliBuckets);
        long divideBy = entry.getKey();
        String suffix = entry.getValue();

        double truncated = milliBuckets / (divideBy / 10.0D);
        long roundedTruncated = Math.round(truncated);
        boolean hasDecimal = roundedTruncated < 100L && Math.abs(truncated - Math.floor(truncated)) > 1.0E-9D;
        return hasDecimal ? (roundedTruncated / 10.0D) + suffix : (roundedTruncated / 10L) + suffix;
    }
}
