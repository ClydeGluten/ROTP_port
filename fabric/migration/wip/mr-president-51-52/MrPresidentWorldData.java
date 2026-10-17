package com.github.standobyte.jojo.fabric.stand.mrpresident;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Original world capability, stored on the actual room dimension as a persistent level attachment. */
public final class MrPresidentWorldData {
    public record Section(int x,int y,int z){
        public Section {if(y<0||y>15||Math.abs((long)x)>1_874_999||Math.abs((long)z)>1_874_999)throw new IllegalArgumentException("Invalid room section");}
        public BlockPos position(int xOffset,int yOffset,int zOffset){return new BlockPos((x<<4)+xOffset,(y<<4)+yOffset,(z<<4)+zOffset);}
        public boolean contains(BlockPos pos){return pos.getX()>>4==x&&pos.getY()>>4==y&&pos.getZ()>>4==z;}
        public static Section at(BlockPos pos){return new Section(pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4);}
    }
    public record TurtlePosition(ResourceKey<Level> dimension,Vec3 position){
        public TurtlePosition{Objects.requireNonNull(dimension);if(!Double.isFinite(position.x)||!Double.isFinite(position.y)||!Double.isFinite(position.z))throw new IllegalArgumentException("Invalid turtle location");}
    }
    public static final Codec<MrPresidentWorldData> CODEC=CompoundTag.CODEC.xmap(MrPresidentWorldData::load,MrPresidentWorldData::save);
    public static final AttachmentType<MrPresidentWorldData> DATA=AttachmentRegistry.<MrPresidentWorldData>builder().initializer(MrPresidentWorldData::new).persistent(CODEC).buildAndRegister(RipplesFabric.id("mr_president_rooms"));
    private final Map<UUID,Section> rooms=new LinkedHashMap<>();
    private final Map<Section,UUID> owners=new HashMap<>();
    private final Map<UUID,TurtlePosition> positions=new HashMap<>();
    private ServerLevel level;
    public static MrPresidentWorldData get(MinecraftServer server){var level=server.getLevel(MrPresidentRooms.DIMENSION);if(level==null)return null;var data=level.getAttachedOrCreate(DATA);data.level=level;return data;}
    public Section room(UUID turtle){return rooms.get(turtle);}
    public UUID owner(Section section){return owners.get(section);}
    public TurtlePosition lastPosition(UUID turtle){return positions.get(turtle);}
    public int roomCount(){return rooms.size();}
    private void changed(){if(level!=null)level.setAttached(DATA,this);}
    public void remember(Entity turtle){positions.put(turtle.getUUID(),new TurtlePosition(turtle.level().dimension(),MrPresidentTeleporter.returnPosition(turtle)));changed();}
    public Section allocate(UUID turtle){
        var existing=rooms.get(turtle);if(existing!=null)return existing;
        int x=0,y=0,z=0,ring=0;var pos=new Section(0,0,0);
        while(owners.containsKey(pos)){
            if(y<15)y++;else{
                y=0;
                if(x==ring){ring++;x=ring-1;z=1;}
                else if(z==ring){z--;x=-1;}
                else if(x==-ring){x++;z=-1;}
                else if(z==-ring){z++;x=1;}
                else if(x>0&&z>0){x--;z++;}
                else if(x<0&&z>0){x--;z--;}
                else if(x<0&&z<0){x++;z--;}
                else if(x>0&&z<0){x++;z++;}
                else throw new IllegalStateException("Original room allocation traversal lost its ring");
            }pos=new Section(x,y,z);
        }
        rooms.put(turtle,pos);owners.put(pos,turtle);changed();return pos;
    }
    private CompoundTag save(){
        var tag=new CompoundTag();var entries=new ListTag();
        for(var entry:rooms.entrySet()){
            var room=new CompoundTag();room.store("Turtle",UUIDUtil.CODEC,entry.getKey());var section=entry.getValue();
            var pos=new ListTag();pos.add(IntTag.valueOf(section.x));pos.add(IntTag.valueOf(section.y));pos.add(IntTag.valueOf(section.z));room.put("Pos",pos);
            var tracked=positions.get(entry.getKey());if(tracked!=null){room.putString("TurtleDim",tracked.dimension.identifier().toString());var vector=new ListTag();vector.add(DoubleTag.valueOf(tracked.position.x));vector.add(DoubleTag.valueOf(tracked.position.y));vector.add(DoubleTag.valueOf(tracked.position.z));room.put("TurtlePos",vector);}
            entries.add(room);
        }tag.put("Rooms",entries);return tag;
    }
    private static MrPresidentWorldData load(CompoundTag tag){
        var data=new MrPresidentWorldData();
        for(var value:tag.getListOrEmpty("Rooms")){
            if(!(value instanceof CompoundTag room))continue;var uuid=room.read("Turtle",UUIDUtil.CODEC);var coords=room.getListOrEmpty("Pos");if(uuid.isEmpty()||coords.size()!=3)continue;
            try{
                var section=new Section(coords.getInt(0).orElseThrow(),coords.getInt(1).orElseThrow(),coords.getInt(2).orElseThrow());
                if(data.rooms.containsKey(uuid.get())||data.owners.containsKey(section))continue;data.rooms.put(uuid.get(),section);data.owners.put(section,uuid.get());
                var dimension=Identifier.tryParse(room.getStringOr("TurtleDim",""));var vector=room.getListOrEmpty("TurtlePos");
                if(dimension!=null&&vector.size()==3)data.positions.put(uuid.get(),new TurtlePosition(ResourceKey.create(Registries.DIMENSION,dimension),new Vec3(vector.getDouble(0).orElseThrow(),vector.getDouble(1).orElseThrow(),vector.getDouble(2).orElseThrow())));
            }catch(IllegalArgumentException|NoSuchElementException malformed){/* An invalid saved entry cannot select arbitrary coordinates. */}
        }return data;
    }
    public static void initialize(){}
}
