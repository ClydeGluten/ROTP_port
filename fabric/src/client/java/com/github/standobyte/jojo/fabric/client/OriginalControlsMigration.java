package com.github.standobyte.jojo.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** One-time migration of Draft 8 defaults; deliberately rebound keys retain their user's values. */
public final class OriginalControlsMigration {
    public static void apply(Minecraft client) {
        var marker = FabricLoader.getInstance().getConfigDir().resolve("jojo/original-controls-v1");
        // Draft 9 wrote GLFW's -1 sentinel, but 26.3 polls SDL scancodes and uses UNKNOWN (0).
        // Repair even when the original layout/key migration has already completed.
        repairUnboundKeys(client, marker.getParent());
        if (Files.exists(marker)) return;
        try {
            Files.createDirectories(marker.getParent());
            var options = client.options.getFile().toPath();
            if (Files.exists(options)) Files.copy(options, marker.resolveSibling("options-before-original-controls.txt"), StandardCopyOption.REPLACE_EXISTING);
            migrate("summon", InputConstants.KEY_G, InputConstants.KEY_M);
            migrate("control", InputConstants.KEY_H, InputConstants.KEY_O);
            migrate("progress", InputConstants.KEY_K, InputConstants.KEY_H);
            migrate("layout", InputConstants.KEY_M, InputConstants.KEY_BACKSLASH);
            migrate("heavy", InputConstants.KEY_V, InputConstants.UNKNOWN.getValue());
            migrate("uppercut", InputConstants.KEY_B, InputConstants.UNKNOWN.getValue());
            KeyMapping.resetMapping();
            client.options.save();
            Files.writeString(marker, "Original ROTP HUD defaults applied; existing custom bindings retained.\n");
        } catch (java.io.IOException exception) {
            LogUtils.getLogger().warn("Could not migrate original Stand controls", exception);
        }
    }

    private static void repairUnboundKeys(Minecraft client, java.nio.file.Path directory) {
        boolean changed = false;
        for (String action : java.util.List.of("heavy", "uppercut")) {
            var key = KeyMapping.get("key.jojo.fabric." + action);
            if (key != null && key.matches(InputConstants.Type.KEYBOARD.getOrCreate(-1))) {
                key.setKey(InputConstants.UNKNOWN);
                changed = true;
            }
        }
        if (!changed) return;
        // The in-memory repair must survive an unwritable options/config directory too.
        KeyMapping.resetMapping();
        try {
            Files.createDirectories(directory);
            var options = client.options.getFile().toPath();
            var backup = directory.resolve("options-before-unbound-key-repair.txt");
            if (Files.exists(options) && !Files.exists(backup)) Files.copy(options, backup);
            client.options.save();
            LogUtils.getLogger().info("Repaired legacy unbound Stand keys for SDL input");
        } catch (java.io.IOException exception) {
            LogUtils.getLogger().warn("Stand keys repaired in memory, but could not save the options backup", exception);
        }
    }

    private static void migrate(String action, int previous, int replacement) {
        var key = KeyMapping.get("key.jojo.fabric." + action);
        if (key != null && key.matches(InputConstants.Type.KEYBOARD.getOrCreate(previous)))
            key.setKey(InputConstants.Type.KEYBOARD.getOrCreate(replacement));
    }

    private OriginalControlsMigration() { }
}
