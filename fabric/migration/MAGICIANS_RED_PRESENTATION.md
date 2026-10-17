# Magician's Red presentation source ledger

Source conversion complete; root runtime acceptance pending. No Star Platinum geometry or pose substitute.

## Geometry, layers and effects

- Original MagiciansRedModel constructor converts through74 into `MagiciansRedGeometry`:30 parts,42 cubes,128×128 atlas, unchanged hierarchy, UV, mirroring and secondary limb rotations. `MagiciansRedModel` controls exact head/beak/body/limb visibility and real partial-arm mode.
- Original MagiciansRedFlameLayer follows both forearms plus upper/lower legs, inheriting each limb transform. Animated body sprites `entity/stand/magicians_red_fire_0/1` are explicitly included in modern blocks atlas. Fire disappears in water/rain. Optional source-compatible glow texture renders independently.
- Original FlameModelRenderer retains four faces, directional transforms,1.4 width scale,0.45-strip stepping,0.9 narrowing, alternating UV and0.03 depth. Source block fire sprites render projectile flames; body sprites render the layer.
- Normal and small ankhs use all seven authored procedural flame pieces. Small form halves rendered shape; synchronized efficiency scales both forms. Original yaw/pitch and flipped model axes remain.
- Fireball uses original item_icon_mr_fireball model/texture converted into modern item definition. A client-only carrier ItemStack selects that model; no invented registered gameplay item.
- Bind renders repeating3-pixel flame segments over the full owner-target ribbon, with full-span culling bounds. Kick uses two authored arm-tip origins computed from source body/arm/forearm transforms. Owner opacity and invisibility are retained.
- Detector is the original three intersecting cubes with −0.2 deformation and32×32 texture. Source bob, six detection axes, proximity strengths and billboard flames are retained.
- Flame trail uses the already registered original `jojo:flame` particle, emitting at source0.4 spacing. Existing original resource pipeline supplies sprites and sounds.

## Active authored pose families

MagiciansRedAnimation includes both original summon poses; idle and negative sine idle loop; inherited four-jab light sequence with source easing/hand mirror; inherited three-stage heavy windup/perform/recovery; original flame pose used by sustained flame, fireball and both charge/fire forms; original bind pose; three dedicated kick poses, including non-wrapping −180-degree arm rotation and kick recovery; source blocking forearms and head-pitch constraints. Original XRotationModelRenderer secondary pitch is composed after authored rotations.

Remote controlled first person uses active ControllerStand.renderStandHands semantics through `MagiciansRedRenderer.submitFirstPerson(PoseStack,SubmitNodeCollector,CameraRenderState,float)`. Flame actions preserve the original body/head pivot correction; ordinary first person hides body and legs. Deprecated separate-arm renderer remains inactive.

## Client state, controls and sound

Renderer reads authoritative task/phase/ticks/duration, summon mode/variant/animation, visual age, current punch side, owner parts, opacity, control and bind target. Phases0 idle,1 windup,2 perform,3 recovery,4 button hold,5 block,6 broken block. Client never changes gameplay state. Time Stop freezes pose sampling, flame sound and detector loop.

ActionCatalog mirrors heavy→kick replacement and source held replacement. Detector uses alongside-held predicate, preserving original flame/bind/block intent and heartbeat. Root supplies catalog/layout/input/first-person common routing.

Source sound aliases are resolved to actual registry keys: FIRE_BLAST/CROSSFIRE/RED_BIND share `jojo:magicians_red_fire_ability`; FIREBALL is vanilla fire-charge use. Avdol Crossfire/special/bind/Hell2U keys remain distinct.55 StandPerception supplies audience filtering and true spectator/SpiritVision checks.58 owns character-voice configuration dispatch; this feature does not invent its own settings. Detector loop starts on detection transition, follows the source distance volume and stops on lost target/removal; sustained flame preserves each-tick random pitch0.3–0.7 at volume0.5.

## Assets and verification

`magicians-red-resources.json` lists25 original textures/metadata/model/particle resources with SHA-256,30-part/42-cube geometry,13 JoJo event keys and vanilla effects. Existing processResources copies original textures, animation metadata, sounds and language;74 converts the fireball model/modern item definition and existing37 fire resources. The new blocks atlas adds only the two body-fire sprites.

Python model check passes6 models/13 outputs. Proposed complete resource manifest check passes37 outputs. No worker Gradle, screenshot comparison or actual client pass is claimed. Root must accept held flame, both Crossfire forms, fireball, bind/kick, detector, partial summons and controlled first person with remote observers before natural acquisition.
