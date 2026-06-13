# CraftedEconomy

A production-ready Minecraft economy plugin for Paper 1.21.x, authored by **SamsSide**.  
Provides virtual diamond currency, Vault integration, MySQL/MariaDB storage, and a clean public API.

---

## 1. Overview

CraftedEconomy gives your Paper server a full-featured virtual economy backed by a relational database. It registers as a Vault economy provider so that any Vault-compatible plugin (Jobs, Auction House, etc.) works out of the box. It also supports PlaceholderAPI for scoreboards and GUIs.

| Property | Value |
|---|---|
| Author | SamsSide |
| Compatible server | Paper 1.21.x |
| Java | 21 |
| Requires | Vault |
| Optional | PlaceholderAPI |

---

## 2. Installation

1. Place `CraftedEconomy-*.jar` in your server's `plugins/` folder.
2. Install [Vault](https://www.spigotmc.org/resources/vault.34315/) and a permissions plugin that Vault can hook into (e.g. LuckPerms).
3. Start the server once to generate `plugins/CraftedEconomy/config.yml`.
4. Stop the server and fill in your database credentials under the `database:` section.
5. Restart. CraftedEconomy will create the required table automatically.

---

## 3. Configuration Reference

All options live in `config.yml`.

### `database`

| Key | Type | Default | Description |
|---|---|---|---|
| `host` | String | `localhost` | Database hostname or IP |
| `port` | int | `3306` | Database port |
| `name` | String | `craftedeconomy` | Database name |
| `username` | String | `root` | DB username |
| `password` | String | `password` | DB password |
| `table-prefix` | String | `ce_` | Prefix for all tables |
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
| `cache-refresh-seconds` | long | `60` | How often to refresh baltop cache |

### `messages`

All messages support `&` colour codes and hex colours via `&#RRGGBB`. See `config.yml` for the full list of keys and their placeholder tokens.

---

## 4. Commands Reference

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/balance` | `/balance [player]` | `craftedeconomy.balance` | Show your or another player's balance |
| `/pay` | `/pay <player> <amount>` | `craftedeconomy.pay` | Send money to a player |
| `/baltop` | `/baltop [page]` | `craftedeconomy.baltop` | View the balance leaderboard |
| `/convert` | `/convert <to\|from> <amount>` | `craftedeconomy.convert` | Exchange physical items ↔ virtual currency |

---

## 5. Permissions Reference

| Permission | Default | Description |
|---|---|---|
| `craftedeconomy.balance` | false | Use `/balance` on self |
| `craftedeconomy.balance.others` | op | Use `/balance <player>` on others |
| `craftedeconomy.pay` | false | Use `/pay` |
| `craftedeconomy.baltop` | false | Use `/baltop` |
| `craftedeconomy.convert` | false | Use `/convert` |
| `craftedeconomy.admin` | op | Parent node for all admin permissions |
| `craftedeconomy.admin.set` | op | Reserved for future `/eco set` command |

---

## 6. PlaceholderAPI Placeholders

Register automatically when PlaceholderAPI is present. Identifier: `craftedeconomy`.

| Placeholder | Returns | Notes |
|---|---|---|
| `%craftedeconomy_balance%` | Player's formatted balance | Live DB lookup |
| `%craftedeconomy_balance_raw%` | Player's raw balance as a double | Live DB lookup |
| `%craftedeconomy_rank%` | Player's position on the baltop | From cache |
| `%craftedeconomy_baltop_1_name%` | Name of #1 | From cache |
| `%craftedeconomy_baltop_1_balance%` | Formatted balance of #1 | From cache |
| `%craftedeconomy_baltop_2_name%` … `_10_name%` | Names #2–#10 | From cache |
| `%craftedeconomy_baltop_2_balance%` … `_10_balance%` | Balances #2–#10 | From cache |

Baltop placeholders use a cached result that refreshes every `baltop.cache-refresh-seconds` seconds (default 60). This prevents database hammering on busy scoreboards.

---

## 7. Internal API Usage

Other plugins on the same server can use `CraftedEconomyAPI` without depending on Vault:

```java
// Get the API instance
CraftedEconomyAPI eco = CraftedEconomy.getAPI();

// Add 500 diamonds to a player's balance
eco.deposit(player.getUniqueId(), 500.0).thenAccept(success -> {
    if (success) {
        player.sendMessage("You received 500 Diamonds!");
    }
});

// Check balance
eco.getBalance(player.getUniqueId()).thenAccept(balance -> {
    player.sendMessage("Your balance: " + balance);
});

// Withdraw (returns false if insufficient funds)
eco.withdraw(player.getUniqueId(), 100.0).thenAccept(success -> {
    if (!success) player.sendMessage("Not enough funds!");
});
```

All API methods return `CompletableFuture` — never block the main thread waiting on them.

---

## 8. Exchange / Conversion System

The `/convert` command lets players swap physical items for virtual currency and vice versa.

**Physical → Virtual (`/convert to <amount>`)**

1. `amount` must be a whole number (you cannot convert half an item).
2. The plugin counts matching items across the player's inventory.
3. If they have enough, those items are removed first.
4. The virtual amount (`physical × exchange-rate`) is then credited.
5. If the credit step fails, the items are returned automatically.

**Virtual → Physical (`/convert from <amount>`)**

1. The number of physical items is `floor(amount / exchange-rate)`.
2. If the result is 0, the player is told the amount is too small.
3. The plugin verifies inventory has enough free slots before touching the balance.
4. The exact virtual cost (`physical-count × exchange-rate`) is deducted first.
5. Items are then placed in the player's inventory on the main thread.

The exchange rate is fully configurable (`currency.exchange-rate`). With rate `2.0`, one diamond is worth 2 virtual diamonds.

---

## 9. Database Schema

CraftedEconomy creates one table (prefix configurable, default `ce_`):

```sql
CREATE TABLE IF NOT EXISTS ce_balances (
    uuid        VARCHAR(36)    NOT NULL PRIMARY KEY,
    player_name VARCHAR(16)    NOT NULL,
    balance     DECIMAL(20,4)  NOT NULL DEFAULT 0.0000
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 10. Building from Source

Requires Java 21 and Maven 3.6+.

```bash
git clone <repo>
cd CraftedEconomy
mvn clean package
```

The shaded jar will be at `target/CraftedEconomy-1.0.0.jar`. HikariCP and the MariaDB connector are relocated and shaded in — no extra jars needed.
