# Task: Add `/admineconomy reload` command to CraftedEconomy

Add a configuration reload command to the existing CraftedEconomy plugin.

## Requirements

**Command:** `/admineconomy reload` (with alias `/aeco reload`)

**Behaviour:**
- Reloads `config.yml` from disk without a server restart
- Re-reads all configurable values (messages, currency name/symbol, formatting, MariaDB/HikariCP settings where safe to hot-reload)
- Re-registers or refreshes any cached config-derived values (e.g. prefixes, message strings, PlaceholderAPI output formats)
- Returns clear success/failure feedback to the sender

## Implementation details

1. **Permission:** Gate the command behind `craftedeconomy.admin.reload`. Default to `op`.

2. **Config handling:**
   - Call `reloadConfig()` on the plugin instance
   - Push refreshed values into whatever config-holder/manager class currently caches them (don't read directly from `getConfig()` at call sites if a cached holder exists — update the holder)
   - Do **not** tear down and rebuild the HikariCP pool on reload unless connection settings actually changed; if pool settings are present in config, log a warning that DB connection changes require a full restart rather than silently ignoring them

3. **Messages:** Use the plugin's existing message/prefix system. Add new keys to `config.yml`:
   - `messages.reload-success`
   - `messages.reload-failed`
   Include the dark gray `»` separator prefix style consistent with the rest of the plugin.

4. **Error handling:** Wrap the reload in try/catch. On failure, send `reload-failed` to the sender, log the full stack trace to console, and leave the previously-loaded config intact (don't apply a partially-parsed config).

5. **Tab completion:** Add `reload` as a tab-completion suggestion for the `admineconomy`/`aeco` command (respecting the permission).

6. **plugin.yml:** Register the command and alias, the permission node, and its default.

## Constraints
- Paper API, Java 21, Maven
- Match the existing code style, package structure, and message-formatting conventions already in the project
- Don't introduce new dependencies

## Deliverables
- Updated command executor (or a new subcommand handler if the plugin uses a subcommand router)
- Updated tab completer
- Updated `config.yml` with new message keys
- Updated `plugin.yml`
- Brief note in the README documenting the new command and permission
