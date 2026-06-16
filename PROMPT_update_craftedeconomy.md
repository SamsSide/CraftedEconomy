# CraftedEconomy — Update Prompt (Phase 2)

## Role & Context

You are an expert Paper/Spigot Minecraft plugin developer. You are working on an **existing plugin** called **CraftedEconomy** — do not rewrite it from scratch. Your task is to add the four features below to the existing codebase, then update `README.md` to reflect all changes.

Read this entire prompt before touching a single file. Make all changes in sequence. After every feature is complete, run `mvn clean package` and confirm it compiles before moving to the next.

---

## Overview of Changes

1. **`/admineconomy` command** — admin balance management (add / subtract / set)
2. **Transaction logging** — every balance change logged to a new DB table, queryable per player
3. **Tab completion** — all commands get full `TabCompleter` implementations
4. **README update** — document all new features, commands, permissions, placeholders, and schema changes

---

## Feature 1 — `/admineconomy` Command

### Command registration

Register a new command `/admineconomy` in `plugin.yml` with the alias `ae` (so `/ae` also works).

```yaml
admineconomy:
  description: Admin economy management commands.
  usage: /<command> <add|subtract|set> <player> <amount>
  permission: craftedeconomy.admin
  aliases: [ae]
```

### Subcommands

| Subcommand | Usage | Description |
|---|---|---|
| `add` | `/admineconomy add <player> <amount>` | Add `amount` to the target player's balance |
| `subtract` | `/admineconomy subtract <player> <amount>` | Subtract `amount` from the target player's balance (cannot go below 0) |
| `set` | `/admineconomy set <player> <amount>` | Set the target player's balance to exactly `amount` (must be ≥ 0) |

### Behaviour rules

- `<player>` accepts both **online** and **offline** player names. Look up UUID via `Bukkit.getOfflinePlayer(name)`. If the player has never joined the server (i.e. `OfflinePlayer.hasPlayedBefore()` returns false), send the configured `player-not-found` message and abort.
- `<amount>` must be a valid positive number. If not parseable or ≤ 0, send the configured `invalid-amount` message.
- For `subtract`: if the result would be below 0, clamp to 0 and proceed (do not reject). Send a separate message telling the admin the balance was clamped, e.g. `"Balance would go negative — clamped to 0."` — add this as a configurable message key.
- For `set`: if `amount` is negative, reject with the configured `invalid-amount` message.
- All database writes are **async** — never block the main thread.
- After any change, if the target player is **online**, send them a configurable notification message informing them their balance was modified by an admin (do not reveal which admin ran the command in the player-facing message).
- Send a confirmation message to the admin who ran the command.

### Permissions

| Node | Default | Description |
|---|---|---|
| `craftedeconomy.admin` | op | Parent — grants all admin subperms below |
| `craftedeconomy.admin.add` | op | Use `/admineconomy add` |
| `craftedeconomy.admin.subtract` | op | Use `/admineconomy subtract` |
| `craftedeconomy.admin.set` | op | Use `/admineconomy set` |

The `craftedeconomy.admin` parent node must implicitly grant all three child nodes via the `children` map in `plugin.yml`.

### Messages to add to config

Add the following keys under the existing `messages:` block in `config.yml`. All support `&` colour codes.

```yaml
messages:
  # ... existing keys ...
  admin-add-success: "&7Added &a{amount} {currency} &7to &e{player}&7's balance. New balance: &a{balance}&7."
  admin-subtract-success: "&7Subtracted &a{amount} {currency} &7from &e{player}&7's balance. New balance: &a{balance}&7."
  admin-subtract-clamped: "&eBalance would go negative — clamped to &a0&e. New balance: &a0 {currency}&e."
  admin-set-success: "&7Set &e{player}&7's balance to &a{amount} {currency}&7."
  admin-notify-player: "&7Your balance was updated by a server administrator. New balance: &a{balance} {currency}&7."
  admin-invalid-amount: "&cPlease enter a valid positive number."
  admin-player-not-found: "&cPlayer '{player}' has never joined this server."
```

---

## Feature 2 — Transaction Logging

### New database table

On plugin startup, auto-create the following table (using the configured `table-prefix`):

```sql
CREATE TABLE IF NOT EXISTS `{prefix}transactions` (
  `id`          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  `uuid`        VARCHAR(36)    NOT NULL,
  `type`        VARCHAR(32)    NOT NULL,
  `amount`      DECIMAL(18,4)  NOT NULL,
  `balance_before` DECIMAL(18,4) NOT NULL,
  `balance_after`  DECIMAL(18,4) NOT NULL,
  `actor`       VARCHAR(64)    DEFAULT NULL,
  `note`        VARCHAR(255)   DEFAULT NULL,
  `timestamp`   BIGINT         NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX IF NOT EXISTS `idx_transactions_uuid` ON `{prefix}transactions` (`uuid`);
CREATE INDEX IF NOT EXISTS `idx_transactions_timestamp` ON `{prefix}transactions` (`uuid`, `timestamp`);
```

### Column definitions

| Column | Description |
|---|---|
| `uuid` | The target player's UUID |
| `type` | One of: `PAY_SENT`, `PAY_RECEIVED`, `CONVERT_TO` (physical→virtual), `CONVERT_FROM` (virtual→physical), `ADMIN_ADD`, `ADMIN_SUBTRACT`, `ADMIN_SET` |
| `amount` | The amount involved in the transaction (always positive) |
| `balance_before` | The player's balance immediately before the transaction |
| `balance_after` | The player's balance immediately after |
| `actor` | For `PAY_SENT`/`PAY_RECEIVED`: the other player's name. For admin actions: the admin's name. For convert: `"self"`. Nullable. |
| `note` | Optional free-text note. Leave null unless there is something meaningful to store (e.g. conversion direction). |
| `timestamp` | Unix epoch milliseconds (`System.currentTimeMillis()`) |

### Where to log

Insert a transaction row **every time** a player's balance changes. This covers all of the following:

- `/pay` — log `PAY_SENT` for the sender and `PAY_RECEIVED` for the recipient (two separate rows)
- `/convert to` (physical → virtual) — log `CONVERT_TO` for the player
- `/convert from` (virtual → physical) — log `CONVERT_FROM` for the player
- `/admineconomy add` — log `ADMIN_ADD` for the target player
- `/admineconomy subtract` — log `ADMIN_SUBTRACT` for the target player
- `/admineconomy set` — log `ADMIN_SET` for the target player

All log inserts are **async**. A failure to insert a log row must never prevent the balance change from completing — log the exception as a `SEVERE` warning but do not roll back the economy operation.

### `/admineconomy logs` subcommand

Add a fourth subcommand to `/admineconomy`:

```
/admineconomy logs <player> [page]
```

- Requires permission: `craftedeconomy.admin.logs` (default: op, child of `craftedeconomy.admin`)
- Fetches the target player's transaction history from the DB, **sorted by timestamp descending** (most recent first)
- Paginates exactly like `/baltop`: 10 entries per page, clickable `[← Previous]` and `[Next →]` Adventure text components at the bottom
- Each line shows: `#<id> | <type> | <amount> | <balance_after> | <actor> | <timestamp>`
- Format the timestamp as `dd/MM/yyyy HH:mm` in the server's default timezone
- If no transactions exist for the player, send: `"&cNo transaction history found for &e{player}&c."`  — add this as a configurable message key: `logs-empty`
- Add the usage to `plugin.yml`:
  ```yaml
  admineconomy:
    usage: /<command> <add|subtract|set|logs> <player> [amount|page]
  ```

### Messages to add

```yaml
messages:
  logs-header: "&8&m-----&r &bTransaction Log &7— &e{player} &8&m-----"
  logs-entry: "&8#{id} &7| &b{type} &7| &a{amount} {currency} &7| Bal: &a{balance_after} &7| &e{actor} &7| &f{timestamp}"
  logs-empty: "&cNo transaction history found for &e{player}&c."
  logs-footer: "&7Page &f{page}&7/&f{total_pages}"
```

---

## Feature 3 — Tab Completion

Implement `TabCompleter` on **every** command registered by the plugin. Use Paper's `@Override onTabComplete` pattern (implement `TabExecutor` or register a separate `TabCompleter`). All completions must return a **filtered** list — only suggestions starting with what the player has already typed (case-insensitive prefix match).

### `/balance` (alias `/bal`)

| Argument position | Suggestions |
|---|---|
| arg 1 (player name) | Online player names — only if sender has `craftedeconomy.balance.others` |

### `/pay`

| Argument position | Suggestions |
|---|---|
| arg 1 (player name) | Online player names, excluding the sender |
| arg 2 (amount) | No suggestions (free-text number) |

### `/convert`

| Argument position | Suggestions |
|---|---|
| arg 1 (direction) | `["to", "from"]` |
| arg 2 (amount) | No suggestions |

### `/baltop`

| Argument position | Suggestions |
|---|---|
| arg 1 (page) | `["1", "2", "3"]` as hints |

### `/admineconomy` (alias `/ae`)

| Argument position | Suggestions |
|---|---|
| arg 1 (subcommand) | `["add", "subtract", "set", "logs"]` — filter by permission: only show subcommands the sender has permission for |
| arg 2 (player name) | All player names (online + recently-seen offline, using `Bukkit.getOnlinePlayers()` names) for `add`/`subtract`/`set`/`logs` |
| arg 3 (amount or page) | For `logs`: `["1", "2", "3"]` as page hints. For `add`/`subtract`/`set`: no suggestions. |

### General rules

- Never return `null` from `onTabComplete` — return an empty list instead.
- Do not suggest player names to senders who lack the relevant `others` permission.
- All completions are **filtered server-side** (prefix match) before being returned. Do not rely on the client to filter.

---

## Feature 4 — README Update

Rewrite `README.md` to include all changes from Phase 2 alongside the original Phase 1 content. The README must contain these sections in order:

1. **Overview** — what the plugin does, one paragraph
2. **Installation** — prerequisites (Paper 1.21+, Vault, MariaDB/MySQL, optional PAPI), jar drop, first-run setup
3. **Configuration** — annotated `config.yml` reference (all keys including new message keys)
4. **Commands** — full table of every command and subcommand, usage string, description, and required permission
5. **Permissions** — every permission node, its default, and what it grants (table format)
6. **Tab Completion** — short section noting that all commands support tab completion with a brief description of what each completes
7. **PlaceholderAPI Placeholders** — every placeholder, what it returns, caching notes (table format)
8. **Transaction Logging** — explain what is logged, the DB table schema, and how to query logs via `/admineconomy logs`
9. **Internal API** — short code example showing another plugin using `CraftedEconomyAPI`
10. **Exchange / Conversion System** — how `/convert to` and `/convert from` work, safety checks
11. **Database Schema** — both tables (`ce_balances` and `ce_transactions`) with column definitions
12. **Building from Source** — `mvn clean package`

---

## Permissions Reference (complete — Phase 1 + Phase 2)

Update `plugin.yml` so the full permissions block exactly matches this:

```yaml
permissions:
  craftedeconomy.balance:
    description: Use /balance on self.
    default: true
  craftedeconomy.balance.others:
    description: Use /balance <player> on others.
    default: op
  craftedeconomy.pay:
    description: Use /pay.
    default: true
  craftedeconomy.baltop:
    description: Use /baltop.
    default: true
  craftedeconomy.convert:
    description: Use /convert.
    default: true
  craftedeconomy.admin:
    description: Parent node — grants all admin subcommands.
    default: op
    children:
      craftedeconomy.admin.add: true
      craftedeconomy.admin.subtract: true
      craftedeconomy.admin.set: true
      craftedeconomy.admin.logs: true
  craftedeconomy.admin.add:
    description: Use /admineconomy add.
    default: op
  craftedeconomy.admin.subtract:
    description: Use /admineconomy subtract.
    default: op
  craftedeconomy.admin.set:
    description: Use /admineconomy set.
    default: op
  craftedeconomy.admin.logs:
    description: Use /admineconomy logs.
    default: op
```

---

## PlaceholderAPI Placeholders (complete — Phase 1 + Phase 2)

All Phase 1 placeholders remain unchanged. Add the following new ones:

| Placeholder | Returns | Notes |
|---|---|---|
| `%craftedeconomy_transactions_count%` | Total number of transactions for the requesting player | Cached for 60 seconds |

All other Phase 1 placeholders (`%craftedeconomy_balance%`, `%craftedeconomy_balance_formatted%`, `%craftedeconomy_rank%`, `%craftedeconomy_baltop_1_name%` through `%craftedeconomy_baltop_10_name%`, `%craftedeconomy_baltop_1_balance%` through `%craftedeconomy_baltop_10_balance%`) must remain registered and working.

---

## Final Checklist

Before finishing, verify every item below is true:

- [ ] Plugin compiles cleanly with `mvn clean package` — no warnings treated as errors, no missing dependencies
- [ ] `/admineconomy add <player> <amount>` works for both online and offline players
- [ ] `/admineconomy subtract` clamps to 0 rather than rejecting
- [ ] `/admineconomy set` rejects negative amounts
- [ ] The `/ae` alias resolves to `/admineconomy` correctly
- [ ] `plugin.yml` `children` map on `craftedeconomy.admin` grants all four child nodes
- [ ] `ce_transactions` table is auto-created on startup if it does not exist
- [ ] Every balance change (pay, convert, admin) inserts a row into `ce_transactions` asynchronously
- [ ] A logging failure never prevents the economy operation from completing
- [ ] `/admineconomy logs <player>` paginates correctly with clickable prev/next components
- [ ] Tab completion returns filtered, non-null lists on every command
- [ ] `/admineconomy` tab completion filters subcommand suggestions by the sender's permissions
- [ ] `/convert` tab-completes `to` and `from` on arg 1
- [ ] `plugin.yml` permissions block matches the exact structure defined above (including `children` map)
- [ ] All new message keys are present in `config.yml` with sensible defaults
- [ ] `README.md` has been fully rewritten with all 12 sections and reflects Phase 1 + Phase 2
- [ ] No hardcoded strings outside of config or constants
- [ ] No database calls on the main thread
