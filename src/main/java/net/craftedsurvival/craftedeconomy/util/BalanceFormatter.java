package net.craftedsurvival.craftedeconomy.util;

import net.craftedsurvival.craftedeconomy.CraftedEconomy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class BalanceFormatter {

    private final DecimalFormat format;
    private final DecimalFormat placeholderFormat;

    public BalanceFormatter(CraftedEconomy plugin) {
        String pattern = plugin.getConfig().getString("currency.balance-format", "#,##0.00");
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        this.format = new DecimalFormat(pattern, symbols);

        // Placeholder display: max 2 decimals, trailing zeros (and a bare decimal point)
        // stripped. Preserve thousands grouping only if the configured format uses it.
        String placeholderPattern = pattern.contains(",") ? "#,##0.##" : "0.##";
        this.placeholderFormat = new DecimalFormat(placeholderPattern, symbols);
    }

    /** Fixed-format display used by in-chat command messages (e.g. {@code 10.00}). */
    public String format(double amount) {
        return format.format(amount);
    }

    /**
     * Placeholder display: rounds half-up to a maximum of 2 decimal places and strips
     * any trailing zeros (and the decimal point itself when nothing meaningful remains),
     * while preserving integer-side grouping. Examples: {@code 10.00 -> 10},
     * {@code 10.20 -> 10.2}, {@code 10.215 -> 10.22}, {@code 0.00 -> 0}.
     *
     * <p>This only affects the rendered string — stored balances, API return values,
     * and calculations are unaffected.</p>
     */
    public String formatPlaceholder(double amount) {
        // Round on a BigDecimal built from the value's decimal representation so that
        // half-up rounding of values like 10.215 behaves as written, free of binary
        // floating-point drift.
        BigDecimal rounded = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        return placeholderFormat.format(rounded);
    }
}
