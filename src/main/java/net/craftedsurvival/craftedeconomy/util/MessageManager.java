package net.craftedsurvival.craftedeconomy.util;

import net.craftedsurvival.craftedeconomy.CraftedEconomy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

public class MessageManager {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    private final CraftedEconomy plugin;

    public MessageManager(CraftedEconomy plugin) {
        this.plugin = plugin;
    }

    public Component get(String key) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String raw = plugin.getConfig().getString("messages." + key, "&cMissing message: " + key);
        return LEGACY.deserialize(prefix + raw);
    }

    public Component get(String key, Map<String, String> placeholders) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String raw = plugin.getConfig().getString("messages." + key, "&cMissing message: " + key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return LEGACY.deserialize(prefix + raw);
    }

    public Component getRaw(String key, Map<String, String> placeholders) {
        String raw = plugin.getConfig().getString("messages." + key, "&cMissing message: " + key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return LEGACY.deserialize(raw);
    }

    public static Component parse(String text) {
        return LEGACY.deserialize(text);
    }
}
