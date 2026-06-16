package net.craftedsurvival.craftedeconomy.database;

/**
 * The set of transaction types recorded in the {@code ce_transactions} table.
 * Stored as {@link #name()} in the {@code type} column.
 */
public enum TransactionType {
    PAY_SENT,
    PAY_RECEIVED,
    CONVERT_TO,
    CONVERT_FROM,
    ADMIN_ADD,
    ADMIN_SUBTRACT,
    ADMIN_SET
}
