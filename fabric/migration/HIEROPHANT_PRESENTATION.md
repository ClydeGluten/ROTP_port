# Hierophant Green presentation source ledger

Dedicated HG implementation prepared for integration; visual/world acceptance is pending. Original Forge tree remains read-only. Target is pinned Minecraft26.3/Fabric/Java25.

## Active source mapping

| Original | Fabric | Coverage |
|---|---|---|
| `HierophantGreenModel` constructor | `HierophantGreenModel.createBodyLayer` | Literal23 bones/30 cuboids, all UVs/mirrors/deformations and original root/head/body/segmented-limb pivots;128×128 texture. |
| HG summon/idle/idle-loop/action poses | `HierophantAnimation` | Original summon20-tick pose with15→20 blend, idle40-tick negative easing, all four splash poses and source0/.6/.85/.9/1 breakpoints, rigid perform/recovery, grapple right-arm pose, inherited guard pitch/clamping. Duplicate right-forearm declaration in source splash4 retains the last rotation. |
| `StandEntityModel.getAnimator/poseStand`, `LegacyStandAnimator`, `ModelPose`, `RotationAngle`, transitions/`PosedActionAnimation` | Renderer extraction + pose sampler | HG has no packaged Gecko animation JSON, so active Java legacy poses apply. Original shortest-angle interpolation, partial bone updates, head tracking and task-to-idle clock preserved. Strings/barrier actions retain their source idle pose rather than invented punch animations. |
| `StandEntityRenderer`, `StandGlowLayer` | `HierophantGreenRenderer` | Source0.9375 size scaling, body glow at full light, summon white overlay/alpha, arms/full visibility, missing-part masks, owner first-person obstruction filtering, source idle sway and accepted independent third-person orbit fade. Skin/visibility resolver hooks are explicit for58/55. |
| `HumanoidStandModel.translateToHand`, inherited held item layer | HG armed model + `ItemInHandLayer` | Original side offset, segmented arm/forearm transform and forearm-pivot cancellation. |
| `ControllerStand.renderStandHands`, `StandEntityModel.setupFirstPersonRotations` | `submitFirstPerson` | Remote-controlled HG uses its actual animated arms in first person; source pitch,180+body-yaw, eye-height transform and player-hand suppression through root hook. |
| `HGStringModel`, `RepeatingModel`, `ExtendingEntityRenderer`, `HGStringAbstractRenderer` | `HierophantProjectileRenderer` | One-pixel-wide eight-pixel repeating cuboid, current endpoint-to-origin extent, owner light/alpha, untrimmed final piece, original normal/glow textures and fullbright glow pass. Same source model for attacks, grapples, fixed barriers and user tether. Segment bounds drive culling. |
| `HGEmeraldModel`, `HGEmeraldRenderer`, `SimpleEntityRenderer` | `HierophantProjectileRenderer` | Original three cuboids/UVs,32×32 texture, yaw/pitch and inverted model axes. Emerald has no original glow layer; plan wording referring to emerald glow is corrected to body/string glow. |
| `HGBarrierEntity.isGlowing/getTeamColor` | `outline(Entity)` + render outline color | Owner-only red ripped-barrier outline, suppressed while time is stopped. Root ORs predicate into shared glow scheduling. |
| `HierophantGreenBarrierDetectionMarker`, inherited `MarkerRenderer` | `HierophantBarrierMarkers` | Actual synchronized rip positions, original barrier icon/additional.png frame, green Stand color,256-block range, original distance-dependent scale and through-block visibility. Current camera rotation/projection maps source billboards into modern HUD extraction. |

The source marker documents an unsynchronized-net FIXME. The port reads the actual tracked barrier's synchronized rip position rather than copying that nonfunctional access path; no client-supplied detection point enters server state.

## Assets and policy handoff

`hierophant-green-resources.json` records hashes/dimensions of24 unchanged original HG resources and the shared marker atlas. The existing build copies original resources; no new artwork, texture replacement or invented animation file is introduced. Main body/glow, string/glow and emerald texture resolution accepts a shared58 resolver.55 installs the visibility/audience predicate and source sound routing. These broader shared plans remain pending and are not claimed complete here.

`HierophantGreenRenderer.initialize()` registers body model layer, body renderer, all four projectile renderers and client held replacement rule. Call once from root client bootstrap. Root HUD calls `HierophantBarrierMarkers.render(GuiGraphicsExtractor,float)` independently of the action menu. Root hand interception calls `HierophantGreenRenderer.submitFirstPerson(PoseStack,SubmitNodeCollector,CameraRenderState,float)` and cancels vanilla hands on true. Root glow scheduling ORs `HierophantProjectileRenderer.outline(Entity)`.

Skin/visibility hooks: `setTextureResolver(BiFunction<Entity,Identifier,Identifier>)`, `setVisibilityPredicate(Predicate<Entity>)`. Defaults use unchanged original resources and entity visibility. Shared policy can resolve Stand owner from `StandManifestation`, `OwnedBodyPartEntity.livingOwner()` or `HGEmeraldEntity.getOwner()`.

## Verification

Source geometry was mechanically converted and checked against its23 bone/30 cuboid count. All24 manifest paths exist and their SHA-256 hashes were generated from unchanged original files. Pinned cached Minecraft jars were inspected with javap for model transforms, armed item state, render submission, camera projection and GUI extraction APIs.

No worker Gradle or visual pass. Root compiles the coherent batch and71 tests summon/splash/grapple/body glow, first-person/remote hands, binding strings, owner tether, anchor/rip markers and integrated/dedicated camera/range. Skin/audience/shader policy remains assigned to55/57/58. HG playability/natural allocation stays disabled until complete delivery acceptance.
