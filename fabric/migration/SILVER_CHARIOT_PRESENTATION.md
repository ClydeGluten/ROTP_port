# Silver Chariot presentation parity

Status: implemented and source-checked; integrated Java compilation, client gameplay and frame comparison await root's shared lane. Silver Chariot remains outside the natural acquisition pool until delivery acceptance. No worker Gradle execution, commits or shared bootstrap edits.

## Integration

`SilverChariotRenderer.initialize()` registers the dedicated manifestation, thrown rapier, flame trail and shared afterimage renderers; original one-tick flame particle; client rapier/finisher replacement; and dash/sweep replacement of held barrage. Common bootstrap must already initialize `SilverChariotActions`, `StandAfterimageEntity` and the independent manifestation factory/attributes.

`SilverChariotRenderer.submitFirstPerson(PoseStack, SubmitNodeCollector, CameraRenderState, float)` returns true for the locally controlled Chariot camera in first person. Root's hand-submit hook calls it alongside HG's entrypoint. This ports the active `ControllerStand.renderStandHands → StandEntityRenderer.renderFirstPerson` route: ordinary action poses, arms-restricted visibility, pitch/body-yaw/eye-height transforms, current armor and rapier flame. The original deprecated separately positioned hand-rendering route is not enabled.

Existing `generateRemainingStandModels` picks up the appended armor model automatically; `convertRemainingResources` picks up `jojo:flame`. No new Gradle task or source set is needed. All textures/icons remain the original bytes through the existing copy pipeline. `silver-chariot-resources.json` records source/target/digest mappings. Optional body and armor glow textures are checked dynamically, preserving resource-pack additions even though those two default files are absent from the original assets.

## Compared source methods and assets

| Original read-only implementation | Fabric implementation / preserved behavior |
| --- | --- |
| `SilverChariotModel` constructor, `HumanoidStandModel.convertLimb` | Strict generated `SilverChariotGeometry`: 22 named parts, 50 cubes, original 128×128 UV, inflation, mirror flags, pivots and hierarchy. Original secondary X rotations are composed with authored ZYX rotations. |
| `SilverChariotArmorLayerModel` constructor | Strict generated independent `SilverChariotArmorGeometry`: 21 parts, 42 cubes. Converter explicitly accepts the original layer marker and literal texture dimension assignments; unknown constructor statements still fail. |
| `SilverChariotModel.prepareMobModel` | Only `rapierBlade` disappears after launch; handle/guard remain. Armor visibility follows server `hasArmor`. |
| `SilverChariotModel.initSummonPoseRotations` | All three literal pose arrays, original 0–127 random-byte selection modulo three, 20-tick summon animation and final five-tick return to idle. Synced age/pose state avoids restarting summon motion for tracking clients. |
| `initBaseIdlePose`, `initIdlePose2Loop`, `StandEntityModel.initIdlePose` | Armed armored, armed unarmored, and unarmed literal poses; sparse breathing overlays; original negative sine interpolation; aiming head rotations and idle body sway. |
| `initActionPoses`, `rapierAnim`, `PosedActionAnimation` | Rapier light start/impact/recovery, including the source Java integer-division easing; dash and ranged launch arm/pitch handling; three-stage sweep and recovery; armed guard. |
| `HumanoidStandModel.initActionPoses`, `ModelPoseSided` | Inherited unarmed four-key jab, mirrored alternating hand, secondary pitch on both arms, recovery, and pitch-limited unarmed block. |
| `StandOneHandedBarrageAnimation`, `ArmsBarrageAnimation`, `ArmBarrageSwing`, `AdditionalBarrageSwing`, `BarrageSwingsHolder` | Two-tick cubic stabbing loop; source hit-rate spawning and precision-derived spread; fractional random starting phase; frame-time advancement ×0.75; removal at 1.5 ticks; original offset/pitch translation and 0.75 alpha. Armor's `isLayerModel` marker suppresses armor barrage duplicates, while body/glow/flame keep their active source behavior. |
| `StandEntityModel.setVisibility`, `HumanoidStandModel.partMissing` | Server arms-only bits, source main-arm choice, first-person reduction and missing Stand body/arms/legs control the same named model branches. |
| `SilverChariotRenderer`, `StandGlowLayer`, `StandLayerGlowLayer` | Original body/armor textures, conditional independent armor, optional full-bright glow and camera opacity. |
| `SilverChariotRapierFlameLayerModel`, `FlameModelRenderer` | Eight source-positioned firelets along blade. Each uses four faces of alternating vanilla animated fire sprites, source width/height scaling, narrowing strips and full-bright lighting. |
| `SCRapierModel`, `SCRapierFlameModel`, `SCRapierRenderer`, `SimpleEntityRenderer` | Original one-cube thrown blade: UV32,72, pivot −0.5X, dimensions1×1×15 and inflation−0.3; interpolated yaw/pitch, original axis inversion, eight firelets and owner-only outline. |
| `SCFlameRenderer`, `FlameRenderer`, `OneTickFlameParticle` | Source initial-position trail at 0.4-block steps, original `magicians_red_flame` sprite, opaque/full-bright one-tick particles. |
| `AfterimageEntity.tick`, `AfterimageRenderer.render` | Shared real entities queue delayed position/rotation and retain source mob distraction/lifespan/serialization behavior. Dedicated renderer draws current original entity pose at delayed location with source light, no invented silhouette/fade, skips local first-person camera origin. |

## Main-side source omissions closed during this plan

The original `StandEntityAction.onClick/afterClick` and inherited action builders require provisional auto-summoning before requirements. `SilverChariotActions.Handler.prepare` uses root's transactional `StandService.prepareSummon`; invalid provisional summons roll back through that shared contract. Light/unarmed request MAIN_ARM, barrage/block ARMS, and dash/sweep/launch/armor FULL. Existing full manifestations stay full. Existing partial arms add requested arms or upgrade to full before requirements, matching source ordering.

`SilverChariotEntity.prepareActionSummon` preserves original bit values7/15/2, partial-arm attack damage −25%, durability −25%, zero precision, shorter light recovery, three-tick base-speed summon lock, gradual weakness after arm→full transition and automatic partial-arm unsummon/fade after task completion. The original synced swing toggle drives inherited unarmed pose side. Synced effective speed/precision drive remote barrage visuals without guessing another player's stamina.

Root's59 config is applied at actual melee/rapier/flame damage boundaries once. Linked damage transfer to the owner does not scale a second time. `staminaFree` handles creative and disabled stamina; Resolve still permits zero-stamina actions while preserving its source debit behavior. Source cooldown/rapier-consumption creative checks remain separate.

## Verification and limits

- `python3 fabric/tools/generate_remaining_stand_models.py --check`: passed, six source models and13 validated outputs. The existing checks cover geometry/UV/hierarchy/conversion strictness; the new independent armor is included.
- `python3 fabric/tools/convert_remaining_resources.py --check`: passed,35 validated outputs. Original active fire resources from37 remain; one source particle entry appended.
- Two existing37 grouped real-world GameTests now allow the initial three-tick summon lock and cover main-arm→both-arms→full transition/weakness. They were edited, not run by this worker.
- Source hygiene and stub scans run before staging. No new mock-only or client test suite was added; root owns consolidated real-input client acceptance.
- No screenshots or matched rendered frames have been reviewed yet. Compilation, render-state submission correctness, first-person control, local/remote armor loss, launch/recovery, fiery sweep, barrage arm images and full entity afterimages require the shared integrated client lane.
- Generic source visibility/skin/audio expansion remains assigned to55; this feature supplies dedicated actual models, layers and source sound event keys through existing shared contracts. This ledger does not claim that future shared phase verified.

No geometry substitutions, generated placeholder textures, new natural Stand entries, or changes to original Forge sources are included.
