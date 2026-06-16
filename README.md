# CraftedEconomy

A production-ready Minecraft economy plugin for Paper 1.21.x, authored by **SamsSide**.
Provides virtual diamond currency, Vault integration, MySQL/MariaDB storage, transaction logging, PlaceholderAPI support, and a clean public API.

---

## 1. Overview

CraftedEconomy gives your Paper server a full-featured virtual economy backed by a relational database. It registers as a Vault economy provider so any Vault-compatible plugin (Jobs, AuctionHouse, ChestShop, etc.) works out of the box, exposes a public `CraftedEconomyAPI` for direct integration, logs every balance change to a queryable audit table, supports physical-item ⇄ virtual-currency conversion, and ships with PlaceholderAPI placeholders for scoreboards and GUIs.

| Property | Value |
|---|---|
| Author | SamsSide |
| Compatible server | Paper 1.21.x |
| Java | 21 |
| Requires | Vault |
| Optional | PlaceholderAPI |
| Storage | MySQL / MariaDB (HikariCP pool) |

---

## 2. Installation

**Prerequisites**

- Paper **1.21+**
- **Vault** (hard dependency) plus a permissions plugin Vault can hook into (e.g. LuckPerms)
- A **MariaDB / MySQL** database
- **PlaceholderAPI** (optional — only needed for placeholders)

**Steps**

1. Drop `CraftedEconomy-1.0.0.jar` into your server's `plugins/` folder.
2. Install Vault (and a permissions plugin). Optionally install PlaceholderAPI.
3. Start the server once to generate `plugins/CraftedEconomy/config.yml`.
4. Stop the server and fill in your database credentials under the `database:` section.
5. Restart. CraftedEconomy auto-creates both tables (`ce_balances` and `ce_transactions`) if they do not already exist.

HikariCP and the MariaDB connector are shaded and relocated inside the jar — no extra driver jars are required.

---

## 3. Configuration

All options live in `config.yml`.

### `database`

| Key | Type | Default | Description |
|---|---|---|---|
| `host` | String | `localhost` | Database hostname or IP |
| `port` | int | `3306` | Database port |
| `name` | String | `craftedeconomy` | Database name |
| `username` | String | `root` | DB username |
| `password` | String | `password` | DB password |
| `table-prefix` | String | `ce_` | Prefix applied to all tables |
| `pool-size` | int | `10` | HikariCP max connections |

### `currency`

| Key | Type | Default | Description |
|---|---|---|---|
| `name-singular` | String | `Diamond` | Currency singular name |
| `name-plural` | String | `Diamonds` | Currency plural name |
| `item` | Material | `DIAMOND` | Physical item material |
| `exchange-rate` | double | `1.0` | Physical-to-virtual rate |
| `minimum-pay-amount` | double | `0.01` | Minimum `/pay` amount |
| `starting-balance` | double | `0.0` | Balance for new accounts |
| `balance-format` | String | `#,##0.00` | Java DecimalFormat pattern |

### `baltop`

| Key | Type | Default | Description |
|---|---|---|---|
| `page-size` | int | `10` | Players per `/baltop` page |
| `cache-refresh-seconds` | long | `60` | How often the baltop cache refreshes |

### `messages`

All messages support `&` colour codes and hex colours via `&#RRGGBB`. Placeholder tokens are written as `{token}`.

| Key | Tokens |
|---|---|
| `prefix` | — |
| `balance-self` | `{balance}`, `{currency}` |
| `balance-other` | `{player}`, `{balance}`, `{currency}` |
| `player-not-found` | `{player}` |
| `pay-sent` | `{amount}`, `{currency}`, `{player}` |
| `pay-received` | `{amount}`, `{currency}`, `{player}` |
| `pay-not-enough` | `{currency}` |
| `pay-invalid-amount` | — |
| `pay-minimum-amount` | `{min}`, `{currency}` |
| `pay-self` | — |
| `convert-to-success` | `{physical}`, `{item}`, `{virtual}`, `{currency}` |
| `convert-from-success` | `{virtual}`, `{currency}`, `{physical}`, `{item}` |
| `convert-not-enough-items` | `{item}` |
| `convert-not-enough-balance` | `{currency}` |
| `convert-inventory-full` | — |
| `convert-amount-too-small` | — |
| `convert-invalid-amount` | — |
| `convert-invalid-direction` | — |
| `baltop-header` | `{page}`, `{total_pages}` |
| `baltop-entry` | `{rank}`, `{player}`, `{balance}`, `{currency}` |
| `baltop-footer` | — |
| `baltop-prev` / `baltop-next` | — |
| `baltop-prev-hover` / `baltop-next-hover` | `{page}` |
| `baltop-invalid-page` | — |
| `no-permission` | — |
| `console-only-players` | — |
| `error-generic` | — |
| `admin-usage` | — |
| `admin-add-success` | `{amount}`, `{currency}`, `{player}`, `{balance}` |
| `admin-subtract-success` | `{amount}`, `{currency}`, `{player}`, `{balance}` |
| `admin-subtract-clamped` | `{currency}`, `{player}` |
| `admin-set-success` | `{amount}`, `{currency}`, `{player}` |
| `admin-notify-player` | `{balance}`, `{currency}` |
| `admin-invalid-amount` | — |
| `admin-player-not-found` | `{player}` |
| `logs-header` | `{player}` |
| `logs-entry` | `{id}`, `{type}`, `{amount}`, `{currency}`, `{balance_after}`, `{actor}`, `{timestamp}` |
| `logs-empty` | `{player}` |
| `logs-footer` | `{page}`, `{total_pages}` |

---

## 4. Commands

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/balance` | `/balance [player]` | `craftedeconomy.balance` | Show your balance, or another player's |
| `/pay` | `/pay <player> <amount>` | `craftedeconomy.pay` | Send currency to another player |
| `/baltop` | `/baltop [page]` | `craftedeconomy.baltop` | View the paginated balance leaderboard |
| `/convert` | `/convert <to\|from> <amount>` | `craftedeconomy.convert` | Exchange physical items ⇄ virtual currency |
| `/admineconomy add` | `/admineconomy add <player> <amount>` | `craftedeconomy.admin.add` | Add to a player's balance |
| `/admineconomy subtract` | `/admineconomy subtract <player> <amount>` | `craftedeconomy.admin.subtract` | Subtract from a player's balance (clamps at 0) |
| `/admineconomy set` | `/admineconomy set <player> <amount>` | `craftedeconomy.admin.set` | Set a player's balance exactly (≥ 0) |
| `/admineconomy logs` | `/admineconomy logs <player> [page]` | `craftedeconomy.admin.logs` | View a player's transaction history |

Aliases: `/balance` → `bal`, `money`; `/admineconomy` → `ae`.

`/admineconomy` accepts both online and offline player names (resolved by UUID). If a player has never joined the server, the `admin-player-not-found` message is sent. `subtract` clamps to `0` rather than rejecting when the result would be negative; `set` rejects negative amounts. All admin database writes run asynchronously, and an online target is notified (without revealing which admin acted).

---

## 5. Permissions

| Permission | Default | Grants |
|---|---|---|
| `craftedeconomy.balance` | `true` | Use `/balance` on self |
| `craftedeconomy.balance.others` | `op` | Use `/balance <player>` on others |
| `craftedeconomy.pay` | `true` | Use `/pay` |
| `craftedeconomy.baltop` | `true` | Use `/baltop` |
| `craftedeconomy.convert` | `true` | Use `/convert` |
| `craftedeconomy.admin` | `op` | Parent node — grants all four admin children below |
| `craftedeconomy.admin.add` | `op` | Use `/admineconomy add` |
| `craftedeconomy.admin.subtract` | `op` | Use `/admineconomy subtract` |
| `craftedeconomy.admin.set` | `op` | Use `/admineconomy set` |
| `craftedeconomy.admin.logs` | `op` | Use `/admineconomy logs` |

`craftedeconomy.admin` implicitly grants `add`, `subtract`, `set`, and `logs` via its `children` map.

---

## 6. Tab Completion

Every command provides server-side, case-insensitive prefix-filtered tab completion (never `null`, never client-filtered):

- **`/balance`** — suggests online player names on arg 1, but only for senders with `craftedeconomy.balance.others`.
- **`/pay`** — suggests online player names (excluding the sender) on arg 1.
- **`/convert`** — suggests `to` / `from` on arg 1.
- **`/baltop`** — suggests `1` / `2` / `3` page hints on arg 1.
- **`/admineconomy`** — arg 1 suggests `add` / `subtract` / `set` / `logs`, filtered to the subcommands the sender actually has permission for; arg 2 suggests online player names; arg 3 suggests page hints for `logs`.

---

## 7. PlaceholderAPI Placeholders

Registered automatically when PlaceholderAPI is present. Identifier: `craftedeconomy`.

| Placeholder | Returns | Notes |
|---|---|---|
| `%craftedeconomy_balance%` | Player's formatted balance | Live DB lookup |
| `%craftedeconomy_balance_raw%` | Player's raw balance as a double | Live DB lookup |
| `%craftedeconomy_rank%` | Player's position on the baltop | From cache |
| `%craftedeconomy_baltop_1_name%` … `_10_name%` | Names of #1–#10 | From cache |
| `%craftedeconomy_baltop_1_balance%` … `_10_balance%` | Balances of #1–#10 | From cache |
| `%craftedeconomy_transactions_count%` | Total transactions for the player | Cached for 60 seconds |

Baltop placeholders use a result cache that refreshes every `baltop.cache-refresh-seconds` (default 60) to avoid hammering the database on busy scoreboards. The transaction count is cached per player for 60 seconds.

---

## 8. Transaction Logging

Every balance change is recorded in the `ce_transactions` table so you have a full audit trail.

**What is logged**

| Trigger | Type(s) recorded |
|---|---|
| `/pay` | `PAY_SENT` (sender) **and** `PAY_RECEIVED` (recipient) — two rows |
| `/convert to` | `CONVERT_TO` |
| `/convert from` | `CONVERT_FROM` |
| `/admineconomy add` | `ADMIN_ADD` |
| `/admineconomy subtract` | `ADMIN_SUBTRACT` |
| `/admineconomy set` | `ADMIN_SET` |

Each row stores the amount involved (always positive), the balance before and after, the actor (the other player's name for pays, the admin's name for admin actions, or `self` for conversions), an optional note, and a millisecond timestamp.

**Resilience** — all log inserts are asynchronous. A logging failure is reported as a `SEVERE` warning in the console but never rolls back or blocks the economy operation that triggered it.

**Querying logs** — `/admineconomy logs <player> [page]` shows a player's history, most recent first, 10 entries per page, with clickable `[← Previous]` / `[Next →]` navigation (identical to `/baltop`). Each line shows `#id | type | amount | balance_after | actor | timestamp`, where the timestamp is formatted `dd/MM/yyyy HH:mm` in the server's default timezone. If a player has no history, the `logs-empty` message is shown.

---

## 9. Internal API

Other plugins on the same server can use `CraftedEconomyAPI` directly, without depending on Vault:

```java
// Get the API instance
CraftedEconomyAPI eco = CraftedEconomy.getAPI();

// Add 500 to a player's balance
eco.deposit(player.getUniqueId(), 500.0).thenAccept(success -> {
    if (success) player.sendMessage("You received 500 Diamonds!");
});

// Check balance
eco.getBalance(player.getUniqueId())
   .thenAccept(balance -> player.sendMessage("Balance: " + balance));

// Withdraw (returns false if insufficient funds)
eco.withdraw(player.getUniqueId(), 100.0).thenAccept(success -> {
    if (!success) player.sendMessage("Not enough funds!");
});
```

All API methods return `CompletableFuture` — never block the main thread waiting on them.

---

## 10. Exchange / Conversion System

The `/convert` command swaps physical items for virtual currency and back.

**Physical → Virtual (`/convert to <amount>`)**

1. `amount` must be a whole number of items.
2. Matching items are counted across the player's inventory.
3. If there are enough, the items are removed first.
4. The virtual amount (`physical × exchange-rate`) is then credited.
5. If the credit step fails, the items are returned automatically.

**Virtual → Physical (`/convert from <amount>`)**

1. The number of physical items is `floor(amount / exchange-rate)`.
2. If the result is 0, the player is told the amount is too small.
3. Free inventory slots are verified **before** the balance is touched.
4. The exact virtual cost (`physical-count × exchange-rate`) is deducted first.
5. Items are placed in the inventory on the main thread; if the inventory fills mid-give, the undelivered portion is refunded.

The exchange rate is configurable via `currency.exchange-rate` (e.g. `2.0` makes one diamond worth two virtual diamonds).

---

## 11. Database Schema

CraftedEconomy creates two tables (prefix configurable, default `ce_`).

**Balances**

```sql
CREATE TABLE IF NOT EXISTS ce_balances (
    uuid        VARCHAR(36)    NOT NULL PRIMARY KEY,
    player_name VARCHAR(16)    NOT NULL,
    balance     DECIMAL(20,4)  NOT NULL DEFAULT 0.0000
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**Transactions**

```sql
CREATE TABLE IF NOT EXISTS ce_transactions (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    uuid           VARCHAR(36)    NOT NULL,
    type           VARCHAR(32)    NOT NULL,
    amount         DECIMAL(18,4)  NOT NULL,
    balance_before DECIMAL(18,4)  NOT NULL,
    balance_after  DECIMAL(18,4)  NOT NULL,
    actor          VARCHAR(64)    DEFAULT NULL,
    note           VARCHAR(255)   DEFAULT NULL,
    timestamp      BIGINT         NOT NULL,
    INDEX idx_transactions_uuid (uuid),
    INDEX idx_transactions_uuid_ts (uuid, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

| `ce_transactions` column | Description |
|---|---|
| `id` | Auto-increment primary key |
| `uuid` | The target player's UUID |
| `type` | `PAY_SENT`, `PAY_RECEIVED`, `CONVERT_TO`, `CONVERT_FROM`, `ADMIN_ADD`, `ADMIN_SUBTRACT`, `ADMIN_SET` |
| `amount` | Amount involved (always positive) |
| `balance_before` | Balance immediately before |
| `balance_after` | Balance immediately after |
| `actor` | Other player / admin name, or `self` (nullable) |
| `note` | Optional free-text note (nullable) |
| `timestamp` | Unix epoch milliseconds |

---

## 12. Building from Source

Requires Java 21 and Maven 3.6+.

```bash
git clone <repo>
cd CraftedEconomy
mvn clean package
```

The shaded jar will be at `target/CraftedEconomy-1.0.0.jar`. HikariCP and the MariaDB connector are relocated and shaded in — no extra jars needed.
