package main.walksy.lib.control.internal;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class Messages {
    public static final String FILE_NAME = "messages.yml";
    private static final String PREFIX_KEY = "prefix";
    private final Map<String, String> templates;

    private Messages(final Map<String, String> templates) {
        this.templates = templates;
    }

    public static Messages load(final File file) {
        final Map<String, String> templates = readDefaults();
        if (file.exists()) {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (final String key : yaml.getKeys(false)) {
                final String value = yaml.getString(key);
                if (value != null) {
                    templates.put(key, value);
                }
            }
        }
        return new Messages(templates);
    }

    private static Map<String, String> readDefaults() {
        final Map<String, String> templates = new HashMap<>();
        final InputStream stream = Messages.class.getClassLoader().getResourceAsStream(FILE_NAME);
        if (stream == null) {
            return templates;
        }
        try (final InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
            for (final String key : yaml.getKeys(false)) {
                final String value = yaml.getString(key);
                if (value != null) {
                    templates.put(key, value);
                }
            }
        } catch (final IOException e) {
            return templates;
        }
        return templates;
    }

    public String get(final String key, final String... pairs) {
        final String template = this.templates.getOrDefault(key, key)
                .replace("{prefix}", this.templates.getOrDefault(PREFIX_KEY, ""));
        final String colored = ChatColor.translateAlternateColorCodes('&', template);
        final Map<String, String> values = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        final StringBuilder out = new StringBuilder(colored.length());
        int i = 0;
        while (i < colored.length()) {
            final char c = colored.charAt(i);
            if (c == '{') {
                final int close = colored.indexOf('}', i + 1);
                if (close > i) {
                    final String name = colored.substring(i + 1, close);
                    final String value = values.get(name);
                    if (value != null) {
                        out.append(value);
                        i = close + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    public void send(final CommandSender sender, final String key, final String... pairs) {
        sender.sendMessage(this.get(key, pairs));
    }
}
