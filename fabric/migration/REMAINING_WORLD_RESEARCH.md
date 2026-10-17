# Remaining world/item research

Original active registry census: `REMAINING_REGISTRY_CENSUS.json`. AntigravityCLI (`gemini-3.8-flash-high`) ran a bounded read-only audit; raw report retained under `reviews/remaining-port/antigravity-world-audit.json`. Its reference to Fabric 1.21.4 is incorrect: this project targets Minecraft 26.3. Treat behavior summaries as leads, not verified source evidence.

## Confirmed registration gaps / planning units

- Blade hat and Tommy gun: original `ModItems`, `ModEntityTypes`, item/projectile classes, armor/item models and loop sounds. Root spot-check corrected external wording: BladeHatItem.use equips while sneaking and throws without sneaking. Preserve dispenser armor-first behavior, tracker attachment and enchantment allowance.
- Meteoric iron/ore/scrap/ingot plus meteorite structure and Stand Arrow recipe/acquisition ecosystem. `ModStructures.METEORITE_BIOMES` selects snowy precipitation outside ocean; other placement/ore rules require direct method review.
- Pillar Man temple and slumbering multi-block/boiling blood belong to the remaining-power implementation and depend on the Pillar Man stage contract.
- Original Mona Lisa and hands painting registrations.
- Walkman/cassettes, recording/copy recipes, container/audio playback and original UI. They are a separate connected feature, not vanilla music discs.
- Polaroid/photo storage, capture/transfer and original display path.
- Stand disc/removers/eject/full-clear variants and item tracking. Read actual power retention/history and owner/component rules before adapting; do not collapse remove/eject/full-clear semantics.
- RPS kid/Boy II Man and Coco Jumbo/Mr President require their Stand-specific state machines, interaction UI and dimension/NPC persistence. Keep these with the corresponding Stand plans rather than treating them as generic mobs.
- Remaining source enchantments, effects/potions, serializers, advancements, loot and gamerules require active-caller mapping. Copying data keys alone does not establish functionality.

## Exclusions

Commented item registrations (Luck/Pluck swords, arrow shard, cocoa gum, tarot deck, photo album/frame, nail clippers, brooch variants, lighter, revolver) remain dormant unless active runtime registration evidence contradicts this. The census intentionally removes comments but does not capture all dynamic registrations; classify them separately.

## Order / ownership

Independent item equipment and paintings may run alongside non-Stand powers. Meteorite and Stand Arrow ecosystem follow the shared item/component and registry contract. Walkman and photo systems have separate client/server payload and disk/storage boundaries. RPS and Mr President require shared Stand abstraction; their acquisition and room/item identity must survive save/rejoin. Root owns common initialization, build registration, game-test registration, final UI routing and package version; domain workers own isolated feature classes/assets/ledgers.
