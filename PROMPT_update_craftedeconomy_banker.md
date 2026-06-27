# CraftedEconomy — Update Prompt (Banker Conversion, Placeholder Trimming, Config Auto-Merge)

## Role & Context

You are an expert Paper/Spigot Minecraft plugin developer working on an **existing, already-functional plugin** called **CraftedEconomy**, authored by **SamsSide**, running on Paper 1.21.x / Java 21, package root `net.craftedsurvival.craftedeconomy`.

This is **normal ongoing development, not a release** — **do NOT increment the plugin version** in `pom.xml` or `plugin.yml`. Leave the version exactly as it is.

The plugin already works: virtual diamond balances, physical↔virtual conversion, admin commands, transaction logging, baltop, Vault registration, PAPI placeholders, and MariaDB/HikariCP storage are all built and functioning. **Do not rewrite or regress any of that.** You are making three targeted changes only.

Read this entire prompt before writing any code. Before changing anything, **read the existing source** for the `/convert` command handler, the PAPI expansion class, and the config-loading / plugin-enable logic, so your changes integrate cleanly with the existing structure and naming conventions. Match the existing code style.

---

## Change 1 — Convert via Banker NPC (console-only `/convert`)

### Goal
On the live server, players must **not** be able to convert their own currency by typing a command. Conversion will be triggered by clicking an item inside an NPC banker's GUI, which fires a **console command**. The GUI itself is **out of scope** for this task — you are only building the console-executable command and its logic. Do not build any GUI, NPC integration, or menu.

### New command behaviour
Replace the current player-facing `/convert` usage with a **console-only** command:

```
/convert <player> <to|from> <amount>
```

- `<player>` — the exact name of the online player who clicked the banker GUI button (passed in by the GUI). The command operates on **that** player's inventory and balance.
- `<to>` — convert **physical → virtual**: remove physical currency items from the player's inventory and credit their virtual balance.
- `<from>` — convert **virtual → physical**: debit the player's virtual balance and give them physical currency items.
- `<amount>` — a positive number, validated the same way the existing conversion logic validates amounts.

### Hard requirements
- **Console-only.** If the sender is a `Player` (or anything that is not the console / command block, per your judgement — at minimum, block real players), reject with a configurable message and do nothing. The player must have **no** way to run this themselves. Remove/disable the old player-facing `/convert` self-conversion path entirely.
- **Target player must be online.** If the named player is offline or not found, send a configurable error to the console sender and do nothing.
- **Reuse the existing, proven conversion logic.** Apply the exact same anti-duplication sequencing that already exists in the current `/convert`:
  - `to` (physical → virtual): verify the player physically has enough items, **remove the items first**, then credit the virtual balance.
  - `from` (virtual → physical): verify sufficient virtual balance, verify the player has **enough free inventory space** for the resulting items, then debit the balance and give the items. If there isn't enough space, abort cleanly and change nothing.
- All transaction logging that currently fires on conversion must continue to fire, recording the **target player** as the affected account.
- Keep the command name `/convert`. Update `plugin.yml` usage/description to reflect the new console syntax. The permission node may remain, but since this is console-only its practical default should reflect that only console/op can ever use it — set the default to `op` (console always bypasses permission checks anyway).

### Messages
Add any new configurable messages needed, e.g.:
- `convert-console-only` — sent if a player tries to run it.
- `convert-usage` — correct console usage string.
- `convert-player-offline` — named player not online.
- `convert-invalid-direction` — second argument wasn't `to` or `from`.

Reuse existing success / not-enough / inventory-full messages where they already exist rather than duplicating them.

---

## Change 2 — Strip useless decimals from balance display (all placeholders)

### Goal
Currency amounts shown via **placeholders** should not show meaningless trailing zeros. Apply this to **every** balance-displaying placeholder (own balance, baltop entries, rank-relative, all of them) — wherever a balance number is rendered for a placeholder.

### Exact formatting rules
- Maximum of **2** decimal places — never show more than two.
- Strip trailing zeros after the decimal point, and strip the decimal point itself if nothing meaningful remains.
- Worked examples (these must hold exactly):
  - `10.00` → `10`
  - `10.20` → `10.2`
  - `10.21` → `10.21`
  - `10` → `10`
  - `10.215` → `10.22` (rounded to 2 dp, then no trailing zero to strip)
  - `0.50` → `0.5`
  - `0.00` → `0`

### Implementation notes
- Implement this as a single shared formatting helper (e.g. a static method) and route **all** placeholder balance output through it, so behaviour is consistent everywhere and there's one place to maintain it.
- Preserve thousands grouping if the existing display already uses it (e.g. `1,234.5`) — only the **decimal** portion is being trimmed; do not change integer-side formatting/grouping.
- Use half-up rounding to 2 dp before trimming so values like `10.215` behave as shown above.
- **Scope:** this is a **placeholder display** change. Do not alter how balances are stored in the database, returned by the internal API, or used in calculations — only the rendered placeholder string changes. If the existing in-chat command messages already use a different fixed format and the user hasn't asked to change those, leave them as-is; this task is specifically about placeholders.

---

## Change 3 — Non-destructive config auto-merge on update

### Goal
When the plugin updates and the bundled default `config.yml` gains new keys, those new keys must be **added automatically** to the user's existing live `config.yml` on the server, **without removing or altering any setting the user has already configured**. Previously, new features shipped without their config keys being merged in, causing missing-key errors.

### Required behaviour
On plugin enable, perform a **non-destructive merge** between the bundled default config (inside the jar) and the user's on-disk `config.yml`:

- **Preserve all existing user values.** Any key already present on disk keeps the user's value untouched — never overwrite it with the default.
- **Add only missing keys.** Any key present in the bundled default but absent from the user's file is added, using the default value.
- This must work for **nested keys** at any depth (e.g. a new key added under `messages:` or `database:` must be inserted without disturbing sibling keys).
- **Never delete** keys that exist on disk but not in the default (don't strip out anything the user added).
- After merging, **save the file back to disk** so the additions persist, and reload the in-memory config so the new keys are immediately available in this same startup.
- Preserve the user's existing settings exactly. Specifically these categories must survive a merge untouched: database connection details, currency settings, exchange rate, and all customised message strings.

### Implementation guidance
- The clean way to do this in Spigot/Paper is to load the on-disk config, load the jar default via `getResource("config.yml")` into a `YamlConfiguration`, then for every key path in the default that is **not** already set on disk, copy the default value across — then save. Spigot's `config.options().copyDefaults(true)` + `saveConfig()` is acceptable **only if** it reliably adds nested defaults without clobbering user values in your testing; if there's any doubt, implement an explicit recursive deep-merge over `getKeys(true)` so behaviour is guaranteed.
- Comment preservation in YAML round-tripping is best-effort and **not required** — correctness of values is the priority. Do not lose user values in pursuit of preserving comments. (If you can preserve the default file's comments for newly added sections, that's a nice-to-have, not a requirement.)
- If you add a config version/marker key to help detect updates, that is acceptable, but it must not interfere with the merge or require user action.

---

## General Requirements

- **Do not increment the version number.** This is routine development.
- Do not introduce new hardcoded strings for anything user-facing — route new messages through `config.yml` and the existing message system.
- Keep all database operations asynchronous, exactly as the existing code does.
- Don't regress any existing feature: admin commands, logging, baltop, Vault, existing placeholders, internal API must all still work after your changes.
- Match the existing project's package layout, naming, and formatting conventions.

---

## Update the README

Update `README.md` to reflect the three changes:
- The `/convert` command is now **console-only** with syntax `/convert <player> <to|from> <amount>`, intended to be driven by a banker NPC GUI (GUI itself not part of the plugin). Note players can no longer self-convert.
- Placeholder balances now display with trailing decimal zeros stripped (max 2 dp), with the worked examples.
- The plugin now performs a non-destructive config auto-merge on startup, preserving all existing user settings while adding any new keys from updates.

Keep the README's existing structure and just amend the relevant sections — do not rewrite unaffected sections.

---

## Final Checklist

Before finishing, verify:

- [ ] Plugin version in `pom.xml` and `plugin.yml` is **unchanged**
- [ ] `/convert` is console-only; a real player running it is rejected with a configurable message
- [ ] `/convert <player> <to|from> <amount>` works from console for both directions against the named online player's inventory and balance
- [ ] Existing anti-duplication sequencing is preserved for both directions
- [ ] Conversion transaction logging still fires, attributing the target player
- [ ] A single shared formatter trims decimals for **all** balance placeholders per the exact worked examples
- [ ] Placeholder trimming does **not** affect stored balances, API return values, or calculations
- [ ] On enable, missing config keys (including nested) are auto-added from the bundled default
- [ ] No existing user config value (database, currency, exchange rate, messages) is ever overwritten or deleted by the merge
- [ ] Merged config is saved to disk and reloaded in the same startup
- [ ] All existing features still function (admin commands, logging, baltop, Vault, placeholders, internal API)
- [ ] `README.md` updated for all three changes, unaffected sections left intact
