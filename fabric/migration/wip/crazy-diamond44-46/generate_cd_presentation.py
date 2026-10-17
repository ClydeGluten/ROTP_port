from pathlib import Path
import re
repo=Path('/home/alanno/Documents/Soft/ROTP_port')
out=Path('/tmp/rotp-cd44-work')
model=(repo/'fabric/src/client/java/com/github/standobyte/jojo/fabric/client/model/SilverChariotModel.java').read_text()
model=model.replace('SilverChariot','CrazyDiamond')
model=model.replace('import com.github.standobyte.jojo.fabric.client.model.generated.CrazyDiamondArmorGeometry;\n','')
model=model.replace('extends EntityModel<State> {','extends EntityModel<State> implements net.minecraft.client.model.ArmedModel<State> {')
model=model.replace('CrazyDiamondModel(boolean armor)','CrazyDiamondModel()').replace('armor ? CrazyDiamondArmorGeometry.createRoot() : CrazyDiamondGeometry.createRoot()','CrazyDiamondGeometry.createRoot()')
model=model.replace(', "rapier", "rapierBlade"','')
model=model.replace('        var blade = part("rapierBlade"); if (blade != null) blade.visible = state.rapier;\n','')
model=model.replace('Literal independently generated source body/armor geometry; animation addresses original part names.','Original Crazy Diamond cuboids and segmented limbs, generated directly from the authored source model.')
model=model.replace('    public ModelPart part(String name)', '''    @Override public void translateToHand(State state, net.minecraft.world.entity.HumanoidArm arm, com.mojang.blaze3d.vertex.PoseStack pose) {
        String side=arm==net.minecraft.world.entity.HumanoidArm.LEFT?"left":"right";
        pose.translate(arm==net.minecraft.world.entity.HumanoidArm.LEFT?-.0625:.0625,0,0);
        for(String name:new String[]{"body","upperPart",side+"Arm",side+"ForeArm"})part(name).translateAndRotate(pose);
        var forearm=part(side+"ForeArm");pose.translate(-forearm.x/16F,-forearm.y/16F,-forearm.z/16F);
    }
    public void rawRotation(String name,float x,float y,float z,float amount) {
        var p=part(name);p.xRot+=(x-p.xRot)*amount;p.yRot+=(y-p.yRot)*amount;p.zRot+=(z-p.zRot)*amount;
    }
    public ModelPart part(String name)''')
(out/'CrazyDiamondModel.java').write_text(model)
src=(repo/'src/main/java/com/github/standobyte/jojo/client/render/entity/model/stand/CrazyDiamondModel.java').read_text()
angle=re.compile(r'RotationAngle.fromDegrees\((\w+),\s*([^,]+),\s*([^,]+),\s*([^\)]+)\)(\.noDegreesWrapping\(\))?')
def pose(body):
    return ','.join(('raw' if nowrap else 'd')+'("'+part+'",'+','.join(v.strip() for v in (x,y,z))+')' for part,x,y,z,nowrap in angle.findall(body))
poses={}
for var,body in re.findall(r'(\w+)\s*=\s*(?:new ModelPose<>\()?new RotationAngle\[\]\s*\{(.*?)\}',src,re.S): poses[var]=pose(body)
poses['IDLE']=pose(src.split('protected ModelPose<CrazyDiamondEntity> initIdlePose()')[1].split('});')[0])
poses['IDLE2']=pose(src.split('protected ModelPose<CrazyDiamondEntity> initIdlePose2Loop()')[1].split('});')[0])
summon=src.split('protected RotationAngle[][] initSummonPoseRotations()')[1].split('protected void initActionPoses()')[0]
for i,body in enumerate(re.findall(r'new RotationAngle\[\]\s*\{(.*?)\}',summon,re.S)): poses['SUMMON'+str(i)]=pose(body)
prefix='''package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.fabric.client.model.CrazyDiamondModel;
import com.github.standobyte.jojo.fabric.stand.crazydiamond.CrazyDiamondEntity.Kind;
import com.github.standobyte.jojo.fabric.stand.crazydiamond.RestorationSelection.Mode;
import net.minecraft.util.Mth;

/** Literal active source pose tables; wrapped body turns and unwrapped heavy head turns remain distinct. */
public final class CrazyDiamondAnimation {
    private record Rotation(String part,float x,float y,float z,boolean raw) { }
    private static Rotation d(String name,double x,double y,double z){return new Rotation(name,(float)(x*Math.PI/180),(float)(y*Math.PI/180),(float)(z*Math.PI/180),false);}
    private static Rotation raw(String name,double x,double y,double z){var r=d(name,x,y,z);return new Rotation(r.part,r.x,r.y,r.z,true);}
'''
for key,value in poses.items(): prefix+='    private static final Rotation[] '+key+'={'+value+'};\n'
sc=(repo/'fabric/src/client/java/com/github/standobyte/jojo/fabric/client/stand/SilverChariotAnimation.java').read_text()
prefix+='\n'.join(line for line in sc.splitlines() if 'private static final Rotation[] JAB' in line)+'\n'
(out/'CrazyDiamondAnimation.java').write_text(prefix+'}\n')
print('Generated Crazy Diamond model and',len(poses),'authored pose arrays.')
