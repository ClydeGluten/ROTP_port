package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.StandItems;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Discoverable knife controls; original selected-action priority and Alt bypass remain intact. */
public final class KnifeClient {
    public static void initialize() {
        ItemTooltipCallback.EVENT.register((stack, context, flags, lines) -> {
            if (!stack.is(StandItems.KNIFE)) return;
            var client = Minecraft.getInstance();
            lines.add(Component.translatable("jojo.fabric.knife.throw", client.options.keyUse.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("jojo.fabric.knife.single", client.options.keyShift.getTranslatedKeyMessage(),
                    client.options.keyUse.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("jojo.fabric.knife.bypass", bypassKey()).withStyle(ChatFormatting.YELLOW));
            lines.add(Component.translatable("jojo.fabric.knife.world").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("jojo.fabric.knife.obtain").withStyle(ChatFormatting.DARK_GRAY));
        });
        HudElementRegistry.addLast(RipplesFabric.id("knife_controls"), (graphics, delta) -> {
            var client = Minecraft.getInstance();
            if (client.player == null || client.gui.screen() != null || client.gui.hud.isHidden()
                    || !client.player.isAlive() || client.player.isSpectator() || !StandSelection.isPowerMode()
                    || !(client.player.getMainHandItem().is(StandItems.KNIFE) || client.player.getOffhandItem().is(StandItems.KNIFE))) return;
            Component hint = throwHint();
            var lines = client.font.split(hint, Math.max(80, graphics.guiWidth() - 16));
            int y = graphics.guiHeight() - 70 - lines.size() * 10;
            for (var line : lines) {
                int width = client.font.width(line), x = (graphics.guiWidth() - width) / 2;
                graphics.fill(x - 3, y - 2, x + width + 3, y + 10, 0x88000000);
                graphics.text(client.font, line, x, y, 0xFFFFFFAA);
                y += 10;
            }
        });
    }

    public static Component throwHint() {
        var options = Minecraft.getInstance().options;
        return Component.translatable("jojo.fabric.knife.hint", bypassKey(), options.keyUse.getTranslatedKeyMessage(),
                options.keyShift.getTranslatedKeyMessage());
    }

    private static Component bypassKey() {
        var mapping = KeyMapping.get("jojo.key.disable_hotbars");
        return mapping == null ? Component.translatable("jojo.key.disable_hotbars") : mapping.getTranslatedKeyMessage();
    }

    private KnifeClient() { }
}
