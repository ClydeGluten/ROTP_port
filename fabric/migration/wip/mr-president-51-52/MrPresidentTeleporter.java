package com.github.standobyte.jojo.fabric.stand.mrpresident;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Exact source room entry and loaded-owner/remembered-position return, without portal sounds. */
public final class MrPresidentTeleporter {
    public record ReturnTarget(ServerLevel level,Vec3 position,Entity turtle){}
    public static Vec3 returnPosition(Entity turtle){return new Vec3(turtle.getX(),turtle.getY(1),turtle.getZ());}
    public static ReturnTarget returnTarget(MinecraftServer server,UUID room){
        if(room==null)return null;
        for(var level:server.getAllLevels()){var turtle=level.getEntity(room);if(turtle!=null)return new ReturnTarget(level,returnPosition(turtle),turtle);}
        var data=MrPresidentWorldData.get(server);var saved=data==null?null:data.lastPosition(room);if(saved==null)return null;
        var level=server.getLevel(saved.dimension());return level==null?null:new ReturnTarget(level,saved.position(),null);
    }
    public static Entity enter(Entity entity,UUID turtle){
        if(!(entity.level() instanceof ServerLevel source)||entity.isRemoved())return null;var server=source.getServer();var world=server.getLevel(MrPresidentRooms.DIMENSION);var data=MrPresidentWorldData.get(server);if(world==null||data==null)return null;
        var section=data.room(turtle);if(section==null){section=data.allocate(turtle);MrPresidentRooms.generate(world,section);}
        var corner=section.position(0,0,0);return entity.teleport(new TeleportTransition(world,new Vec3(corner.getX()+8,corner.getY()+6,corner.getZ()+8),entity.getDeltaMovement(),entity.getYRot(),entity.getXRot(),TeleportTransition.DO_NOTHING));
    }
    public static Entity exit(Entity entity,UUID room){
        if(!(entity.level() instanceof ServerLevel source)||entity.isRemoved())return null;var target=returnTarget(source.getServer(),room);if(target==null)return null;
        var result=entity.teleport(new TeleportTransition(target.level,target.position,entity.getDeltaMovement(),entity.getYRot(),entity.getXRot(),TeleportTransition.DO_NOTHING));
        if(result!=null&&target.turtle!=null)MrPresidentRoomAccess.quit(target.turtle,result.getUUID());return result;
    }
    private MrPresidentTeleporter(){}
}
