package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.fabric.client.ActionCatalog;
import com.github.standobyte.jojo.fabric.client.ActionLayouts;
import com.github.standobyte.jojo.fabric.client.StandSelection;
import com.github.standobyte.jojo.fabric.client.StandView;
import com.github.standobyte.jojo.fabric.client.VampireClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real knife activation, original non-Stand controls, vampire/Stand coexistence and both infection paths over packets. */
public final class VampireClientGameTest implements FabricClientGameTest {
    private static final Vec3 START = new Vec3(0.5, 140, 0.5);

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            exercise(context, world.getServer(), "singleplayer");
        }
        var properties = new java.util.Properties();
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("online-mode", "false");
        try (var server = context.worldBuilder().createServer(properties); var connection = server.connect()) {
            connection.waitForChunksRender();
            exercise(context, server, "dedicated");
        }
    }

    private static void exercise(ClientGameTestContext context, TestServerContext server, String mode) {
        var uuid = context.computeOnClient(client -> client.player.getUUID());
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            minecraft.setDifficulty(Difficulty.NORMAL, true);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.connection.teleport(START.x, START.y, START.z, 0, 0);
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 12; z++) {
                player.level().setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.STONE.defaultBlockState());
                player.level().setBlockAndUpdate(new BlockPos(x, 145, z), Blocks.STONE.defaultBlockState());
            }
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(VampireItems.STONE_MASK));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StandItems.KNIFE));
        });
        context.waitFor(client -> client.player.position().distanceTo(START) < 0.01
                && client.player.getItemBySlot(EquipmentSlot.HEAD).is(VampireItems.STONE_MASK)
                && client.player.getMainHandItem().is(StandItems.KNIFE));
        context.getInput().lookAt(0, 0);
        context.runOnClient(client -> {
            client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            ActionLayouts.set(VampireClient.POWER_ID, ActionCatalog.defaults(VampireClient.POWER_ID));
        });
        assertMaskModel(context, false);
        context.waitTicks(3);
        context.takeScreenshot("vampire-" + mode + "-mask-worn");
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitFor(client -> client.gui.screen() != null);
        context.takeScreenshot("vampire-" + mode + "-mask-inventory");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> client.player.getAttachedOrCreate(VampireAttachments.PROGRESS).fullPower()
                && StoneMaskItem.activationTicks(client.player.getItemBySlot(EquipmentSlot.HEAD)) > 0);
        context.runOnClient(client -> {
            if (VampireClient.isMode()) throw new AssertionError("Non-Stand acquisition auto-opened a mode that the source leaves manual");
        });
        assertMaskModel(context, true);
        context.takeScreenshot("vampire-" + mode + "-mask-activated");
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            if (!player.getMainHandItem().is(StandItems.KNIFE) || player.getMainHandItem().getCount() != 1)
                throw new AssertionError("Activating the mask consumed the reusable single knife");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setHealth(player.getMaxHealth());
            player.setDeltaMovement(Vec3.ZERO);
            player.connection.teleport(START.x, START.y, START.z, 0, 0);
        });
        context.waitFor(client -> client.player.getMainHandItem().isEmpty() && client.player.position().distanceTo(START) < .01);
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        context.getInput().pressKey(key(context, "jojo.key.non_stand_mode"));
        context.waitFor(client -> VampireClient.isMode() && !StandSelection.isStandMode());
        context.takeScreenshot("vampire-" + mode + "-blood-hud");
        context.getInput().pressKey(key(context, "jojo.key.hamon_skills_window"));
        context.waitFor(client -> client.gui.screen() instanceof com.github.standobyte.jojo.fabric.client.ProgressionScreen);
        context.runOnClient(client -> {
            String narration = client.gui.screen().getNarrationMessage().getString();
            if (!narration.contains("Full vampire") || !narration.contains("Blood:"))
                throw new AssertionError("Vampire overview narration omitted the synchronized stage and blood: " + narration);
        });
        context.takeScreenshot("vampire-" + mode + "-help");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);

        // V + number selects the original attack row without moving the inventory selection.
        int inventorySlot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
        slot(context, true, 2);
        context.runOnClient(client -> {
            if (!StandSelection.selectedAction(true).equals("vampirism_blood_drain")
                    || client.player.getInventory().getSelectedSlot() != inventorySlot)
                throw new AssertionError("Vampire action selection changed the vanilla hotbar or selected the wrong attack");
        });
        int targetId = server.computeOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            var cow = EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND);
            cow.snapTo(0.5, 140, 2.1, 180, 0);
            cow.setNoGravity(true);
            cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            cow.setHealth(100);
            player.level().addFreshEntity(cow);
            player.setAttached(VampireAttachments.PROGRESS, VampireService.progress(player).withBlood(100));
            return cow.getId();
        });
        context.waitFor(client -> client.level.getEntity(targetId) instanceof LivingEntity);
        context.getInput().lookAt(0, 15);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> client.player.getAttachedOrCreate(VampireAttachments.HUD).held() == VampireHudState.Held.DRAIN);
        context.waitTicks(15);
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            var target = (LivingEntity) player.level().getEntity(targetId);
            if (target.getHealth() == 100) throw new AssertionError("Drain target untouched: held="
                    + player.getAttachedOrCreate(VampireAttachments.HUD) + ", player=" + player.position()
                    + ", rotation=" + player.getYRot() + "/" + player.getXRot() + ", ray=" + VampireService.target(player, 2)
                    + ", target=" + target.position() + ", hp=" + player.getHealth() + ", blood=" + VampireService.progress(player).blood());
        });
        server.waitFor(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            return VampireService.progress(player).blood() > 101
                    && ((LivingEntity) player.level().getEntity(targetId)).getHealth() < 100;
        });
        context.waitFor(client -> client.player.getAttachedOrCreate(VampireAttachments.HUD).held() == VampireHudState.Held.DRAIN);
        context.takeScreenshot("vampire-" + mode + "-drain");
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        server.waitFor(minecraft -> minecraft.getPlayerList().getPlayer(uuid).getAttachedOrCreate(VampireAttachments.HUD).held() == VampireHudState.Held.NONE);
        float health = server.computeOnServer(minecraft -> ((LivingEntity) minecraft.getPlayerList().getPlayer(uuid).level().getEntity(targetId)).getHealth());
        context.waitTicks(15);
        server.runOnServer(minecraft -> {
            var target = (LivingEntity) minecraft.getPlayerList().getPlayer(uuid).level().getEntity(targetId);
            if (target.getHealth() != health) throw new AssertionError("Blood Drain continued after the mouse release packet");
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        });
        // Opening a non-pausing editor must cancel the server hold too.
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> client.player.getAttachedOrCreate(VampireAttachments.HUD).held() == VampireHudState.Held.DRAIN);
        context.getInput().pressKey(key(context, "key.jojo.fabric.layout"));
        context.waitFor(client -> client.gui.screen() instanceof com.github.standobyte.jojo.fabric.client.ActionLayoutScreen);
        server.waitFor(minecraft -> minecraft.getPlayerList().getPlayer(uuid).getAttachedOrCreate(VampireAttachments.HUD).held() == VampireHudState.Held.NONE);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
        slot(context, true, 1);
        float beforeClaw = server.computeOnServer(minecraft -> {
            var target = (LivingEntity) minecraft.getPlayerList().getPlayer(uuid).level().getEntity(targetId);
            target.snapTo(.5, 140, 2.1, 180, 0);
            target.setDeltaMovement(Vec3.ZERO); target.setInvulnerableTime(0);
            return target.getHealth();
        });
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        server.waitFor(minecraft -> ((LivingEntity) minecraft.getPlayerList().getPlayer(uuid).level().getEntity(targetId)).getHealth() < beforeClaw);
        context.waitFor(client -> client.level.getEntity(targetId).position().distanceTo(new Vec3(.5, 140, 2.1)) > .1);
        server.runOnServer(minecraft -> minecraft.getPlayerList().getPlayer(uuid).level().getEntity(targetId).discard());
        server.waitFor(minecraft -> minecraft.getPlayerList().getPlayer(uuid).getAttachedOrCreate(VampireAttachments.HUD).clawTicks() == 0);

        // A connected synthetic recipient receives the real client's sixty-tick Blood Gift.
        var recipientId = server.computeOnServer(minecraft -> {
            var donor = minecraft.getPlayerList().getPlayer(uuid);
            donor.setAttached(VampireAttachments.PROGRESS, VampireService.progress(donor).withBlood(700));
            donor.setHealth(donor.getMaxHealth());
            var id = java.util.UUID.randomUUID();
            var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                    new com.mojang.authlib.GameProfile(id, "gift-" + id.toString().substring(0, 8)), false);
            var recipient = new ServerPlayer(minecraft, donor.level(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            minecraft.getPlayerList().placeNewPlayer(connection, recipient, cookie);
            recipient.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            recipient.setGameMode(GameType.SURVIVAL); recipient.setNoGravity(true);
            recipient.snapTo(.5, 140, 2, 180, 0);
            recipient.setHealth(6); recipient.getFoodData().setFoodLevel(10);
            return id;
        });
        context.getInput().lookAt(0, 0);
        context.waitFor(client -> client.level.getPlayerByUUID(recipientId) != null);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> client.player.getAttachedOrCreate(VampireAttachments.HUD).heldTicks() >= 20);
        context.takeScreenshot("vampire-" + mode + "-blood-gift-charge");
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> client.player.getAttachedOrCreate(VampireAttachments.HUD).held() == VampireHudState.Held.NONE);
        context.waitTicks(45);
        server.runOnServer(minecraft -> {
            if (VampireService.progress(minecraft.getPlayerList().getPlayer(recipientId)).vampire())
                throw new AssertionError("Blood Gift completed after releasing its charge early");
        });
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        server.waitFor(minecraft -> VampireService.progress(minecraft.getPlayerList().getPlayer(recipientId)).vampire(), 100);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        server.runOnServer(minecraft -> {
            var recipient = minecraft.getPlayerList().getPlayer(recipientId);
            if (VampireService.progress(recipient).fullPower()) throw new AssertionError("Blood Gift bypassed the original weak-vampire stage");
            recipient.connection.disconnect(net.minecraft.network.chat.Component.literal("Vampire client test finished"));
            var donor = minecraft.getPlayerList().getPlayer(uuid);
            StandService.grant(donor, StandProgress.THE_WORLD);
            donor.setGameMode(GameType.CREATIVE);
            StandService.toggle(donor);
        });
        context.waitFor(client -> StandView.ownStand(client) != null);
        context.getInput().pressKey(key(context, "jojo.key.stand_mode"));
        context.waitFor(client -> StandSelection.isStandMode() && !VampireClient.isMode());
        context.takeScreenshot("vampire-" + mode + "-stand-coexistence");
        context.getInput().pressKey(key(context, "jojo.key.non_stand_mode"));
        context.waitFor(client -> VampireClient.isMode() && !StandSelection.isStandMode());
        context.waitTicks(45);
        server.runOnServer(minecraft -> {
            if (TimeStopService.isActive(minecraft.getPlayerList().getPlayer(uuid)))
                throw new AssertionError("Original J vampire toggle also triggered the legacy Time Stop shortcut");
            var player = minecraft.getPlayerList().getPlayer(uuid);
            VampireService.clear(player); VampireService.grant(player, false);
        });
        context.runOnClient(client -> ActionLayouts.set(VampireClient.POWER_ID, ActionCatalog.defaults(VampireClient.POWER_ID)));
        context.waitFor(client -> client.player.getAttachedOrCreate(VampireAttachments.PROGRESS).vampire()
                && !client.player.getAttachedOrCreate(VampireAttachments.PROGRESS).fullPower());
        if (!context.computeOnClient(client -> VampireClient.isMode())) {
            context.getInput().pressKey(key(context, "jojo.key.non_stand_mode"));
            context.waitFor(client -> VampireClient.isMode());
        }
        context.runOnClient(client -> {
            if (!StandSelection.visibleActions(true).equals(java.util.List.of("vampirism_blood_drain")))
                throw new AssertionError("Weak vampires expose the locked full-power Claw slot");
        });
        slot(context, true, 1);
        context.runOnClient(client -> {
            if (!StandSelection.selectedAction(true).equals("vampirism_blood_drain"))
                throw new AssertionError("Weak vampire's first visible slot failed to select Blood Drain");
            if (!ActionLayouts.get(StandProgress.THE_WORLD).attacks().contains("light"))
                throw new AssertionError("Vampire selection overwrote the independent Stand action layout");
        });
        context.takeScreenshot("vampire-" + mode + "-weak-drain-only");
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            player.setAttached(VampireAttachments.PROGRESS, VampireService.progress(player).withBlood(1000));
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(false);
            player.setDeltaMovement(Vec3.ZERO);
            player.connection.teleport(START.x, START.y, START.z, 0, 0);
        });
        context.waitFor(client -> client.player.onGround() && !client.player.isCreative()
                && client.player.getAttachedOrCreate(VampireAttachments.PROGRESS).blood() > 900);
        server.waitFor(minecraft -> minecraft.getPlayerList().getPlayer(uuid).onGround());
        context.getInput().holdKey(options -> options.keyShift);
        context.getInput().holdKey(options -> options.keyJump);
        context.waitFor(client -> VampireCombatService.state(client.player).leapCooldown() > 0);
        context.getInput().releaseKey(options -> options.keyJump);
        context.getInput().releaseKey(options -> options.keyShift);
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            if (VampireService.progress(player).fullPower()) throw new AssertionError("Leap fixture lost Blood Gift's weak form");
            player.setNoGravity(true);
        });
        advancedPresentation(context, server, uuid, mode);
    }

    /** Small Draft 13 flow: actual input packets, synchronized phases, live renderers and original resources. */
    private static void advancedPresentation(ClientGameTestContext context, TestServerContext server, java.util.UUID uuid, String mode) {
        int targetId = server.computeOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            StandService.dismiss(player);
            VampireActionPayload.stopAllHeld(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            // The source mask drops after 101 ticks and can be picked up during the earlier gift flow.
            player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(8)).forEach(net.minecraft.world.entity.Entity::discard);
            player.setAttached(VampireAttachments.PROGRESS, VampireService.progress(player).withFullPower().withBlood(1000));
            player.setGameMode(GameType.CREATIVE);
            player.connection.teleport(START.x, START.y, START.z, 0, 15);
            player.setDeltaMovement(Vec3.ZERO);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            var cow = EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND);
            cow.snapTo(.5, 140, 2.1, 180, 0); cow.setNoAi(true); cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); cow.setHealth(1000);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            player.level().addFreshEntity(cow);
            var zombie = new HungryZombieEntity(VampireZombieService.HUNGRY_ZOMBIE, player.level());
            zombie.snapTo(3, 140, 2, 180, 0); zombie.setNoAi(true); zombie.setNoGravity(true); zombie.setOwner(player);
            player.level().addFreshEntity(zombie);
            var coffin = VampireBlocks.COFFINS.get(net.minecraft.world.item.DyeColor.RED);
            var foot = new BlockPos(-3, 140, 3);
            player.level().setBlockAndUpdate(foot, coffin.defaultBlockState().setValue(WoodenCoffinBlock.FACING, net.minecraft.core.Direction.SOUTH));
            player.level().setBlockAndUpdate(foot.south(), coffin.defaultBlockState().setValue(WoodenCoffinBlock.FACING, net.minecraft.core.Direction.SOUTH)
                    .setValue(WoodenCoffinBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
            var mask = new BlockPos(-2, 140, 1);
            player.level().setBlockAndUpdate(mask, VampireBlocks.STONE_MASK.defaultBlockState().setValue(StoneMaskBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
            ((StoneMaskBlockEntity) player.level().getBlockEntity(mask)).setStack(new ItemStack(VampireItems.STONE_MASK));
            VampireBlocks.splashBlood(player.level(), Vec3.atCenterOf(mask), .1);
            return cow.getId();
        });
        context.waitFor(client -> VampireService.progress(client.player).fullPower() && client.level.getEntity(targetId) != null
                && client.player.getMainHandItem().isEmpty() && client.player.position().distanceTo(START) < .01);
        context.runOnClient(client -> {
            var models = client.getModelManager().getBlockStateModelSet();
            var blocks = new java.util.ArrayList<net.minecraft.world.level.block.Block>(VampireBlocks.COFFINS.values());
            blocks.add(VampireBlocks.STONE_MASK); blocks.add(VampireBlocks.AJA_STONE_MASK);
            for (var block : blocks) for (var state : block.getStateDefinition().getPossibleStates()) {
                var model = models.get(state);
                if (model == models.missingModel() || model.particleMaterial().sprite().contents().name().getPath().contains("missing"))
                    throw new AssertionError("Missing original vampire block model: " + state);
            }
            for (var item : java.util.List.of(VampireItems.AJA_STONE_MASK, VampireHamon.SCARF, VampireItems.COFFINS.get(net.minecraft.world.item.DyeColor.RED))) {
                var model = new ItemStackRenderState();
                client.getItemModelResolver().updateForLiving(model, new ItemStack(item), ItemDisplayContext.GUI, client.player);
                var material = model.pickParticleMaterial(RandomSource.create(0));
                if (model.isEmpty() || material == null || material.sprite().contents().name().getPath().contains("missing"))
                    throw new AssertionError("Missing original vampire item model: " + item);
            }
            client.options.setCameraType(CameraType.FIRST_PERSON);
        });
        context.getInput().lookAt(0, 15);
        slot(context, true, 3);
        context.runOnClient(client -> {
            var action = ActionCatalog.move("vampirism_freeze");
            if (!VampireClient.isMode() || !StandSelection.selectedAction(true).equals(action.id()) || !VampireClient.available(action))
                throw new AssertionError("Freeze input unavailable: mode=" + VampireClient.isMode() + ", selected=" + StandSelection.selectedAction(true)
                        + ", hand=" + client.player.getMainHandItem() + ", basic=" + client.player.getAttachedOrCreate(VampireAttachments.HUD)
                        + ", combat=" + VampireCombatService.state(client.player) + ", power=" + VampireService.progress(client.player));
        });
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(10);
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            var target = (LivingEntity) player.level().getEntity(targetId);
            if (!VampireCombatService.state(player).freeze() || !target.hasEffect(VampireCombatService.FREEZE))
                throw new AssertionError("Freeze server state: combat=" + VampireCombatService.state(player) + ", hand=" + player.getMainHandItem()
                        + ", ray=" + VampireService.target(player, 2) + ", position=" + player.position() + ", rotation=" + player.getYRot() + "/" + player.getXRot()
                        + ", target=" + target.position() + ", targetHp=" + target.getHealth() + ", effects=" + target.getActiveEffects());
        });
        context.waitFor(client -> VampireCombatService.state(client.player).freeze()
                && ((LivingEntity) client.level.getEntity(targetId)).hasEffect(VampireCombatService.FREEZE));
        context.waitTicks(3);
        context.takeScreenshot("vampire-" + mode + "-freeze-hands-and-target");
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> !VampireCombatService.state(client.player).freeze());
        slot(context, true, 1);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> client.player.getAttachedOrCreate(VampireAttachments.HUD).clawTicks() > 0);
        context.takeScreenshot("vampire-" + mode + "-claw-hands");
        context.waitFor(client -> client.player.getAttachedOrCreate(VampireAttachments.HUD).clawTicks() == 0);
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        slot(context, true, 4);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> VampireCombatService.state(client.player).eyesChargeTicks() > 0);
        context.takeScreenshot("vampire-" + mode + "-eyes-charge");
        context.waitFor(client -> VampireCombatService.state(client.player).eyesChargeTicks() >= 20);
        context.waitTicks(5);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> VampireCombatService.state(client.player).eyesTicks() > 0
                && !client.level.getEntitiesOfClass(SpaceRipperStingyEyesEntity.class, client.player.getBoundingBox().inflate(32)).isEmpty());
        context.waitTicks(2);
        context.takeScreenshot("vampire-" + mode + "-eyes-beams");
        server.runOnServer(minecraft -> minecraft.getPlayerList().getPlayer(uuid).setItemSlot(EquipmentSlot.HEAD, new ItemStack(VampireHamon.SCARF)));
        context.waitFor(client -> client.player.getItemBySlot(EquipmentSlot.HEAD).is(VampireHamon.SCARF));
        context.waitTicks(3);
        context.takeScreenshot("vampire-" + mode + "-scarf-and-zombie");
        server.runOnServer(minecraft -> {
            var mask = new ItemStack(VampireItems.AJA_STONE_MASK); StoneMaskItem.activate(mask);
            minecraft.getPlayerList().getPlayer(uuid).setItemSlot(EquipmentSlot.HEAD, mask);
        });
        context.waitFor(client -> client.player.getItemBySlot(EquipmentSlot.HEAD).is(VampireItems.AJA_STONE_MASK));
        context.waitTicks(3);
        context.takeScreenshot("vampire-" + mode + "-aja-mask");
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        context.getInput().lookAt(47, 25);
        context.waitTicks(3);
        context.takeScreenshot("vampire-" + mode + "-coffin-and-floor-mask");
        // Fresh client worlds isolate sleep quorum from the server suite's synthetic players.
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            var level = player.level();
            var clock = level.dimensionType().defaultClock().orElseThrow();
            var clocks = minecraft.clockManager();
            long previousTime = clocks.getInstance(clock).totalTicks();
            var head = new BlockPos(-3, 140, 4);
            var coffin = VampireBlocks.COFFINS.get(net.minecraft.world.item.DyeColor.RED);
            var advancement = minecraft.getAdvancements().get(RipplesFabric.id("jojo/coffin_sleep"));
            if (advancement == null) throw new AssertionError("Original coffin advancement failed to load");
            var progress = player.getAdvancements().getOrStartProgress(advancement);
            try {
                player.connection.teleport(-2.5, 140, 4.5, 0, 0);
                coffin.sleepInside(level, head, level.getBlockState(head), player);
                if (!VampireLifecycle.isSleepingInCoffin(player) || progress.isDone())
                    throw new AssertionError("Coffin entry must start sleep without awarding the day-to-night advancement");
                clocks.setTotalTicks(clock, 18000);
                if (VampireLifecycle.sleepToNight(level, clocks, clock) == null || progress.isDone())
                    throw new AssertionError("Night-to-night coffin sleep must meet quorum without awarding advancement");
                clocks.setTotalTicks(clock, 6000);
                VampireLifecycle.sleepToNight(level, clocks, clock);
                if (clocks.getInstance(clock).totalTicks() != 12600 || !progress.isDone())
                    throw new AssertionError("Daytime coffin sleep must skip to 12600 and award original advancement");
            } finally {
                if (player.isSleeping()) player.stopSleepInBed(true, true);
                clocks.setTotalTicks(clock, previousTime);
            }
        });
    }

    private static KeyMapping key(ClientGameTestContext context, String name) {
        return context.computeOnClient(client -> {
            var mapping = KeyMapping.get(name);
            if (mapping == null || mapping.isUnbound()) throw new AssertionError("Missing vampire control " + name);
            return mapping;
        });
    }
    private static void slot(ClientGameTestContext context, boolean attack, int number) {
        var focus = key(context, attack ? "jojo.key.attack_hotbar" : "jojo.key.ability_hotbar");
        context.getInput().holdKey(focus);
        context.getInput().pressKey(options -> options.keyHotbarSlots[number - 1]);
        context.getInput().releaseKey(focus);
    }
    private static void assertMaskModel(ClientGameTestContext context, boolean activated) {
        context.runOnClient(client -> {
            var state = new ItemStackRenderState();
            client.getItemModelResolver().updateForLiving(state, client.player.getItemBySlot(EquipmentSlot.HEAD),
                    ItemDisplayContext.GUI, client.player);
            var particle = state.pickParticleMaterial(RandomSource.create(0));
            var expected = RipplesFabric.id("item/stone_mask" + (activated ? "_activated" : ""));
            if (state.isEmpty() || particle == null || !particle.sprite().contents().name().equals(expected))
                throw new AssertionError("Stone Mask activation resolved the wrong original model: " + particle);
        });
    }
}
