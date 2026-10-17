package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.client.pillarman.PillarmanClient;
import com.github.standobyte.jojo.fabric.client.pillarman.PillarmanHud;
import com.github.standobyte.jojo.fabric.client.pillarman.PillarmanActionCatalog;

import com.github.standobyte.jojo.core.ActionLayout;
import com.github.standobyte.jojo.fabric.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.List;

/** The same catalog supplies selection, the editor, progression and HUD labels. */
public final class ActionCatalog {
    private static final java.util.Map<String,java.util.function.BiPredicate<Move,Move>> ALONGSIDE_HELD=new java.util.HashMap<>();
    public static void registerAlongsideHeld(String stand,java.util.function.BiPredicate<Move,Move> predicate){
        com.github.standobyte.jojo.core.StandDefinitions.definition(stand);
        if(ALONGSIDE_HELD.putIfAbsent(stand,java.util.Objects.requireNonNull(predicate))!=null)throw new IllegalStateException("Duplicate alongside-held handler: "+stand);
    }
    public static boolean canRunAlongsideHeld(Move next,Move active){
        if(next.definition()==null||active.definition()==null||next.held()!=0)return false;
        String stand=next.definition().standId();var predicate=ALONGSIDE_HELD.get(stand);
        return stand.equals(active.definition().standId())&&predicate!=null&&predicate.test(next,active);
    }
    private static final java.util.Map<String,java.util.function.BiPredicate<Move,Move>> HELD_REPLACEMENTS=new java.util.HashMap<>();
    public static void registerHeldReplacement(String stand,java.util.function.BiPredicate<Move,Move> predicate){
        com.github.standobyte.jojo.core.StandDefinitions.definition(stand);
        if(HELD_REPLACEMENTS.putIfAbsent(stand,java.util.Objects.requireNonNull(predicate))!=null)throw new IllegalStateException("Duplicate held replacement: "+stand);
    }
    public static boolean canReplaceHeld(Move next,Move active){
        if(!next.registered()||!active.registered()||next.definition()==null||active.definition()==null)return false;
        String stand=next.definition().standId();var predicate=HELD_REPLACEMENTS.get(stand);
        return stand.equals(active.definition().standId())&&predicate!=null&&predicate.test(next,active);
    }
    private static final java.util.Map<String,java.util.function.BiFunction<Minecraft,Identifier,Identifier>> REPLACEMENTS = new java.util.HashMap<>();
    public static void registerReplacement(String stand, java.util.function.BiFunction<Minecraft,Identifier,Identifier> replacement) {
        com.github.standobyte.jojo.core.StandDefinitions.definition(stand);
        if (REPLACEMENTS.putIfAbsent(stand,java.util.Objects.requireNonNull(replacement)) != null) throw new IllegalStateException("Duplicate client Stand replacement: " + stand);
    }
    private static Move registeredDisplay(String id, boolean shift) {
        var base = move(id); var variant = shift ? shifted(id) : null;
        var selected = variant != null && variant.unlocked() ? variant : base;
        var replacement = REPLACEMENTS.get(selected.definition().standId());
        if (replacement == null) return selected;
        var result = replacement.apply(Minecraft.getInstance(), Identifier.parse(selected.id()));
        var definition = result == null ? null : StandActionRegistry.definition(result).orElse(null);
        return definition != null && definition.standId().equals(selected.definition().standId()) ? move(result.toString()) : selected;
    }
    public record Move(String id, String icon, String packet, int level, int cost, int held) {
        public Component name() {
            if (registered()) return Component.translatable("action." + Identifier.parse(id).getNamespace() + "." + Identifier.parse(id).getPath());
            if (pillar()) return PillarmanHud.name(id);
            if (zombie()) return ZombieHud.name(id);
            if (id.equals("control")) return Component.translatable("jojo.key.stand_remote_control");
            if (id.equals("time_stop")) {
                var player = Minecraft.getInstance().player;
                float ticks = player == null ? 5 : player.isCreative() ? ProgressionService.timeStopMaximum(player) : Math.min(ProgressionService.timeStopMaximum(player), StandAttachments.resolve(player).timeStopTicks());
                return Component.translatable("action.jojo." + icon, String.format(java.util.Locale.ROOT, "%.2f", ticks / 20F));
            }
            // TheWorldTSHeavyAttack changes its icon for backshot, but retains its action name.
            return Component.translatable("action.jojo." + (id.equals("time_punch") ? "the_world_ts_punch" : icon));
        }
        public Identifier texture() {
            if (registered()) { var value = Identifier.parse(icon); return Identifier.fromNamespaceAndPath(value.getNamespace(), "textures/action/" + value.getPath() + ".png"); }
            return RipplesFabric.id("textures/action/" + icon + ".png");
        }
        public boolean registered() { return id.indexOf(':') > 0; }
        public StandActionRegistry.Definition definition() { return registered() ? StandActionRegistry.definition(Identifier.parse(id)).orElse(null) : null; }
        public StandHudState.ActionView snapshot() {
            var player = Minecraft.getInstance().player; if (player == null || !registered()) return null;
            var state = player.getAttachedOrCreate(StandHudState.REGISTERED);
            if (!state.stand().equals(standId())) return null;
            return state.actions().stream().filter(view -> view.action().toString().equals(id)).findFirst().orElse(null);
        }
        public boolean available() { var state = snapshot(); return state != null && state.available(); }
        public StandActionPayload.Action action() { return StandActionPayload.Action.valueOf(packet); }
        public boolean unlocked() {
            var p = Minecraft.getInstance().player;
            if (registered()) { var state = snapshot(); return state != null && state.unlocked(); }
            if (hamon()) return HamonClient.unlocked(id);
            if (vampire()) return VampireClient.unlocked(id);
            if (pillar()) return PillarmanClient.unlocked(id);
            if (zombie()) return ZombieClient.unlocked(id);
            return p != null && StandAttachments.resolve(p).unlocked(level, StandConfig.skipsTraining(p));
        }
        public boolean hamon() { return HamonActionCatalog.get(id) != null; }
        public boolean vampire() { return id.startsWith("vampirism_"); }
        public boolean pillar() { return id.startsWith("pillarman_"); }
        public boolean zombie() { return id.startsWith("zombie_"); }
        public int cooldown(StandHudState state) {
            if (registered()) { var view = snapshot(); return view == null ? 0 : view.remaining(); }
            if (hamon()) return HamonClient.cooldown(id);
            if (vampire()) return VampireClient.cooldown(id);
            if (pillar()) return PillarmanClient.cooldown(id);
            if (zombie()) return ZombieClient.cooldown(id);
            return switch (id) {
                case "finger" -> state.finger(); case "inhale" -> state.inhale();
                case "time_stop" -> state.timeStop(); case "blink" -> state.blink();
                case "time_punch" -> state.timePunch(); default -> 0;
            };
        }
        public int cooldownTotal(StandHudState state) {
            if (registered()) { var view = snapshot(); return view == null ? 0 : view.total(); }
            if (hamon()) return HamonClient.cooldownTotal(id);
            if (vampire()) return VampireClient.cooldownTotal(id);
            if (pillar()) return PillarmanClient.cooldownTotal(id);
            if (zombie()) return ZombieClient.cooldownTotal(id);
            return switch (id) {
                case "finger" -> state.fingerTotal(); case "inhale" -> state.inhaleTotal();
                case "time_stop" -> state.timeStopTotal();
                case "blink" -> state.blinkTotal();
                case "time_punch" -> state.timePunchTotal();
                default -> 0;
            };
        }
        public Component costText() { if (pillar()) return PillarmanHud.costText(id); if (registered() || hamon() || zombie()) return Component.literal(Integer.toString(cost)); return Component.translatable("jojo.fabric.cost." + id, cost); }
        /** Original time actions clamp their debit; stamina does not prevent starting them. */
        public boolean requiresStaminaUpfront() { return !id.equals("blink") && !id.equals("time_punch"); }
        /** TheWorldTSHeavyAttack can replace the current attack; the server checks the precise phase. */
        public boolean canReplaceRecovery() { return id.equals("time_punch"); }
        /** TimeStopInstant performs on the user even when their Stand is dismissed. */
        public boolean requiresManifestation() { return registered() ? definition() != null && definition().manifestationRequired() : !id.equals("blink"); }
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
    public static List<String> attacks(String power) {
        if (power.equals(PillarmanClient.POWER_ID)) return PillarmanClient.attacks();
        if (power.equals(ZombieClient.POWER_ID)) return ZombieClient.attacks();
        if (power.equals(HamonClient.POWER_ID)) return HamonActionCatalog.attacks(HamonClient.state());
        if (power.equals(VampireClient.POWER_ID)) return List.of("vampirism_claw_lacerate", "vampirism_blood_drain", "vampirism_freeze", "vampirism_space_ripper_stingy_eyes");
        return registeredRow(power, StandActionRegistry.Hotbar.LEFT);
    }
    public static List<String> abilities(String power) {
        if (power.equals(PillarmanClient.POWER_ID)) return PillarmanClient.abilities();
        if (power.equals(ZombieClient.POWER_ID)) return ZombieClient.abilities();
        if (power.equals(HamonClient.POWER_ID)) return HamonActionCatalog.abilities(HamonClient.state());
        if (power.equals(VampireClient.POWER_ID)) return List.of("vampirism_blood_gift", "vampirism_zombie_summon", "vampirism_dark_aura", "vampirism_hamon_suicide");
        return registeredRow(power, StandActionRegistry.Hotbar.RIGHT);
    }
    public static boolean legacyStand(String power) { return power.equals("jojo:star_platinum") || power.equals("jojo:the_world"); }
    private static List<String> registeredRow(String power, StandActionRegistry.Hotbar row) {
        return StandActionRegistry.actions(power).stream().filter(d -> d.hotbar() == row)
                .sorted(java.util.Comparator.comparingInt(StandActionRegistry.Definition::slot)).map(d -> legacyStand(power) ? legacyLayoutId(d) : d.id().toString()).toList();
    }
    private static String legacyLayoutId(StandActionRegistry.Definition d) {
        String prefix = Identifier.parse(d.standId()).getPath() + "_";
        String path = d.id().getPath();
        if (!path.startsWith(prefix) || !d.delegated()) return d.id().toString();
        return switch (path.substring(prefix.length())) {
            case "punch" -> "light"; case "barrage" -> "barrage"; case "block" -> "guard";
            case "star_finger" -> "finger"; case "inhale" -> "inhale"; case "zoom" -> "zoom";
            case "time_stop" -> "time_stop"; case "ts_punch" -> "time_punch";
            default -> d.id().toString();
        };
    }
    public static int standColor() { return com.github.standobyte.jojo.core.StandDefinitions.find(standId()).map(com.github.standobyte.jojo.core.StandDefinitions.Definition::color).orElse(0xFFFFFF); }
    public static String standPath() { return com.github.standobyte.jojo.core.StandDefinitions.find(standId()).map(com.github.standobyte.jojo.core.StandDefinitions.Definition::path).orElse("star_platinum"); }
    public static Move shifted(String id) {
        if (id.indexOf(':') < 0) return null;
        var definition = StandActionRegistry.definition(Identifier.parse(id)).orElse(null);
        return definition == null || definition.shift() == null ? null : move(definition.shift().toString());
    }
    public static ActionLayout defaults(String power) { return ActionLayout.restore(attacks(power), abilities(power)); }
    public static Move move(String id) { return move(id, world()); }
    /** Original Shift/finisher replacement chain, restricted to abilities already migrated. */
    public static Move displayMove(String id) {
        if (id.startsWith("pillarman_")) return PillarmanClient.displayMove(id);
        if (HamonActionCatalog.get(id) != null) return HamonClient.displayMove(id);
        var client = Minecraft.getInstance();
        boolean shift = client.options.keyShift.isDown();
        if (id.indexOf(':') > 0) return registeredDisplay(id, shift);
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
        if (id.indexOf(':') > 0) {
            var d = StandActionRegistry.definition(Identifier.parse(id)).orElseThrow(() -> new IllegalArgumentException("Unknown registered action: " + id));
            return new Move(id, id, id, d.resolveLevel(), Math.round(d.startCost()), d.held() ? 6 : 0);
        }
        if (id.startsWith("pillarman_")) return PillarmanClient.move(id);
        if (id.startsWith("zombie_")) return ZombieClient.move(id);
        var hamon = HamonActionCatalog.get(id);
        if (hamon != null) return new Move(id, id, id, 0, Math.round(hamon.energyCost()), hamon.held() ? 1 : 0);
        String prefix = world ? "the_world_" : "star_platinum_";
        return switch (id) {
            case "vampirism_claw_lacerate" -> new Move(id, id, "CLAW", 0, 45, 0);
            case "vampirism_blood_drain" -> new Move(id, id, "START_DRAIN", 0, 0, 1);
            case "vampirism_blood_gift" -> new Move(id, id, "START_GIFT", 0, 5, 2);
            case "vampirism_freeze" -> new Move(id, id, "START_FREEZE", 0, 1, 3);
            case "vampirism_space_ripper_stingy_eyes" -> new Move(id, id, "START_EYES", 0, 20, 4);
            case "vampirism_zombie_summon" -> new Move(id, id, "SUMMON_ZOMBIES", 0, 100, 0);
            case "vampirism_dark_aura" -> new Move(id, id, "DARK_AURA", 0, 25, 0);
            case "vampirism_hamon_suicide" -> new Move(id, id, "START_HAMON", 0, 0, 5);
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
