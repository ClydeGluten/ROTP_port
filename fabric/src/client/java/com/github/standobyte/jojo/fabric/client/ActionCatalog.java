package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.core.ActionLayout;
import com.github.standobyte.jojo.fabric.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.List;

/** The same catalog supplies selection, the editor, progression and HUD labels. */
public final class ActionCatalog {
    public record Move(String id, String icon, String packet, int level, int cost, int held) {
        public Component name() {
            if (id.equals("control")) return Component.translatable("jojo.key.stand_remote_control");
            if (id.equals("time_stop")) {
                var player = Minecraft.getInstance().player;
                float ticks = player == null ? 5 : player.isCreative() ? 100 : StandAttachments.resolve(player).timeStopTicks();
                return Component.translatable("action.jojo." + icon, String.format(java.util.Locale.ROOT, "%.2f", ticks / 20F));
            }
            // TheWorldTSHeavyAttack changes its icon for backshot, but retains its action name.
            return Component.translatable("action.jojo." + (id.equals("time_punch") ? "the_world_ts_punch" : icon));
        }
        public Identifier texture() { return RipplesFabric.id("textures/action/" + icon + ".png"); }
        public StandActionPayload.Action action() { return StandActionPayload.Action.valueOf(packet); }
        public boolean unlocked() {
            var p = Minecraft.getInstance().player;
            return p != null && StandAttachments.resolve(p).unlocked(level, p.isCreative());
        }
        public int cooldown(StandHudState state) {
            return switch (id) {
                case "finger" -> state.finger(); case "inhale" -> state.inhale();
                case "time_stop" -> state.timeStop(); case "blink" -> state.blink();
                case "time_punch" -> state.timePunch(); default -> 0;
            };
        }
        public int cooldownTotal(StandHudState state) {
            return switch (id) {
                case "finger" -> state.fingerTotal(); case "inhale" -> state.inhaleTotal();
                case "time_stop" -> state.timeStopTotal();
                case "blink" -> state.blinkTotal();
                case "time_punch" -> state.timePunchTotal();
                default -> 0;
            };
        }
        public Component costText() { return Component.translatable("jojo.fabric.cost." + id, cost); }
        /** Original time actions clamp their debit; stamina does not prevent starting them. */
        public boolean requiresStaminaUpfront() { return !id.equals("blink") && !id.equals("time_punch"); }
        /** TheWorldTSHeavyAttack can replace the current attack; the server checks the precise phase. */
        public boolean canReplaceRecovery() { return id.equals("time_punch"); }
        /** TimeStopInstant performs on the user even when their Stand is dismissed. */
        public boolean requiresManifestation() { return !id.equals("blink"); }
    }
    public static boolean world() {
        var p = Minecraft.getInstance().player;
        return p != null && p.getAttachedOrCreate(StandAttachments.PROGRESS).hasTheWorld();
    }
    public static String standId() {
        var p = Minecraft.getInstance().player;
        return p == null ? "jojo:star_platinum" : p.getAttachedOrCreate(StandAttachments.PROGRESS).standId();
    }
    public static List<String> attacks(boolean world) {
        // ModStandsInit: heavy punches are Shift variants; finishers are not separate hotbar slots.
        return world ? List.of("light", "barrage", "time_punch") : List.of("light", "barrage", "finger");
    }
    public static List<String> abilities(boolean world) {
        return world ? List.of("guard", "time_stop") : List.of("guard", "zoom", "inhale", "time_stop");
    }
    public static ActionLayout defaults(boolean world) { return ActionLayout.restore(attacks(world), abilities(world)); }
    public static Move move(String id) { return move(id, world()); }
    /** Original Shift/finisher replacement chain, restricted to abilities already migrated. */
    public static Move displayMove(String id) {
        var client = Minecraft.getInstance();
        boolean shift = client.options.keyShift.isDown();
        if (shift && (id.equals("light") || id.equals("barrage"))) {
            var stand = StandView.ownStand(client);
            if (stand != null && stand.finisherCharge() >= 0.5F && move(world() ? "kick" : "uppercut").unlocked()) {
                // Sending HEAVY_PUNCH lets the server validate and consume finisher charge normally.
                var finisher = move(world() ? "kick" : "uppercut");
                return new Move(finisher.id(), finisher.icon(), "HEAVY_PUNCH", finisher.level(), finisher.cost(), 0);
            }
            return move("heavy");
        }
        if (id.equals("time_stop") && client.player != null) {
            if (client.player.getAttachedOrCreate(StandAttachments.HUD).activeStop() > 0) return move("resume");
            if (shift) return move("blink");
        }
        if (shift && world() && id.equals("time_punch")) {
            var punch = move(id);
            return new Move(punch.id(), "the_world_ts_punch_back", punch.packet(), punch.level(), punch.cost(), punch.held());
        }
        return move(id);
    }
    public static Move move(String id, boolean world) {
        String prefix = world ? "the_world_" : "star_platinum_";
        return switch (id) {
            case "light" -> new Move(id, prefix + "punch", "LIGHT_PUNCH", 0, 10, 0);
            case "heavy" -> new Move(id, prefix + "heavy_punch", "HEAVY_PUNCH", 0, 50, 0);
            case "barrage" -> new Move(id, prefix + "barrage", "START_BARRAGE", 0, 4, 1);
            case "guard" -> new Move(id, prefix + "block", "START_GUARD", 0, 0, 2);
            case "uppercut" -> new Move(id, "star_platinum_uppercut", "UPPERCUT", 1, 50, 0);
            case "kick" -> new Move(id, "the_world_kick", "UPPERCUT", 1, 50, 0);
            case "finger" -> new Move(id, "star_platinum_star_finger", "STAR_FINGER", 3, 375, 0);
            case "inhale" -> new Move(id, "star_platinum_inhale", "START_INHALE", 2, 2, 3);
            case "zoom" -> new Move(id, "star_platinum_zoom", "START_ZOOM", 0, 0, 4);
            case "control" -> new Move(id, prefix + "punch", "START_CONTROL", 0, 0, 5);
            case "time_stop" -> new Move(id, prefix + "time_stop", "TIME_STOP", world ? 2 : 4, 225, 0);
            case "resume" -> new Move(id, prefix + "time_resume", "TIME_STOP", 0, 0, 0);
            case "blink" -> new Move(id, prefix + "ts_blink", "BLINK", world ? 2 : 4, 180, 0);
            case "time_punch" -> new Move(id, "the_world_ts_punch", "TIME_PUNCH", 3, 140, 0);
            default -> throw new IllegalArgumentException("Unknown action: " + id);
        };
    }
    private ActionCatalog() { }
}
