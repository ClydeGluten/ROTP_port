package com.github.standobyte.jojo.fabric;
import com.github.standobyte.jojo.core.HamonProgress;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
/** Focused integration checks for natural learning, equipment, inheritance and old structure templates. */
public final class HamonContentGameTests {
    private static ServerPlayer user(GameTestHelper test){return VampireGameTests.player(test,"hamon-content",1.5);}
    private static void close(ServerPlayer player){HamonService.clear(player);VampireService.clear(player);player.level().getServer().getPlayerList().remove(player);}
    @GameTest(environment="jojo:vampire_night",setupTicks=5) public void gliderLandingProtectsPassengers(GameTestHelper test){
        var player=user(test);var glider=test.spawn(HamonContentEntities.LEAVES_GLIDER,new Vec3(2.5,2,2.5));
        test.assertTrue(player.startRiding(glider),"Player mounts glider");float health=player.getHealth();
        test.assertFalse(glider.causeFallDamage(40,1,glider.damageSources().fall()),"Original glider landing ignores fall damage");
        test.assertValueEqual(player.getHealth(),health,"Vehicle does not pass landing damage to rider");
        player.stopRiding();player.causeFallDamage(4,1,player.damageSources().fall());
        test.assertTrue(player.getHealth()<health,"Ordinary unmounted player fall damage remains active");
        glider.discard();close(player);test.succeed();
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5) public void molotovIgnitionConsumesOnlySuccessfulThrows(GameTestHelper test){
        var player=user(test);var stack=new ItemStack(HamonItems.MOLOTOV,3);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        test.assertTrue(HamonItems.MOLOTOV.use(player.level(),player,InteractionHand.MAIN_HAND)==net.minecraft.world.InteractionResult.FAIL,"No ignition source rejects throw");
        test.assertValueEqual(stack.getCount(),3,"Rejected throw keeps the bottle");
        var fuel=new ItemStack(Items.FIRE_CHARGE,2);player.setItemInHand(InteractionHand.OFF_HAND,fuel);
        test.assertTrue(HamonItems.MOLOTOV.use(player.level(),player,InteractionHand.MAIN_HAND).consumesAction(),"Carried ignition permits throw");
        test.assertValueEqual(stack.getCount(),2,"Successful throw consumes one bottle");test.assertValueEqual(fuel.getCount(),1,"Successful throw consumes one fire charge");
        test.setBlock(new BlockPos(2,2,2),Blocks.TORCH);
        HamonItems.MOLOTOV.use(player.level(),player,InteractionHand.MAIN_HAND);
        test.assertValueEqual(stack.getCount(),1,"Nearby flame permits next throw");test.assertValueEqual(fuel.getCount(),1,"Nearby flame takes precedence over carried fuel");
        var bottles=player.level().getEntitiesOfClass(MolotovEntity.class,player.getBoundingBox().inflate(3),e->e.getOwner()==player);
        test.assertValueEqual(bottles.size(),2,"Only accepted uses spawn a projectile");bottles.forEach(Entity::discard);
        close(player);test.succeed();
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5) public void teacherAcquisitionAndNoviceVampireMemory(GameTestHelper test){
        var player=user(test);var teacher=test.spawn(HamonContentEntities.HAMON_MASTER,new Vec3(2.5,2,2.5));HamonService.grantMaster(teacher);
        test.assertTrue(HamonService.interactTeacher(player,teacher),"Original teacher awakens Hamon in Survival");
        var hamon=HamonService.state(player);test.assertTrue(hamon.hamon()&&hamon.hasSkill("overdrive")&&hamon.hasSkill("healing"),"New learner receives both default skills");
        test.assertTrue(hamon.strengthLevel()==0&&hamon.controlLevel()==0&&hamon.breathingLevel()==0,"Teacher does not grant master stats to Survival learner");
        VampireService.grant(player,true);test.assertFalse(HamonService.hasHamon(player),"Stone Mask replaces active Hamon");
        test.assertTrue(player.getAttachedOrCreate(VampireHamon.MEMORY).formerUser(),"Even untrained Hamon remains remembered after conversion");
        close(player);teacher.discard();test.succeed();
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5) public void soapPartialChargeReloadAndCreativeBottleCost(GameTestHelper test){
        var player=user(test);var gloves=new ItemStack(HamonItems.BUBBLE_GLOVES);HamonItems.value(gloves,"Ammo",1);player.setItemInHand(InteractionHand.MAIN_HAND,gloves);
        player.getInventory().add(new ItemStack(HamonItems.SOAP));
        test.assertTrue(HamonItems.consumeSoap(player,50)==HamonItems.SoapSource.GLOVES,"Last partial charge remains usable");
        test.assertTrue(HamonItems.ammo(gloves)==500,"Empty gloves refill from inventory soap");
        test.assertTrue(HamonItems.find(player,s->s.is(Items.GLASS_BOTTLE)).getCount()==1,"Reload returns one bottle");
        player.setGameMode(GameType.CREATIVE);var soap=new ItemStack(HamonItems.SOAP);player.setItemInHand(InteractionHand.MAIN_HAND,soap);
        test.assertTrue(HamonItems.consumeSoap(player,1)==HamonItems.SoapSource.BOTTLE&&soap.isEmpty(),"Launcher consumes soap bottles in Creative too");
        close(player);test.succeed();
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5) public void scaledDamageScarfAndRawCharges(GameTestHelper test){
        var player=user(test);HamonService.grant(player);HamonService.setStats(player,HamonProgress.MAX_HAMON_POINTS,0);
        var zombie=test.spawnWithNoFreeWill(EntityTypes.ZOMBIE,new Vec3(3,2,3));zombie.setHealth(20);
        test.assertTrue(VampireHamon.damage(test.getLevel(),zombie,player,1),"Hamon hit accepted");
        test.assertTrue(Math.abs(zombie.getHealth()-13)<.001,"Strength60 multiplies base Hamon damage by7");
        VampireHamon.damageRaw(test.getLevel(),zombie,player,1);test.assertTrue(Math.abs(zombie.getHealth()-12)<.001,"Already scaled charges avoid double multiplication");
        player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(VampireHamon.SCARF));
        test.assertFalse(VampireHamon.damage(test.getLevel(),player,zombie,20),"Scarf fully protects a Hamon user");
        zombie.discard();close(player);test.succeed();
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5) public void lifeMagnetismUsesLeavesAndSharedEnergy(GameTestHelper test){
        var player=user(test);HamonService.grant(player);HamonService.energy(player,1000);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_LEAVES,2));
        test.assertTrue(HamonItems.lifeMagnetism(player,null,150),"Inventory leaves create glider");
        test.assertTrue(player.getVehicle() instanceof LeavesGliderEntity,"Item-created glider mounts user");
        var glider=(LeavesGliderEntity)player.getVehicle();test.assertTrue(player.getMainHandItem().getCount()==1,"Survival spends exactly one leaves block");
        test.assertTrue(glider.getEnergy()==150&&glider.getLeavesBlock().is(Blocks.OAK_LEAVES),"Glider retains initial energy and leaf type");
        glider.tick();test.assertTrue(glider.getEnergy()==200&&HamonService.state(player).energy()==948,"Rider pays the missing52 energy after original2/tick drain");
        player.stopRiding();glider.discard();close(player);test.succeed();
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5) public void crimsonBubbleTransfersEightyPercentAndReleasesItem(GameTestHelper test){
        var player=user(test);HamonService.grant(player);HamonService.setStats(player,10,20);
        var bubble=new CrimsonBubbleEntity(test.getLevel());bubble.setPos(player.position());bubble.setHamonPoints(100,200);test.getLevel().addFreshEntity(bubble);
        var item=new net.minecraft.world.entity.item.ItemEntity(test.getLevel(),player.getX(),player.getY(),player.getZ(),new ItemStack(HamonItems.CLACKERS));test.getLevel().addFreshEntity(item);bubble.putItem(item);
        bubble.tick();test.assertTrue(HamonService.state(player).strengthPoints()==90&&HamonService.state(player).controlPoints()==180,"Crimson Bubble adds80percent of each stored stat");
        bubble.tick();test.assertTrue(bubble.isRemoved()&&!item.isPassenger(),"Spent bubble disappears and releases its retained item");
        item.discard();close(player);test.succeed();
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5) public void originalTempleTemplatesAndNinePieceLayout(GameTestHelper test){
        var manager=test.getLevel().getStructureTemplateManager();
        var building=manager.get(RipplesFabric.id("hamon_temple/building"));test.assertTrue(building.isPresent()&&building.get().getSize().getX()>40,"Original temple building loads through Minecraft data fixing");
        var pieces=new net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder();
        HamonTempleStructure.addPieces(manager,new BlockPos(0,100,0),pieces,net.minecraft.util.RandomSource.create(17));
        test.assertTrue(pieces.build().pieces().size()==9,"One building, four paths and four rock templates retain original layout");
        test.succeed();
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5) public void infusedMolotovCreatesBothGroundedWaves(GameTestHelper test){
        var player=user(test);var level=test.getLevel();
        var bottle=new MolotovEntity(level,player,new ItemStack(HamonItems.MOLOTOV));
        bottle.setPos(test.absoluteVec(new Vec3(3.5,3,3.5)));level.addFreshEntity(bottle);
        bottle.setAttached(HamonCharges.CHARGE,new HamonCharge(1,200,200,player.getUUID().toString(),500,false,true,true,true));
        var block=bottle.blockPosition();level.setBlockAndUpdate(block,Blocks.STONE.defaultBlockState());
        bottle.onHit(new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(block),net.minecraft.core.Direction.UP,block,false));
        var waves=level.getEntitiesOfClass(HamonSendoEntity.class,new net.minecraft.world.phys.AABB(block).inflate(1));
        test.assertTrue(bottle.isRemoved()&&waves.size()==2,"Charged bottle shatters into two Sendo entities");
        test.assertTrue(waves.stream().anyMatch(w->w.radius()==3&&w.waves()==2)&&waves.stream().anyMatch(w->w.radius()==2&&w.waves()==4),"Original two radius/count combinations preserved");
        test.assertTrue(waves.stream().allMatch(w->w.owner()==player),"Blast waves retain shooter attribution");
        waves.forEach(Entity::discard);close(player);test.succeed();
    }
}
