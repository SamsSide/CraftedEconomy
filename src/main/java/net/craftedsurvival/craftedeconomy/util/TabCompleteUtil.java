package net.craftedsurvival.craftedeconomy.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Helpers for building filtered, case-insensitive tab-completion suggestions.
 * Every method returns a mutable {@link List}; callers never receive {@code null}.
 */
public final class TabCompleteUtil {

    private TabCompleteUtil() {
    }

    /** Return the options whose start matches {@code token} (case-insensitive), sorted. */
    public static List<String> filter(Collection<String> options, String token) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(token, options, matches);
        Collections.sort(matches);
        return matches;
    }

    /** Names of all currently online players. */
    public static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    /** Names of all online players except the given one (used so /pay never suggests the sender). */
    public static List<String> onlinePlayerNamesExcept(Player exclude) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(exclude)) {
                names.add(p.getName());
            }
        }
        return names;
    }
}
