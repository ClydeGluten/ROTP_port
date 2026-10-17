# Equipment parity ledger

Source: original Forge tree at `72862a5826ed45d1dc26e90b37853097acda35de`. Target: pinned Minecraft 26.3 / Java 25. Source remains read-only. Implementation status and runtime verification are separate below.

| Original entry point / inherited method | Fabric implementation | Status |
| --- | --- | --- |
| `BladeHatItem.use`, `CustomModelArmorItem` → `ArmorItem.use` | `equipment.BladeHatItem.use`: sneak equips only an empty head slot; ordinary use throws at 0.75 speed / 0.5 spread; Creative preserves inventory | Implemented; world check pending |
| `BladeHatItem` dispenser constructor | `DISPENSE_BEHAVIOR`: equipment dispensing first, then ownerless projectile at 1.1 speed / 6 spread with 0.1 vertical bias | Implemented; real dispenser check pending |
| `ModArmorMaterials.BLACK_CLOTH`, `ModItems.BLADE_HAT` | `EquipmentContent`: 132 durability, armor 1, enchantability 15, black wool repair; armor tags and Sharpness eligibility hook | Implemented; enchantment integration owned by root |
| `ItemProjectileEntity.onHit`, `onHitEntity`, `getActualDamage`, `playerTouch` | `BladeHatEntity`: source fractional speed × damage (base 6 plus Sharpness), ordinary damage immunity, hit reversal, block sound, owner/ground pickup | Implemented; collision/recovery check pending |
| `ItemNbtProjectileEntity.onHit`, `tickDespawn`, save/read | Exact ItemStack components retained; one durability per owner-thrown impact; recoverable hats do not despawn; age/return/item/owner/ground state saved | Implemented; save/logout check pending |
| `BladeHatEntity.tick`, `canHitEntity`, `getBaseDamage` | Reverse after tick 30, gravity after tick 100, source plant/web/tripwire cutting; own projectiles excluded | Implemented; boundary check pending |
| `BladeHatEntityModel`, `BladeHatArmorModel`, `SimpleEntityRenderer` | `client.equipment.BladeHatRenderer`, generic `BladeHatLayer`: literal source cubes, UVs, pivots, 36°/tick spin, worn offset, outer player hat hiding and armor glint | Implemented; client verification pending |
| `BladeHatSound` | `EquipmentClient.Loop`: entity-following spinning audio, velocity volume, client Time Stop mute, ground/removal/world-change stop | Implemented; client audio check pending |
| `TommyGunItem.use`, `reload`, `consumeAmmo`, `onUseTick`, `inventoryTick` | `TommyGunItem`: 50-round magazine, one iron nugget/round and eight rounds/powder with rounding, two ticks/loaded-round cooldown, one shot/even tick over 100 ticks, recoil and synced three-tick flash | Implemented; ammo/cadence check pending |
| `TommyGunItem.releaseUsing`, `hurtEnemy`, attributes, tooltips | Joseph voice timing and successful Hamon melee reward, +0.5 reach, -2 attack speed, localized reload instructions | Implemented; source-derived behavior |
| `TommyGunBulletEntity` → `ModdedProjectileEntity` → `DamagingEntity` | `TommyGunBulletEntity`: collision before motion, shooter velocity excluded, 2 arrow damage through immunity, soft-block hardness ≤0.3 penetration at ×0.9 speed, death/lifespan removal, pickability/deflection | Implemented; flight/hit checks pending |
| `TommyGunLoopSound`, `TommyGunISTER`, `CustomModelItemISTER`, `PlayerRendererMixin.getArmPose` | Loop follows held-use lifecycle; original item sprite for GUI/ground/fixed; 3D held mesh, swing flip, two fullbright muzzle frames; crossbow hold integration in root | Implemented; client verification pending |
| `ParseGeckoModel`, `tommy_gun.geo.json` | `tools/generate_equipment_mesh.py` retains rotated cube child bones, box/per-face UVs, missing faces, inflation and source item transform: 322 body quads and 36 flash quads | Generated from original; generator completed |
| `ModPaintings`, source painting textures | Two datapack variants + placeable tag: Mona Lisa 2×3 blocks, Hands 1×1 | Implemented; data-loading check pending |
| `recipes/blade_hat`, recipe advancement, weaponsmith global loot modifier | Modern hat recipe and iron-ingot unlock; 5% loaded Tommy gun weaponsmith loot injection | Implemented; resource loading pending |

## Inactive source behavior preserved

- Tommy gun has no durability setting or wear. Its displayed bar is magazine ammunition. No invented crafting recipe or gun enchantment allowance is added.
- `TommyGunBulletEntity.getGravityAcceleration()` returns 1.5, but inherited `constVelocity()` returns true. Gravity remains ineffective exactly as in the source; the tick-five flag is retained.
- The original gun per-shot sound and bullet impact sound calls are commented out. The actual firing sound is the continuous original loop.

## Root integration contract

- Common: call `EquipmentContent.initialize()` once. Add `EquipmentContent.allowsEnchantment(stack, enchantment)` to shared enchantment eligibility where source-specific allowances are applied.
- Client: call `EquipmentClient.initialize()` once. Register `TommyGunRenderer.Unbaked.CODEC` as `jojo:tommy_gun` in the special model mapper. Pair `TommyGunRenderer.begin(ItemOwner, ItemDisplayContext)` and `end()` around item-model update, matching the existing Clackers context hook.
- Attach `new BladeHatLayer<>(this)` to AvatarRenderer, the deepest HumanoidMobRenderer constructor, and ArmorStandRenderer. Call `BladeHatLayer.extract(entity,state)` after humanoid state extraction and `hideOuterHat(model,state)` after player model animation. ArmorStandRenderer uses the common humanoid extraction method.
- Original Tommy gun arm pose is `CROSSBOW_HOLD` whenever the held hand contains the gun and `!entity.isSwinging()`. Minecraft 26.3's melee swing hand is `entity.getCurrentSwing().hand()`.
- Register `EquipmentGameTests` in root test metadata. The worker did not run Gradle; root owns the single build lane.

## Cross-plan dependency

Original `BladeHatItem.use` calls `TrackerItemStack.setAtEntity` for already tracked items. Fabric has no item-tracking capability/service yet. Plan **05-47** must integrate this throw/return path with Crazy Diamond item tracking when that shared service exists. Exact stack component preservation is present, but it does not substitute for updating the tracking service. Full original-mod parity remains open until this dependency and runtime client verification are closed.

## Assets inspected

Original Blade Hat item model; opened projectile and armor PNG references; gun item and flipped display JSON; gun Bedrock geometry including magazine and fire bones; gun sprite/body/two muzzle textures; bullet trace texture; spinning/gun loop sound classes and sound registrations; both painting definitions/textures; original hat recipe/advancement and loaded-gun loot modifier. Texture/sound binaries are reused through the existing original-resource build copy. Blade Hat item/model JSON is owned by the separate resource generator and was not overwritten.

## Verification boundary

Seven focused world tests are written for equip/throw/return/components, wall/save/logout/recovery, real dispenser equip/fallback, reload/cadence/Creative ammo, first-segment hits/immunity/lifespan, soft/hard block collision, and painting dimensions/placeability. Root `/tmp/rotp-rest-foundation-compile3.log` passed main/client/GameTest compilation before the final generic armor-layer extension. That extension and world/client acceptance remain pending root verification. No worker-side runtime result or visual/audio approval is claimed. Root compile diagnostics drove API corrections using cached Minecraft `javap` output. No placeholder models or unrelated replacement items are used.
