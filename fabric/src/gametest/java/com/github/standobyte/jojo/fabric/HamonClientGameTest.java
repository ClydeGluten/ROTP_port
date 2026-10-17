package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.HamonProgress;
import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.fabric.client.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real teacher acquisition, original skill/technique UI, held input and renderer smoke over both connection types. */
public final class HamonClientGameTest implements FabricClientGameTest {
    private static final Vec3 START = new Vec3(.5,140,.5);
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            exercise(context,world.getServer(),"singleplayer");
        }
        var properties=new java.util.Properties();
        properties.setProperty("server-ip","127.0.0.1");properties.setProperty("online-mode","false");
        try(var server=context.worldBuilder().createServer(properties);var connection=server.connect()) {
            connection.waitForChunksRender();exercise(context,server,"dedicated");
        }
    }
    private static void exercise(ClientGameTestContext context,TestServerContext server,String mode) {
        var uuid=context.computeOnClient(client->client.player.getUUID());
        int teacher=server.computeOnServer(minecraft->{
            var player=minecraft.getPlayerList().getPlayer(uuid);
            player.setGameMode(GameType.SURVIVAL);player.setNoGravity(true);
            player.connection.teleport(START.x,START.y,START.z,0,0);
            for(int x=-8;x<=8;x++)for(int z=-8;z<=12;z++)player.level().setBlockAndUpdate(new BlockPos(x,139,z),Blocks.STONE.defaultBlockState());
            var master=HamonContentEntities.HAMON_MASTER.create(player.level(),EntitySpawnReason.COMMAND);
            master.snapTo(.5,140,2.5,180,0);master.setNoGravity(true);HamonService.grantMaster(master);player.level().addFreshEntity(master);
            return master.getId();
        });
        context.waitFor(client->client.player.position().distanceTo(START)<.01 && client.level.getEntity(teacher)!=null);
        unlitMolotov(context);
        context.getInput().lookAt(0,15);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client->HamonService.hasHamon(client.player));
        context.runOnClient(client->{if(HamonClient.isMode())throw new AssertionError("Hamon acquisition opened the manual non-Stand mode");});
        server.runOnServer(minecraft->{
            var player=minecraft.getPlayerList().getPlayer(uuid);
            HamonService.setStats(player,HamonProgress.pointsAtLevel(20),HamonProgress.pointsAtLevel(20));
            HamonService.setBreathing(player,20);HamonService.energy(player,1000);
        });
        context.waitFor(client->HamonClient.state().strengthLevel()>=20);
        // Interacting again must open the same original HamonScreen as H.
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client->client.gui.screen() instanceof HamonScreen);
        screenshot(context,"hamon-"+mode+"-intro");
        click(context,-14,70); // Strength tab.
        context.waitTicks(2);
        click(context,183,145); // Zoom Punch, original flexibility tree row 0.
        button(context,"hamon.learnButton");
        context.waitFor(client->HamonClient.state().hasSkill("zoom_punch"));
        screenshot(context,"hamon-"+mode+"-skills");
        click(context,-14,126); // Technique tab.
        context.waitTicks(2);
        slotTooltip(context,44,"hamon.technique_slot.free");
        slotTooltip(context,115,"hamon.technique_slot.locked",30);
        // Rebuff starts below the viewport. Selecting it must reveal its description and Learn button.
        context.runOnClient(client->{var screen=client.gui.screen();screen.mouseScrolled((screen.width-230)/2+203,(screen.height-227)/2+205,0,-8);});
        context.waitTicks(2);
        click(context,203,143);
        selection(context,"rebuff_overdrive","hamon.techniques.tab");
        context.waitTicks(2);
        drag(context,55,65,-12);
        descriptionScrolled(context);
        // Return to the start, then drag the scrollbar in its opposite, downward direction.
        context.runOnClient(client->{var screen=client.gui.screen();screen.mouseScrolled((screen.width-230)/2+55,(screen.height-227)/2+65,0,100);});
        context.waitTicks(2);
        drag(context,209,48,8);
        descriptionScrolled(context);
        screenshot(context,"hamon-"+mode+"-description-scroll");
        click(context,134,103); // Rebuff requires Protection on the Control tab.
        selection(context,"protection","hamon.control_skills.tab");
        click(context,-14,126);
        context.waitTicks(2);
        button(context,"hamon.pick_technique");
        context.waitFor(client->HamonClient.state().technique().equals("jonathan"));
        context.waitTicks(2);
        click(context,203,179); // Overdrive Barrage requires Zoom Punch on the Strength tab.
        context.waitTicks(2);
        click(context,134,103);
        selection(context,"zoom_punch","hamon.strength_skills.tab");
        click(context,-14,126);
        context.waitTicks(8); // Keep this purchase separate from the previous skill double-click window.
        click(context,203,179);
        button(context,"hamon.learnButton");
        context.waitFor(client->HamonClient.state().hasSkill("overdrive_barrage"));
        click(context,115,205); // Clear the selection so the occupied-slot click must restore it.
        context.waitTicks(2);
        click(context,44,129); // Occupied technique slots select their learned skill.
        selection(context,"overdrive_barrage","hamon.techniques.tab");
        server.runOnServer(minecraft->{var player=minecraft.getPlayerList().getPlayer(uuid);HamonService.setStats(player,HamonProgress.pointsAtLevel(40),HamonProgress.pointsAtLevel(40));});
        context.waitFor(client->HamonClient.state().strengthLevel()>=40);
        context.waitTicks(2);
        slotTooltip(context,115,"hamon.technique_slot.free");
        slotTooltip(context,186,""); // Later unlocked empty slots have no hint.
        screenshot(context,"hamon-"+mode+"-technique");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitFor(client->client.gui.screen()==null);
        kickHandVariants(context);
        context.getInput().pressKey(key(context,"jojo.key.non_stand_mode"));
        context.waitFor(client->HamonClient.isMode()&&!StandSelection.isStandMode());
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
        context.waitFor(client->HamonActionService.state(client.player).held().equals("hamon_breath"));
        context.waitTicks(12);
        screenshot(context,"hamon-"+mode+"-breathing-hud");
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
        server.waitFor(minecraft->HamonActionService.state(minecraft.getPlayerList().getPlayer(uuid)).held().isEmpty());
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
        context.waitFor(client->HamonActionService.state(client.player).held().equals("hamon_breath"));
        context.getInput().pressKey(key(context,"key.jojo.fabric.layout"));
        context.waitFor(client->client.gui.screen() instanceof ActionLayoutScreen);
        server.waitFor(minecraft->HamonActionService.state(minecraft.getPlayerList().getPlayer(uuid)).held().isEmpty());
        screenshot(context,"hamon-"+mode+"-layout");
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitFor(client->client.gui.screen()==null);
        int bubble=server.computeOnServer(minecraft->{
            var player=minecraft.getPlayerList().getPlayer(uuid);
            var master=player.level().getEntity(teacher);if(master!=null)master.discard(); // Keep the front camera clear for the mask/pose capture.
            player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(HamonItems.BREATH_CONTROL_MASK));
            var projectile=new HamonProjectileEntity(HamonEntities.BUBBLE,player).configure(0,0,400,.5F);
            projectile.setPos(-1,141,3);player.level().addFreshEntity(projectile);
            var cutter=new HamonProjectileEntity(HamonEntities.BUBBLE_CUTTER,player).configure(0,0,400,.5F);
            cutter.setPos(2,141,3);player.level().addFreshEntity(cutter);
            var crimson=new CrimsonBubbleEntity(player.level());crimson.setHamonPoints(100,100);crimson.setPos(-2,141,4);player.level().addFreshEntity(crimson);
            var glider=HamonContentEntities.LEAVES_GLIDER.create(player.level(),EntitySpawnReason.COMMAND);
            glider.setPos(3,142,5);glider.setNoGravity(true);glider.setEnergy(1000);player.level().addFreshEntity(glider);
            return projectile.getId();
        });
        context.waitFor(client->client.level.getEntity(bubble)!=null && client.player.getItemBySlot(EquipmentSlot.HEAD).is(HamonItems.BREATH_CONTROL_MASK));
        context.getInput().lookAt(0,0);context.waitTicks(3);
        screenshot(context,"hamon-"+mode+"-entities");
        context.runOnClient(client->client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
        context.waitFor(client->HamonActionService.state(client.player).heldTicks()>10);
        screenshot(context,"hamon-"+mode+"-breathing-animation-mask");
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
        server.runOnServer(minecraft->{var player=minecraft.getPlayerList().getPlayer(uuid);StandService.grant(player,StandProgress.THE_WORLD);StandService.toggle(player);});
        context.waitFor(client->StandView.ownStand(client)!=null);
        context.getInput().pressKey(key(context,"jojo.key.stand_mode"));
        context.waitFor(client->StandSelection.isStandMode()&&!HamonClient.isMode());
        context.getInput().pressKey(key(context,"jojo.key.non_stand_mode"));
        context.waitFor(client->HamonClient.isMode()&&!StandSelection.isStandMode());
        context.runOnClient(client->client.options.setCameraType(CameraType.FIRST_PERSON));
        liquidWalking(context,server,mode);
    }
    private static void liquidWalking(ClientGameTestContext context,TestServerContext server,String mode){
        var uuid=context.computeOnClient(client->client.player.getUUID());
        server.runOnServer(minecraft->{
            var player=minecraft.getPlayerList().getPlayer(uuid);
            for(int x=-3;x<=3;x++)for(int z=21;z<=27;z++){
                player.level().setBlockAndUpdate(new BlockPos(x,139,z),Blocks.STONE.defaultBlockState());
                player.level().setBlockAndUpdate(new BlockPos(x,140,z),Blocks.WATER.defaultBlockState());
            }
            player.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
            HamonService.addSkill(player,"liquid_walking");HamonService.energy(player,1000);
            player.setNoGravity(false);player.setOnGround(false);
            player.connection.teleport(.5,141.05,24.5,0,0);
        });
        context.waitFor(client->HamonClient.state().hasSkill("liquid_walking")
                &&client.player.getZ()>24&&client.level.getBlockState(new BlockPos(0,140,24)).is(Blocks.WATER));
        context.waitTicks(20);
        context.runOnClient(client->{
            var player=client.player;
            if(!player.onGround()||player.getY()<140.5||player.getY()>141.2||player.isInWater())
                throw new AssertionError("Client did not remain supported on the Hamon water-walking surface");
            player.setOnGround(false);
            player.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(0,-.08,0));
            if(!player.onGround())throw new AssertionError("Client airborne fluid movement did not restore grounding safely");
        });
        server.runOnServer(minecraft->{
            var player=minecraft.getPlayerList().getPlayer(uuid);
            if(!HamonMovement.isWalkingOnLiquid(player)||HamonService.state(player).energy()>=1000)
                throw new AssertionError("Server did not retain and charge actual water-walking contacts");
        });
        screenshot(context,"hamon-"+mode+"-liquid-walking");
    }
    private static void screenshot(ClientGameTestContext context,String name){
        context.runOnClient(client->{client.gui.toastManager().clear();client.gui.hud.getChat().clearMessages(false);});
        context.waitTicks(2);context.takeScreenshot(name);
    }
    private static void selection(ClientGameTestContext context,String skill,String tab){context.runOnClient(client->{
        var screen=client.gui.screen();
        if(!skill.equals(field(screen,"selected"))||!screen.getNarrationMessage().getString().startsWith(Component.translatable(tab).getString()))
            throw new AssertionError("Skill navigation did not reveal "+skill+" on "+tab);
        int activeTab=(int)field(screen,"tab");
        if(((int[])field(screen,"scroll"))[activeTab]!=0)throw new AssertionError("Selected skill remained below the viewport");
        var learn=(Button)field(screen,"learn");
        if(learn.visible&&learn.getY()!=(screen.height-227)/2+92)throw new AssertionError("Selected skill's Learn button remained scrolled out of view");
    });}
    private static void descriptionScrolled(ClientGameTestContext context){
        selection(context,"rebuff_overdrive","hamon.techniques.tab");
        context.runOnClient(client->{if(((Number)field(client.gui.screen(),"descriptionScroll")).doubleValue()<=0)
            throw new AssertionError("Dragging did not scroll the selected skill description");});
    }
    private static void drag(ClientGameTestContext context,int x,int y,int dy){context.runOnClient(client->{
        var screen=client.gui.screen();double mx=(screen.width-230)/2.0+x,my=(screen.height-227)/2.0+y;
        var info=new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0);
        screen.mouseClicked(new MouseButtonEvent(mx,my,info),false);
        var moved=new MouseButtonEvent(mx,my+dy,info);
        screen.mouseDragged(moved,0,dy);screen.mouseReleased(moved);
    });}
    private static void slotTooltip(ClientGameTestContext context,int x,String key,Object...args){context.runOnClient(client->{
        var screen=client.gui.screen();int mx=(screen.width-230)/2+x,my=(screen.height-227)/2+129;
        var area=((java.util.List<?>)field(screen,"areas")).stream().filter(a->mx>=(int)field(a,"x")&&mx<(int)field(a,"x")+(int)field(a,"w")
                &&my>=(int)field(a,"y")&&my<(int)field(a,"y")+(int)field(a,"h")).findFirst().orElseThrow(()->new AssertionError("Missing rendered technique slot"));
        var tooltip=(java.util.List<?>)field(area,"tooltip");
        String actual=tooltip.stream().map(line->((Component)line).getString()).collect(java.util.stream.Collectors.joining("\n"));
        String expected=key.isEmpty()?"":Component.translatable(key,args).getString();
        if(!actual.equals(expected))throw new AssertionError("Technique slot hint: expected '"+expected+"', got '"+actual+"'");
    });}
    private static Object field(Object object,String name){
        try{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
        catch(ReflectiveOperationException error){throw new AssertionError("Cannot inspect client result "+name,error);}
    }
    private static void kickHandVariants(ClientGameTestContext context){context.runOnClient(client->{
        var player=client.player;var oldOption=client.options.mainHand().get();var oldArm=player.getMainArm();
        var main=player.getMainHandItem();var off=player.getOffhandItem();var action=HamonActionService.state(player);
        String[][] clips={{"sendo_wave_kick","sendo_wave_kick_r","sendo_wave_kick_l","sendo_wave_kick_lr"},
                {"sendo_wave_kick","sendo_wave_kick_l","sendo_wave_kick_r","sendo_wave_kick_lr"}};
        try{
            player.setAttached(HamonActionService.STATE,new HamonActionState("",0,"zeppeli_sendo_wave_kick",8,0,false,false,0,java.util.Map.of(),java.util.List.of(),""));
            for(int handed=0;handed<2;handed++){
                var arm=handed==0?HumanoidArm.RIGHT:HumanoidArm.LEFT;client.options.mainHand().set(arm);player.setMainArm(arm);
                for(int items=0;items<4;items++){
                    player.setItemSlot(EquipmentSlot.MAINHAND,(items&1)==0?ItemStack.EMPTY:new ItemStack(Items.STICK));
                    player.setItemSlot(EquipmentSlot.OFFHAND,(items&2)==0?ItemStack.EMPTY:new ItemStack(Items.STICK));
                    var state=new AvatarRenderState();HamonPlayerAnimation.extract(player,state,0);
                    if(!HamonPlayerAnimation.pose(state).clip().equals(clips[handed][items]))throw new AssertionError("Wrong kick pose for "+arm+" main arm, item combination "+items);
                }
            }
        }finally{
            client.options.mainHand().set(oldOption);player.setMainArm(oldArm);
            player.setItemSlot(EquipmentSlot.MAINHAND,main);player.setItemSlot(EquipmentSlot.OFFHAND,off);player.setAttached(HamonActionService.STATE,action);
        }
    });}
    private static void unlitMolotov(ClientGameTestContext context){context.runOnClient(client->{
        var inventory=client.player.getInventory();
        var saved=java.util.stream.IntStream.range(0,inventory.getContainerSize()).mapToObj(inventory::getItem).toList();
        try{
            inventory.clearContent();
            var bottle=new ItemStack(HamonItems.MOLOTOV);client.player.setItemSlot(EquipmentSlot.MAINHAND,bottle);
            if(HamonItems.MOLOTOV.use(client.level,client.player,InteractionHand.MAIN_HAND)!=InteractionResult.FAIL
                    ||client.player.getMainHandItem()!=bottle||bottle.getCount()!=1)
                throw new AssertionError("Client must reject an unlit Molotov and retain its stack without a fire source");
        }finally{for(int slot=0;slot<saved.size();slot++)inventory.setItem(slot,saved.get(slot));}
    });}
    private static KeyMapping key(ClientGameTestContext context,String name){return context.computeOnClient(client->{var key=KeyMapping.get(name);if(key==null||key.isUnbound())throw new AssertionError("Missing original control "+name);return key;});}
    private static void click(ClientGameTestContext context,int x,int y){context.runOnClient(client->{var screen=client.gui.screen();screen.mouseClicked(new MouseButtonEvent((screen.width-230)/2+x,(screen.height-227)/2+y,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0)),false);});}
    private static void button(ClientGameTestContext context,String key){context.runOnClient(client->{
        var label=Component.translatable(key).getString();
        var button=client.gui.screen().children().stream().filter(w->w instanceof Button b&&b.visible&&b.active&&b.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow(()->new AssertionError("No enabled Hamon button "+key));
        client.gui.screen().mouseClicked(new MouseButtonEvent(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0)),false);
    });}
}
