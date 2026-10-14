package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.core.ActionLayout;
import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Local preferences only. Invalid or obsolete action IDs never become network commands. */
public final class ActionLayouts {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ActionLayout> LAYOUTS = new HashMap<>();
    private static Path file = FabricLoader.getInstance().getConfigDir().resolve("jojo/action-layouts.json");
    private static boolean loaded;

    public static ActionLayout current() { return get(ActionCatalog.standId()); }
    public static ActionLayout get(String stand) {
        if (!loaded) load();
        return LAYOUTS.computeIfAbsent(stand, id -> ActionCatalog.defaults(id.equals("jojo:the_world")));
    }
    public static boolean set(String stand, ActionLayout layout) {
        if (!loaded) load();
        LAYOUTS.put(stand, normalize(stand, layout));
        return save();
    }
    private static ActionLayout normalize(String stand, ActionLayout layout) {
        boolean world = stand.equals("jojo:the_world");
        return ActionLayout.restoreFlexible(layout.attacks(), layout.abilities(), layout.selectedAttack(), layout.selectedAbility(), layout.disabled(),
                ActionCatalog.attacks(world), ActionCatalog.abilities(world));
    }
    public static void load() {
        loaded = true;
        LAYOUTS.clear();
        if (!Files.exists(file)) return;
        try {
            if (Files.size(file) > 65536) throw new IOException("Action layout exceeds 64 KiB");
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (String stand : List.of("jojo:star_platinum", "jojo:the_world")) {
                if (!root.has(stand)) continue;
                JsonObject value = root.getAsJsonObject(stand);
                boolean world = stand.equals("jojo:the_world");
                List<String> attacks = strings(value.get("attacks"));
                List<String> abilities = strings(value.get("abilities"));
                // Draft 8 had extra direct-action slots and a different default ability order.
                // Reset untouched defaults while preserving deliberate compatible reordering.
                if (!root.has("format")) {
                    if (attacks.equals(world ? List.of("light", "heavy", "barrage", "kick", "time_punch")
                            : List.of("light", "heavy", "barrage", "uppercut", "finger"))) attacks = ActionCatalog.attacks(world);
                    if (abilities.equals(world ? List.of("guard", "control", "time_stop", "blink")
                            : List.of("guard", "inhale", "zoom", "control", "time_stop"))) abilities = ActionCatalog.abilities(world);
                }
                LAYOUTS.put(stand, ActionLayout.restoreFlexible(attacks, abilities,
                        string(value.get("selectedAttack")), string(value.get("selectedAbility")), strings(value.get("disabled")),
                        ActionCatalog.attacks(world), ActionCatalog.abilities(world)));
            }
        } catch (IOException | RuntimeException exception) {
            LAYOUTS.clear();
            LogUtils.getLogger().warn("Could not read Stand action layouts; using defaults", exception);
            try { Files.copy(file, file.resolveSibling("action-layouts.invalid.json"), StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException backupError) { LogUtils.getLogger().warn("Could not preserve invalid Stand layout", backupError); }
        }
    }
    private static String string(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
    private static List<String> strings(JsonElement value) {
        if (value == null || !value.isJsonArray()) return List.of();
        List<String> result = new ArrayList<>();
        value.getAsJsonArray().forEach(item -> { String id = string(item); if (id != null) result.add(id); });
        return result;
    }
    private static boolean save() {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "action-layouts-", ".tmp");
            JsonObject root = JSON.toJsonTree(LAYOUTS).getAsJsonObject();
            root.addProperty("format", 2);
            Files.writeString(temporary, JSON.toJson(root));
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException exception) {
            LogUtils.getLogger().warn("Could not save Stand action layouts", exception);
            return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
    private ActionLayouts() { }
}
