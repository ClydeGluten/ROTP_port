package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.client.OriginalControlsMigration;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import org.lwjgl.sdl.SDLKeyboard;

/** Covers native SDL focus polling, which the simulated-input harness normally replaces. */
public final class ControlsClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var heavy = KeyMapping.get("key.jojo.fabric.heavy");
            var uppercut = KeyMapping.get("key.jojo.fabric.uppercut");
            if (!heavy.getDefaultKey().equals(InputConstants.UNKNOWN)
                    || !uppercut.getDefaultKey().equals(InputConstants.UNKNOWN)) {
                throw new AssertionError("Default unbound Stand keys must use InputConstants.UNKNOWN, not key.keyboard.-1");
            }
            var previousHeavy = InputConstants.getKey(heavy.saveString());
            var previousUppercut = InputConstants.getKey(uppercut.saveString());
            var folder = FabricLoader.getInstance().getConfigDir().resolve("jojo");
            var marker = folder.resolve("original-controls-v1");
            try {
                java.nio.file.Files.createDirectories(folder);
                java.nio.file.Files.writeString(marker, "Draft 9 migration already completed.\n");
                heavy.setKey(InputConstants.Type.KEYBOARD.getOrCreate(-1));
                uppercut.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F7));
                KeyMapping.resetMapping();
                client.options.save();
                OriginalControlsMigration.apply(client);
                if (!heavy.isUnbound() || !uppercut.matches(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F7)))
                    throw new AssertionError("Existing Draft 9 repair missed invalid key or overwrote a custom binding");
                var saved = java.nio.file.Files.readString(client.options.getFile().toPath());
                if (!saved.contains("key_key.jojo.fabric.heavy:key.keyboard.unknown"))
                    throw new AssertionError("Repaired unbound key was not persisted");
                var backup = folder.resolve("options-before-unbound-key-repair.txt");
                if (!java.nio.file.Files.isRegularFile(backup)) throw new AssertionError("Key repair backup missing");

                // The first fixture also proves re-running after the v1 marker preserves custom bindings.
                uppercut.setKey(InputConstants.Type.KEYBOARD.getOrCreate(-1));
                client.options.save();
                OriginalControlsMigration.apply(client);
                if (!heavy.isUnbound() || !uppercut.isUnbound()) throw new AssertionError("Uppercut repair missed invalid key");
                client.options.load();
                if (!heavy.isUnbound() || !uppercut.isUnbound()) throw new AssertionError("Options reload restored invalid keys");

                // Use the real SDL buffer directly, bypassing Fabric GameTest's InputConstants mixin.
                var keyboard = SDLKeyboard.SDL_GetKeyboardState();
                if (keyboard == null) throw new AssertionError("Native SDL keyboard unavailable");
                for (var mapping : client.options.keyMappings) {
                    var key = InputConstants.getKey(mapping.saveString());
                    if (key.getType() == InputConstants.Type.KEYBOARD && !mapping.isUnbound()) {
                        try { keyboard.get(key.getValue()); }
                        catch (IndexOutOfBoundsException error) {
                            throw new AssertionError("Unsafe native focus key: " + mapping.getName() + "=" + key.getName(), error);
                        }
                    }
                }
                KeyMapping.setAll();
            } catch (java.io.IOException error) {
                throw new AssertionError("Could not exercise saved-key migration", error);
            } finally {
                heavy.setKey(previousHeavy); uppercut.setKey(previousUppercut);
                KeyMapping.resetMapping();
                client.options.save();
            }
        });
    }
}
