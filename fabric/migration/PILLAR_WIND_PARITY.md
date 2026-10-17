# Pillar WIND parity — 05-31

Implementation is staged for the root-owned combined compile/test lane. No Gradle task or GameTest was run by this worker; runtime parity is not yet verified.

## Original behavior mapped

| Original source | Fabric owner | Preserved behavior |
| --- | --- | --- |
| PillarmanSmallSandstorm | PillarWindActions | WIND mode without an invented stage-4 gate; energy 20, cooldown 10; radius 1.5, damage 6, lifetime 40, speed 1.5, spread 1 |
| PillarmanDivineSandstorm | PillarWindActions | Charge 40 ticks, movement multiplier .2; energy 3 per active tick, emissions on even ticks beginning 40; radius 1.5, damage 2, lifetime 10, speed .9, spread 2 |
| PillarmanWindCloak | PillarWindActions/PillarWindClient | Invisibility and sun resistance refreshed for 5 ticks; energy 2 per effect tick; maximum hold 200, cooldown 100; original cloak energy texture and start/stop particles |
| PillarmanAtmosphericRift | PillarWindActions | Charge 40, movement multiplier 0; energy 3 per active tick, self damage 1 with ordinary hurt immunity; radius .5, damage 2, lifetime 60, speed 1.75, spread 1 |
| PillarmanDivineSandstormEntity and inherited projectile classes | PillarSandstormEntity | Entity piercing, immunity bypass, decay floor .5, block collision termination, hardness/config/permission checks, owner and projectile persistence |
| DivineSandstormRenderer, DivineSandstormParticle, AtmosphericRiftParticle | PillarWindClient | Empty entity renderer with original particle appearance, age frames, lifetime, full brightness and emission rates |
| WindCloakLayer, HamonAuraParticle, HamonAura3PersonParticle | PillarWindClient | Cloak body/hand submission and tracked third-person charge aura |

Source details deliberately retained: cloak increments the hold counter before the maximum check, giving 199 effect/cost ticks; charge emission happens before energy debit; charged attacks require positive energy even in creative mode; only one nearest entity is hit per projectile tick, and entity hits do not consume the projectile. Block hits consume it even when destruction is denied. Particle offsets retain the source negative-octant sampling. The original subclass's client-only block-hit sound branch is unreachable through its server-only parent callback and is not newly activated.

The projectile is a free projectile, not a parent-owned body part. Both vanilla deflection overloads reject deflection; the shared evasion contract also rejects evasion. The original damage knockback factor .1 requires the shared hook below; explicit directional knockback uses radius * .035 and resistance.

## Root-owned integration contract

- Common bootstrap: call `PillarWindActions.initialize()`. This class owns entity, particles, synced state and `jojo:sun_resistance` registration; do not register that effect twice.
- Player tick: call `PillarWindActions.tick(player)` once per server tick. Lifecycle: `clear(player)` on disconnect/transfer/reset, `clearAllRuntime()` at shutdown. Coordinate mutually exclusive holds in the shared action dispatcher. Ordinary damage does not cancel these source actions.
- Input: send `new PillarWindActions.Intent(Action)` or `PillarWindClient.send(Action)`. Enum values are SMALL_SANDSTORM, START_DIVINE, START_CLOAK, START_RIFT, KEEP_HELD, STOP_HELD. Heartbeats sustain holds; release stops them. Apply `movementMultiplier` to client input alongside existing shared movement rules.
- Damage knockback: add `PillarSandstormEntity` to the shared damage-knockback factor hook with multiplier .1.
- Sun resistance: existing sunlight checks recognize its registry ID. Use `PillarWindActions.uncurable(effect)` in shared cure filtering; include sun resistance in tracked effect presentation where appropriate.
- Client bootstrap: call `PillarWindClient.initialize()`. Add `CloakLayer` to player renderers, call `extract(Avatar, AvatarRenderState, float)` during extraction, and `submitHand(PoseStack, SubmitNodeCollector, ModelPart, ModelPart, int)` from the first-person hand submission hook.
- Register `PillarWindGameTests` in the shared GameTest entrypoint.

## Presentation and asset handoff

The existing 05-34 presentation scope must consume `animation(LivingEntity)` (original `pillarman/divine_sandstorm` and `pillarman/atmospheric_rift` paths) and `firstPersonAuraCount(LivingEntity)`. Original charge pose playback and the first-person green pseudo-particle draw are not implemented by this slice. The aura count preserves source integer division: Divine 1, Rift 0.

The resource pipeline must carry original `assets/jojo/particles/divine_sandstorm.json`, `atmospheric_rift.json`, and `hamon_aura_green.json`, the referenced green aura sprites, and `textures/entity/biped/wind_cloak.png`. Storm JSON files reference vanilla explosion frames. Ability sounds retain original registry names.

## Focused verification authored, not executed

Four actual-world GameTests cover WIND eligibility and charge/cost cadence; entity piercing, immunity bypass, decay, serialization and lifetime; terrain hardness and griefing rules; and cloak sunlight protection, maximum hold/cooldown, Rift self damage and heartbeat cleanup. Parent owns the combined compilation and execution evidence. Original Forge source was not modified.
