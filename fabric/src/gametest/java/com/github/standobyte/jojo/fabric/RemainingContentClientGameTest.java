package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.client.StandSelection;
import com.github.standobyte.jojo.fabric.client.ZombieClient;
import com.github.standobyte.jojo.fabric.client.pillarman.*;
import com.github.standobyte.jojo.fabric.pillarman.*;
import com.github.standobyte.jojo.core.PillarmanProgress.Mode;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

/** One shared world per connection mode: retained Stand behavior, vampire duration and actual zombie input. */
public final class RemainingContentClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            exercise(context, world.getServer(), "integrated");
        }
        var properties = new java.util.Properties();
        properties.setProperty("server-ip", "127.0.0.1"); properties.setProperty("online-mode", "false");
        try (var server = context.worldBuilder().createServer(properties); var connection = server.connect()) {
            connection.waitForChunksRender();
            exercise(context, server, "dedicated");
        }
    }
    private static void exercise(ClientGameTestContext context, TestServerContext server, String mode) {
        TimeStopCombatClientGameTest.exercise(context, server, mode);
        var uuid = context.computeOnClient(client -> client.player.getUUID());
        int targetId = server.computeOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            player.setGameMode(GameType.CREATIVE);
            player.connection.teleport(.5, 140, .5, 0, 0);
            if (!ZombieService.grant(player)) throw new AssertionError("Zombie fixture grant failed");
            var cow = EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND);
            cow.snapTo(.5, 140, 2.1, 180, 0); cow.setNoGravity(true);
            cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            cow.setHealth(100); player.level().addFreshEntity(cow);
            return cow.getId();
        });
        context.waitFor(client -> ZombieClient.playable() && client.level.getEntity(targetId) != null);
        context.getInput().pressKey(key(context, "jojo.key.non_stand_mode"));
        context.waitFor(client -> ZombieClient.isMode() && !StandSelection.isStandMode());
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        context.takeScreenshot("zombie-" + mode + "-original-hud");
        context.getInput().holdKey(key(context, "jojo.key.attack_hotbar"));
        context.getInput().pressKey(options -> options.keyHotbarSlots[1]);
        context.getInput().releaseKey(key(context, "jojo.key.attack_hotbar"));
        context.runOnClient(client -> {
            if (!StandSelection.selectedAction(true).equals("zombie_devour")) throw new AssertionError("Original zombie attack row did not select Devour");
        });
        context.getInput().lookAt(0, 15);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> ZombieClient.hud().held() == ZombieAttachments.Held.DEVOUR);
        server.waitFor(minecraft -> ((LivingEntity) minecraft.getPlayerList().getPlayer(uuid).level().getEntity(targetId)).getHealth() < 100);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> ZombieClient.hud().held() == ZombieAttachments.Held.NONE);
        context.getInput().holdKey(key(context, "jojo.key.ability_hotbar"));
        context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        context.getInput().releaseKey(key(context, "jojo.key.ability_hotbar"));
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> ZombieClient.state().disguised(), 100);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> ZombieClient.hud().held() == ZombieAttachments.Held.NONE);
        context.runOnClient(client -> {
            if (!ZombieClient.greenSelection("zombie_disguise") || client.player.getAttachedOrCreate(ZombieAttachments.VISUAL).skin())
                throw new AssertionError("Disguise did not synchronize its original selection and skin state");
            client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        context.takeScreenshot("zombie-" + mode + "-disguise");
        context.getInput().pressKey(key(context, "jojo.key.non_stand_mode"));
        context.waitFor(client -> !ZombieClient.isMode());
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            player.level().getEntity(targetId).discard(); ZombieService.clear(player);
        });
        equipment(context, server, mode);
        pillarman(context, server, mode);
    }
    private static void equipment(ClientGameTestContext context, TestServerContext server, String mode) {
        var uuid = context.computeOnClient(client -> client.player.getUUID());
        int target = server.computeOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            StandService.clear(player);
            player.connection.teleport(.5, 140, .5, 0, 4);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(com.github.standobyte.jojo.fabric.equipment.EquipmentContent.BLADE_HAT));
            var cow = EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND);
            cow.snapTo(.5, 140, 8.5, 180, 0); cow.setNoGravity(true);
            cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            cow.setHealth(100); player.level().addFreshEntity(cow);
            return cow.getId();
        });
        context.waitFor(client -> client.player.getMainHandItem().is(com.github.standobyte.jojo.fabric.equipment.EquipmentContent.BLADE_HAT));
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        context.getInput().holdKey(options -> options.keyShift);
        context.waitFor(client -> client.player.isShiftKeyDown());
        server.waitFor(minecraft -> minecraft.getPlayerList().getPlayer(uuid).isShiftKeyDown());
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                .is(com.github.standobyte.jojo.fabric.equipment.EquipmentContent.BLADE_HAT));
        context.getInput().releaseKey(options -> options.keyShift);
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.takeScreenshot("equipment-" + mode + "-worn-blade-hat");
        server.runOnServer(minecraft -> minecraft.getPlayerList().getPlayer(uuid).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                com.github.standobyte.jojo.fabric.equipment.TommyGunItem.createLoadedStack()));
        context.waitFor(client -> client.player.getMainHandItem().is(com.github.standobyte.jojo.fabric.equipment.EquipmentContent.TOMMY_GUN));
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        context.getInput().lookAt(0, 4);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        server.waitFor(minecraft -> ((LivingEntity)minecraft.getPlayerList().getPlayer(uuid).level().getEntity(target)).getHealth() < 100);
        context.takeScreenshot("equipment-" + mode + "-tommy-gun-firing");
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> !client.player.isUsingItem());
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            player.level().getEntity(target).discard();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
        });
    }
    private static void select(ClientGameTestContext context, boolean attack, int slot) {
        var focus = key(context, attack ? "jojo.key.attack_hotbar" : "jojo.key.ability_hotbar");
        context.getInput().holdKey(focus);
        context.getInput().pressKey(options -> options.keyHotbarSlots[slot]);
        context.getInput().releaseKey(focus);
    }
    /** One connected flow exercises original mode selection, charged input, rendered layers and real host camera. */
    private static void pillarman(ClientGameTestContext context, TestServerContext server, String mode) {
        var uuid = context.computeOnClient(client -> client.player.getUUID());
        int target = server.computeOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            player.setGameMode(GameType.CREATIVE); PillarmanService.grant(player); PillarmanService.setStage(player, 2);
            player.connection.teleport(.5, 140, .5, 0, 15);
            for (int x=-3;x<=3;x++) for(int z=-3;z<=8;z++) player.level().setBlockAndUpdate(new net.minecraft.core.BlockPos(x,139,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            var cow=EntityTypes.COW.create(player.level(),EntitySpawnReason.COMMAND);cow.snapTo(.5,140,8.1,180,0);cow.setNoGravity(true);
            cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);cow.setHealth(500);
            player.level().addFreshEntity(cow);return cow.getId();
        });
        context.waitFor(client -> PillarmanClient.playable() && client.level.getEntity(target)!=null);
        context.getInput().pressKey(key(context,"jojo.key.non_stand_mode"));
        context.waitFor(client -> PillarmanClient.isMode());
        select(context,false,0);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> PillarmanClient.state().stoneForm(),100);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.takeScreenshot("pillarman-"+mode+"-stone-form");
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> !PillarmanClient.state().stoneForm(),100);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        server.runOnServer(minecraft -> PillarmanService.setMode(minecraft.getPlayerList().getPlayer(uuid),Mode.WIND));
        context.waitFor(client -> PillarmanClient.state().mode()==Mode.WIND);
        select(context,false,5);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> PillarWindActions.state(client.player).held()==PillarWindActions.Held.CLOAK);
        context.takeScreenshot("pillarman-"+mode+"-wind-cloak");
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> PillarWindActions.state(client.player).held()==PillarWindActions.Held.NONE);
        server.runOnServer(minecraft -> PillarmanService.setMode(minecraft.getPlayerList().getPlayer(uuid),Mode.HEAT));
        context.waitFor(client -> PillarmanClient.state().mode()==Mode.HEAT);
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        select(context,true,5);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> PillarHeatActions.state(client.player).ticks()>=31,80);
        context.takeScreenshot("pillarman-"+mode+"-heat-charge");
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> PillarHeatActions.state(client.player).phase()==PillarHeatActions.Phase.NONE);
        server.runOnServer(minecraft -> {
            var player=minecraft.getPlayerList().getPlayer(uuid);PillarBodyParts.clear(player);PillarmanService.setMode(player,Mode.LIGHT);
        });
        context.waitFor(client -> PillarmanClient.state().mode()==Mode.LIGHT);
        select(context,true,0);
        context.getInput().holdKey(options -> options.keyShift);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.getInput().releaseKey(options -> options.keyShift);
        context.waitFor(client -> PillarLightActions.state(client.player).phase()==PillarLightActions.Phase.SLASH);
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.takeScreenshot("pillarman-"+mode+"-light-blades");
        context.waitFor(client -> PillarLightActions.state(client.player).phase()==PillarLightActions.Phase.NONE);
        server.runOnServer(minecraft -> {
            var player=minecraft.getPlayerList().getPlayer(uuid);PillarmanService.setMode(player,Mode.NONE);
            player.level().getEntity(target).setPos(.5,140,2.1);player.connection.teleport(.5,140,.5,0,15);
        });
        context.waitFor(client -> PillarmanClient.state().mode()==Mode.NONE);
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        context.getInput().lookAt(0,15);select(context,false,3);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> PossessionClient.isPossessing() && client.getCameraEntity()!=client.player,80);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.takeScreenshot("pillarman-"+mode+"-host-camera");
        context.getInput().holdKey(options -> options.keyShift);
        context.waitFor(client -> !PossessionClient.isPossessing() && client.getCameraEntity()==client.player);
        context.getInput().releaseKey(options -> options.keyShift);
        server.runOnServer(minecraft -> {
            var player=minecraft.getPlayerList().getPlayer(uuid);
            if(player.gameMode()!=GameType.CREATIVE)throw new AssertionError("Possession did not restore creative mode");
            player.level().getEntity(target).discard();PillarmanService.clear(player);
        });
        context.waitFor(client -> !PillarmanClient.playable());
    }
    private static KeyMapping key(ClientGameTestContext context, String name) { return context.computeOnClient(client -> KeyMapping.get(name)); }
}
