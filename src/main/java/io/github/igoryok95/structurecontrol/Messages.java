package io.github.igoryok95.structurecontrol;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class Messages {
    private static final Map<String, Map<String, String>> CATALOGUES = load();

    private Messages() {}

    private static Map<String, Map<String, String>> load() {
        Map<String, Map<String, String>> all = new HashMap<>();
        for (String locale : new String[]{"en_us", "ru_ru"}) {
            String path = "/assets/structure_control/lang/" + locale + ".json";
            try (var stream = Messages.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing language catalogue: " + locale);
                Map<String, String> catalogue = new HashMap<>();
                JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject()
                        .entrySet().forEach(entry -> catalogue.put(entry.getKey(), entry.getValue().getAsString()));
                all.put(locale, Map.copyOf(catalogue));
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot load language catalogue: " + locale, exception);
            }
        }
        if (!all.get("en_us").keySet().equals(all.get("ru_ru").keySet())) {
            throw new IllegalStateException("English and Russian catalogue keys differ");
        }
        return Map.copyOf(all);
    }

    public static MutableComponent tr(CommandSourceStack source, String key, Object... arguments) {
        String language = source.getEntity() instanceof ServerPlayer player ? player.clientInformation().language() : "en_us";
        return language(language, key, arguments);
    }

    public static MutableComponent language(String language, String key, Object... arguments) {
        String fullKey = "structure_control." + key;
        String locale = language.toLowerCase(java.util.Locale.ROOT).startsWith("ru") ? "ru_ru" : "en_us";
        String fallback = CATALOGUES.get(locale).get(fullKey);
        if (fallback == null) throw new IllegalArgumentException("Missing translation: " + fullKey);
        return Component.translatableWithFallback(fullKey, fallback, arguments);
    }

    public static Map<String, String> catalogue(String locale) { return CATALOGUES.get(locale); }
}
