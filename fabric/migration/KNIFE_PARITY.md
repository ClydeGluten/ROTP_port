# Draft 11: knives

Original Forge sources are read-only. This milestone ports their knife logic into the Minecraft 26.3/Fabric APIs; it does not claim complete original-mod parity.

## Source mapping

| Behavior | Original source | Fabric adapter |
| --- | --- | --- |
| Primary throw | `item/KnifeItem.use` | `KnifeItem.use`: up to eight, Shift one, speed 1.5, first spread 1 and later spread 16, cooldown three ticks per primary knife, Survival consumption, Creative preservation |
| Supplementary volley | `entity/stand/stands/TheWorldEntity.onKnivesThrow` | `KnifeService`: only summoned The World, not Shift, min(remaining stack, 8), originates at Stand eyes minus 0.1 and follows player aim; no stamina or Resolve gate |
| Voice triggers | `TheWorldEntity.onKnivesThrow`, `PlayerUtilCap.knivesThrewTicks` | Transient 80-tick counter, original `dio_one_more` and 100-tick-stop `dio_5_seconds` window (>50 and <=80 ticks remaining) |
| Damage and impacts | `entity/itemprojectile/KnifeEntity.hurtTarget`, `ItemProjectileEntity.onHitEntity/getActualDamage` | Float speed times base damage 2, original Gaussian launch spread, fixed 0.3 target margin, nearest contact only, original rebound movement, critical addition, arrow damage attribution, invulnerability timer preservation, accepted-hit consumption, rejected-hit bounce/fire restoration, Enderman handling |
| Time Stop | `KnifeEntity.tick/onHit/canUpdate` | Five lifetime flight ticks granted by hand throws; entity contact exhausts the budget without damage; thaw traces collision again rather than replaying damage against a remembered target; blocks still collide during grace |
| Pickup and persistence | `ItemProjectileEntity`, `KnifeEntity` | Grounded knife recovery, Creative-only pickup, owner and ordinary arrow state, saved age/remaining flight/texture; ordinary projectile lifetime retained |
| Environment | `KnifeEntity.tick` | Forward outline trace cuts cobweb with drops and 0.8 velocity multiplier; tripwire breaks with drops |
| Recipe and dispenser | `recipes/knife.json`, `KnifeItem` constructor | Iron ingot above wooden rod produces four, stack limit sixteen, original dispenser position/directional lift and recoverable ownerless projectile; dispenser receives no hand-throw grace |
| Tool behavior | `KnifeItem.getDestroySpeed/isCorrectToolForDrops/getAttributeModifiers/useOn` | Singleton +2 mainhand attack damage, cobweb speed 15 and correct drops, plant-family speed 1.5, log stripping without consumption/durability |
| Item appearance | `ClientSetup` count property, original item models | Original one-to-eight stack models/textures; living render owner determines count, ownerless display remains one |
| Projectile appearance | `client/render/entity/renderer/itemprojectile/KnifeRenderer` | Original six quads, UVs, dimensions and rotations; normal, scalpel and fish texture variants retained; no invented random fish chance |
| Stuck knives | `LivingStuckObjects.StuckObjectsTracker`, `KnifeLayer`, vanilla 1.16 `StuckInBodyLayer` | Persistent synchronized living count, transient timer `20 * (30 - count)`, one removed per expiry; original player-only layer, seeded placement and legacy body-part selection |

Survival stack sixteen produces eight player knives plus eight Stand knives. Survival stack eight produces only the primary eight. Creative retains its stack, so even one knife with The World produces one player knife plus one Stand knife. Shift always suppresses the supplementary volley. The primary throw sound has literal zero volume in the original source; this is preserved. The supplementary sound uses volume 0.5. Neither path adds bleeding.

## Platform adaptations

Forge entity ticking flags become server/client tick gates and synchronized remaining flight state. Original entity tracking range four and interval twenty remain. Modern arrows move to an impact point before invoking hit callbacks, so frozen entity contact restores the pre-impact position and velocity. Remaining flight belongs to the projectile's lifetime and never resets when another Time Stop starts. Client correction agrees with the authoritative freeze endpoint. Frozen knife rendering uses the current position/rotation rather than interpolating from the prior flight tick; other frozen entity render behavior is unchanged.

The original Gaussian launch formula and widened float constants are restored explicitly; modern triangular spread and age-dependent hitbox inflation would change volley damage and close-range hit detection. Rejected hits stop tracing after the nearest entity and advance with the original rebound velocity, preventing unintended damage behind a blocking target.

Modern damage hooks retain native armor, shields, invulnerability and PvP checks. Minecraft's current projectile/enchantment integration replaces the old engine methods. The old stack-aware attribute hook becomes a narrow item-stack modifier adapter; standard components and other items retain their existing paths. Removed block materials map to current plant classes/tags. Axe stripping uses the current data-driven strip transforms and does not grant copper scraping/wax removal. Dispenser launch explicitly restores the original center-plus-0.7-facing position and +0.1 vertical direction, replacing newer Minecraft defaults. Recipe tags use Fabric common `c:ingots/iron` and `c:rods/wooden`.

The current renderer uses render states and submitted geometry instead of immediate Forge rendering. Stuck knives preserve original placement randomness and cube bounds while following current player limb poses; the slim arm pivot follows Minecraft's updated model. Stuck-count decay pauses with the entity during Time Stop. Death does not copy the stuck-count attachment; saving preserves count but resets the transient removal timer, matching the original.

## Remaining dependencies and limits

Draft 12 restores the original single-knife Stone Mask activation: one self-damage, reusable knife and no throw cooldown. See VAMPIRISM_PARITY.md. Item tracking used by other unported content is not implemented. Broader voice-line arbitration/configuration, Stand projectile interception/deflection, Time Stop immunity/perception and other combat systems retain the project's previously documented gaps. Hamon remains a later milestone. Forge saves are not imported.

## Verification

Final combined verification passed in 3m 28s (`/tmp/rotp-draft11-verification2.log`): 76 core tests, 146 server GameTests, 29 model checks and ten real-client suites. Actual singleplayer and dedicated checks cover item models/input/volleys, stopped-time collisions, frozen position and render-state agreement, resumed damage/movement and player stuck-knife visuals. Original spread, fixed margin and closest-hit regressions fail before the fixes and pass afterward. Recipe, tool, actual dispenser, equipment count transitions and stuck-count persistence/decay are covered server-side.

The versioned Draft 11 bundle preserves final evidence, physics before/after logs, inspected screenshots, checksums and a playtest guide. Manual playtest remains pending; see `05-06-SUMMARY.md`.
