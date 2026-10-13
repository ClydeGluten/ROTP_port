package com.github.standobyte.jojo.fabric;

import com.google.gson.JsonParser;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registers the original sound identifiers without Forge's DeferredRegister. */
public final class JojoSounds {
    private static final Map<String, SoundEvent> SOUNDS = new LinkedHashMap<>();

    public static void initialize() {
        try (var input = JojoSounds.class.getResourceAsStream("/assets/jojo/sounds.json")) {
            if (input == null) throw new IllegalStateException("Missing original sound definitions");
            var definitions = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String path : definitions.keySet()) {
                var id = RipplesFabric.id(path);
                SOUNDS.put(path, Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load original sound definitions", e);
        }
    }

    public static SoundEvent get(String path) {
        SoundEvent sound = SOUNDS.get(path);
        if (sound == null) throw new IllegalArgumentException("Unknown JoJo sound: " + path);
        return sound;
    }
    private JojoSounds() { }
}
