package net.craftedsurvival.craftedeconomy.util;

import net.craftedsurvival.craftedeconomy.CraftedEconomy;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class BalanceFormatter {

    private final DecimalFormat format;

    public BalanceFormatter(CraftedEconomy plugin) {
        String pattern = plugin.getConfig().getString("currency.balance-format", "#,##0.00");
        this.format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
    }

    public String format(double amount) {
        return format.format(amount);
    }
}
