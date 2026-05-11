package de.elivb.investment.util;

import java.util.List;

/**
 * Parses player amount input with abbreviation support.
 * K=1,000  M=1,000,000  B=1,000,000,000  T=1,000,000,000,000
 * "all" / "max" returns ALL sentinel so the caller uses maximum available.
 */
public final class AmountParser {

    /** Returned when player types "all" or "max". */
    public static final double ALL = Double.NEGATIVE_INFINITY;

    private AmountParser() {}

    /**
     * @param input         raw player input, e.g. "1.5m", "500K", "all"
     * @param abbrevFormats suffix list from config, e.g. ["K","M","B","T"]
     * @param useAbbrev     whether abbreviations are enabled
     * @return parsed value, {@link #ALL}, or {@code null} if invalid
     */
    public static Double parse(String input, List<String> abbrevFormats, boolean useAbbrev) {
        if (input == null || input.isBlank()) return null;
        String s = input.trim();

        if (s.equalsIgnoreCase("all") || s.equalsIgnoreCase("max")) return ALL;

        if (useAbbrev && abbrevFormats != null && !abbrevFormats.isEmpty()) {
            String upper = s.toUpperCase();
            double[] multipliers = {1_000.0, 1_000_000.0, 1_000_000_000.0, 1_000_000_000_000.0};
            for (int i = 0; i < abbrevFormats.size(); i++) {
                String suffix = abbrevFormats.get(i).toUpperCase();
                if (upper.endsWith(suffix)) {
                    double mult = (i < multipliers.length) ? multipliers[i] : Math.pow(1000, i + 1);
                    String num = upper.substring(0, upper.length() - suffix.length()).trim();
                    try { return Double.parseDouble(num) * mult; }
                    catch (NumberFormatException e) { return null; }
                }
            }
        }

        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return null; }
    }
}
