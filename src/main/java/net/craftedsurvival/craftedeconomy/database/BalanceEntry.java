package net.craftedsurvival.craftedeconomy.database;

import java.util.UUID;

public record BalanceEntry(UUID uuid, String playerName, double balance) {}
