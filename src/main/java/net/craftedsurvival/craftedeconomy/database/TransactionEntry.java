package net.craftedsurvival.craftedeconomy.database;

import java.util.UUID;

/**
 * A single immutable row from the {@code ce_transactions} table.
 *
 * @param id            auto-increment primary key
 * @param uuid          the target player's UUID
 * @param type          one of {@link TransactionType}
 * @param amount        the amount involved (always positive)
 * @param balanceBefore the player's balance immediately before the transaction
 * @param balanceAfter  the player's balance immediately after the transaction
 * @param actor         the other party / admin / "self" — may be {@code null}
 * @param note          optional free-text note — may be {@code null}
 * @param timestamp     Unix epoch milliseconds
 */
public record TransactionEntry(
        long id,
        UUID uuid,
        String type,
        double amount,
        double balanceBefore,
        double balanceAfter,
        String actor,
        String note,
        long timestamp
) {}
