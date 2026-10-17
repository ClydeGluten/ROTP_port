package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.VampirePresentation;
import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.nio.file.Files;

/** Original per-player glowing-eyes preference; reapplied on every server connection. */
public final class VampireClientSettings {
    public record Settings(boolean vampireGlowingEyes) { }
    public static void initialize() {
        var path = FabricLoader.getInstance().getConfigDir().resolve("jojo/vampire-client.json");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        var defaults = new Settings(true);
        try {
            if (!Files.exists(path)) { Files.createDirectories(path.getParent()); Files.writeString(path, gson.toJson(defaults) + "\n"); }
        } catch (Exception error) { RipplesFabric.LOGGER.warn("Could not save vampire cosmetic preferences", error); }
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Settings settings = defaults;
            try { var loaded = gson.fromJson(Files.readString(path), Settings.class); if (loaded != null) settings = loaded; }
            catch (Exception error) { RipplesFabric.LOGGER.warn("Could not load vampire cosmetic preferences", error); }
            if (ClientPlayNetworking.canSend(VampirePresentation.Settings.TYPE))
                ClientPlayNetworking.send(new VampirePresentation.Settings(settings.vampireGlowingEyes()));
        });
    }
    private VampireClientSettings() { }
}
