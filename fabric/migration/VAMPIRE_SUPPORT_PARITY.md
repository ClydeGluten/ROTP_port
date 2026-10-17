# Vampire summons, particles, statistics and advancements

The retained Forge `src/` files are read-only references. This slice implements the following original behavior.

## Hungry zombies

`HungryZombieEntity` and its four `entity/ai/Zombie*Goal` classes supply the entity attributes, goals, owner UUID, saved ability marker, owner teams and targeting. The entity has 30 health, 0.3 movement speed and 5 attack damage. Ownership survives saves; an owner who loses vampirism or reaches curing stage four releases it. Modern `Entity.isAlliedTo(Entity)` is final, so a narrow `HungryZombieAllianceMixin` replaces that original override. Only an owner can attach a leash. Owner kills grant no experience. Water does not convert it into a Drowned.

The original follow goal starts at ten blocks, stops at two and only requests paths while within twelve blocks. It does not teleport. Owner-defense goals also require twelve blocks; autonomous villager targeting is suppressed within sixteen blocks of an owner. The original owner-attack goal's explicit `bloodDrain` damage-name filter is preserved. Unarmed attacks retain the armor-coverage roll, blood-drain bonus and healing. Villager/illager conversion retains original difficulty chances: Hard always, Normal one half, Easy one eighth. Hungry-zombie kills skip conversion on Easy, while player Blood Drain can still convert on Easy. The unused `makePersistent` argument and persisted-only `AbilitySummon` marker do not introduce new persistence or daylight immunity. Vanilla Zombie daylight burning remains active.

`VampirismZombieSummon` supplies 100 blood cost, 100 ticks of cooldown and one/two/three summoned zombies by difficulty. Full power and curing stage at most three are required. The source checks whether existing hungry zombies exceed `difficulty * 10` in a sixteen-block horizontal area; its strict greater-than comparison is retained. Its old fixed 0–256 height is adapted to the current dimension build height. Cooldown is saved and synchronized through an attachment. The action does not require an empty hand. Creative mode skips blood expenditure and the additional cooldown as upstream does.

`VampirismUtil.editMobAiGoals` edits only ordinary nearest-player targeting on monster-category mobs. Vampire neutrality ends at curing stage three; retaliation remains available. Iron golems receive the original priority-three vampire-hunting goal, ending at curing stage four. Existing target predicates are composed with the vampire predicate. The original configuration switch remains effective. Evil Atmosphere's Hard-difficulty allied-zombie buffs retain their source durations and levels.

The renderer uses Minecraft's zombie geometry/armor layers with the original hungry-zombie texture. The spawn egg uses the modern vanilla zombie-egg model because the former two-color template no longer exists. `ZombieDevour` and `ZombieDisguise` belong to the separate player Zombie power, not vampirism; they are not introduced as vampire actions.

## Blood particles

`BloodParticle`, `BloodFromEntityParticle` and `BloodParticlesPacket` supply the four original sprites and motion. The port preserves random angular bursts or directed bursts, velocity jitter, sixty-tick lifetime, half scale, gravity starting at age five, attachment to nearby entities with the source excluded, collision stopping, rain acceleration and fluid removal. A single burst packet replaces the old packet class. Camera distance and particle settings retain the original limiter. `SingleQuadParticle`'s translucent layer replaces the removed Forge-era particle renderer interface.

## Statistics, advancements and sledgehammer

`ModCustomStats` and `PeopleDrainedTrigger` supply all four vampire counters. Drain kills distinguish people from animals, count actual zombie conversions, and trigger the original bounded people/zombie predicates. Summoning records the original summoned count. Original custom triggers use modern codecs, including optional player predicates, power classification/type, count bounds and the mask-destruction block/tool/mask predicates. The supplied advancement cards retain their text, icons, hidden flags and rewards, including the 64-human challenge's 100 experience reward.

The original Stone Mask advancement is made the visible root of this branch, with the original JoJo background. Its original parent belongs to unported Pillar Man temple world generation; retaining that missing parent would hide the branch. Acquisition, curing, Aja-mask suicide and scarf/Hamon-damage callbacks preserve their original event conditions. General Hamon-power kill achievements remain dependent on the separate Hamon power integration.

The mask-destruction predicate still requires `jojo:sledgehammer`. `SledgehammerItem` ports the original iron pickaxe material, +9 tier damage, -3.3 attack speed, two durability per mined nonzero-hardness block and one per attack. Modern item/enchantment tags replace Forge's enchanting callback: mining and weapon enchantments remain available, Sweeping Edge remains excluded. Its original shaped recipe, item model and texture are retained.

## Shared integration

Blood particles receive their originating entity so they do not immediately stick to the injured entity. Hamon Spread uses its original affected-entity eligibility. Stun and Hamon Spread retain their original uncurable status alongside Stand Virus and Sun Burn; Bleeding and Freeze remain milk-curable. The original empty `hamon_damage` entity tag is available for data packs, and Hamon damage bypasses armor.

The progression overview uses the synchronized blood maximum and the current player-specific Time Stop cap. Blood-rich vampires can display/train the extended cap; when their blood falls, additional training is retained and shown separately from currently usable training. The operator progression command accepts 0–175 training points. `/rotp vampire hamon_memory <strength>` and `/rotp vampire clear_hamon_memory` are explicit development adapters for the retained-Hamon branch; they do not claim to implement Hamon acquisition or training. The source's full Hamon and Pillar Man power dependencies remain separate subsystems.

`StandPower.addLearningProgressPoints` only clamps an addition when the previous stored value was within the current cap. That source detail also remains: previously trained vampires can continue accumulating raw training above a temporary low-blood cap. Stored values remain finite and nonnegative; usable Time Stop remains capped at 100 or 180 ticks. Vampire stamina/regen multipliers, fixed fivefold Resolve health factor and absent Resolve damage reduction follow the original vampire power methods.

## Retained Hamon

`VampirismHamonSuicide` applies four self-damage every tenth held tick at offset five, adds visible Hamon Spread II for 100 ticks at held tick thirty, and deals 200 self-damage when completed. It does not grant the extra Weakness effect used by a different Hamon Spread helper. Its radius-six blast uses distance from the explosion center, vanilla exposure, the original explosion-immunity check and the retained strength multiplier. Blocks and movement are unaffected by the blast itself. Scarf mitigation, armor bypass, invulnerability-tick handling and living-target reduction follow `DamageUtil.dealHamonDamage`. Retained character techniques select the original breath shout. Curing or losing vampirism on death clears retained Hamon memory along with the removed vampire power.

Accepted hits now use the original orange Hamon sprite, full brightness, Crit-particle motion, damage-scaled tracking emitters and entity-following spark sounds. The current CritParticle constructor is private, so a small SingleQuadParticle adapter reproduces that motion. The full Hamon power's training, current-user scarf immunity and Hamon-wide configuration, and Pillar Man stone-form immunity/reduction, remain dependencies of those separate power subsystems.
