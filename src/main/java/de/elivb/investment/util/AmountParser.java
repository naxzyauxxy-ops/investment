package de.elivb.investment.util;

import java.util.List;

/**
 * Parses monetary amounts from strings, supporting abbreviations.
 *
 * Supported abbreviations (case-insensitive, configurable):
 *   K  = 1,000
 *   M  = 1,000,000
 *   B  = 1,000,000,000
 *   T  = 1,000,000,000,000
 *
 * Examples:  "1k" → 1000.0,  "2.5M" → 2500000.0,  "all" → special sentinel
 */
public class AmountParser {

    /** Sentinel returned when the player typed "all" or "max". */
    public static final double ALL = -1.0;

    private AmountParser() {}

    /**
     * Parse an amount string using the provided abbreviation list.
     *
     * @param input         raw player input (e.g. "1.5m", "500K", "all")
     * @param abbrevFormats list of suffix strings from config (e.g. ["K","M","B","T"])
     * @param useAbbrev     whether abbreviations are enabled in config
     * @return parsed double value, or {@code ALL} sentinel, or {@code null} if invalid
     */
    public static Double parse(String input, List<String> abbrevFormats, boolean useAbbrev) {
        if (input == null || input.isBlank()) return null;

        String s = input.trim();

        // "all" / "max" returns the sentinel so callers can use max available
        if (s.equalsIgnoreCase("all") || s.equalsIgnoreCase("max")) {
            return ALL;
        }

        if (useAbbrev && abbrevFormats != null && !abbrevFormats.isEmpty()) {
            String upper = s.toUpperCase();
            double multiplier = 1.0;
            boolean matched = false;

            // Default suffix → multiplier map (order matters — check longer first if needed)
            double[] multipliers = {1_000.0, 1_000_000.0, 1_000_000_000.0, 1_000_000_000_000.0};

            for (int i = 0; i < abbrevFormats.size(); i++) {
                String suffix = abbrevFormats.get(i).toUpperCase();
                if (upper.endsWith(suffix)) {
                    multiplier = (i < multipliers.length) ? multipliers[i] : Math.pow(1000, i + 1);
                    s = upper.substring(0, upper.length() - suffix.length()).trim();
                    matched = true;
                    break;
                }
            }

            if (matched || !upper.equals(s.toUpperCase())) {
                try {
                    return Double.parseDouble(s) * multiplier;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        // Plain number fallback
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Format a double into an abbreviated string for display.
     * e.g. 1500000 → "$1.5M"
     */
    public static String format(double value, java.text.DecimalFormat fmt) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000_000.0) return "$" + fmt.format(value / 1_000_000_000_000.0) + "T";
        if (abs >= 1_000_000_000.0)     return "$" + fmt.format(value / 1_000_000_000.0) + "B";
        if (abs >= 1_000_000.0)         return "$" + fmt.format(value / 1_000_000.0) + "M";
        if (abs >= 1_000.0)             return "$" + fmt.format(value / 1_000.0) + "K";
        return "$" + fmt.format(value);
    }
}
