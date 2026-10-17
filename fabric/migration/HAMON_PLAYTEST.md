# Draft 16.1 Hamon playtest

Launch `rotptest`. Use a fresh test world; Forge saves are not imported. Existing draft playtest saves are not modified by automated checks. The versioned bundle and its verification manifest live under `drafts/latest`.

## Check the liquid-walking crash fix

Load the existing draft world that crashed; no reset is required. With Liquid Walking learned and energy available, step or fall onto water, then move along its surface and return to shore. Repeat a short lava crossing with sufficient health. Check original energy costs, lava damage and double-sneak release. Test both first and third person. Draft 16.1 restores the original fluid-displacement method and removes the recursive vanilla collision hook; see [fix details](HAMON_LIQUID_WALKING_FIX.md).

## Check the source corrections first

- In **H**, select a skill below the viewport. Its description and Learn button should become visible. Drag a long description and its scrollbar, click prerequisites across stat tabs, and select a learned technique skill through its occupied slot. Compare locked, next-free and later-empty slot hints.
- Try Sendo Wave Kick with each dominant-arm setting and different occupied hands. Check physical hand motion and item placement.
- Apply Hamon Shock, then damage the target. Health damage should remove Shock and restore mob AI immediately; rejected or fully absorbed hits should retain it.
- With the relevant protection skills, touch cactus/berry blocks. Check contact sparks and sound. Every intersected hazardous block uses the original resource check; insufficient energy is drained before normal damage proceeds.
- Try Plant Infusion with a mob in front of a valid plant. It should charge the plant at its plant cost. Organism Infusion should reject an undead target even with a plant behind it. Invalid targets should show the original condition message.
- Use Life Magnetism with leaves in either hand, then only in inventory while both hands are occupied. Held leaves provide the original free-hand exemption; inventory leaves do not. Land while riding the glider and check passenger fall immunity.
- Use a Molotov without nearby fire or inventory fuel, then with ignition available. Failed ignition should not predict a throw or consume the bottle; successful use should consume/spawn once.
- Optional training edge: the final eligible exercise tick counts before day rollover. At breathing 100, raw completion can precede cached bonuses until a source refresh such as reload; this original quirk is retained. Daily reset clears bonuses.

Automation passed these functional boundaries where noted in the source ledger. Your playtest checks usability, motion, audiovisual feedback and gameplay feel.

## Start naturally

1. Find a Hamon Temple in new windswept-hill terrain. Expert cartographers can offer the original temple map under its biome/power probability rules.
2. For a short setup, enable commands and use `/summon jojo:hamon_master ~ ~ ~2`. Right-click the master with an empty hand. Survival should start with novice stats and the two default skills, not maximum training.
3. Press **H**. Check the original introduction, stats, exercises, Strength/Control branches, technique list, tooltips, scrolling and abandonment confirmation at your normal GUI scale.
4. Press **J** to enter Hamon mode. Hold middle mouse to breathe. Watch energy and breathing stability separately. V/B select actions; left/right mouse use them; Shift selects variants. Hold Alt for ordinary item use. Backslash opens the layout editor.

## Progression and abilities

- Mine, run, swim and meditate from the Stats exercise panel. Check exercise progress, completion bonuses and daily breathing progression. Wear the Breath Control Mask to compare its training and breathing effects.
- Train Strength/Control with successful actions. Buy skills with available points; teacher-required skills need a qualified teacher within the original range. A second player can teach through the request/accept flow.
- Choose each of the five techniques across separate characters or Creative resets: Jonathan, Zeppeli, Joseph, Caesar and Lisa Lisa. Check slot requirements, prerequisites, technique bonuses and automatic perks.
- Try overdrives, healing, protection, movement, detector/hypnosis/shock, projectile shield, infusion and technique attacks. Check held-action release, cancellation when opening menus, cooldowns and resource costs in Survival.
- Craft and use gloves, bubble gloves/soap, oil, clackers and Molotovs. Infused projectiles should retain owner attribution. Soap gloves consume their last partial charge and reload from carried soap.
- Try Life Magnetism with both carried leaves and a targeted leaf block. Check steering, passenger positions, shared energy and first-/third-person presentation.
- Test Deep Pass/Crimson Bubble inheritance under the configured death rules. Default `keepHamonOnDeath=true` suppresses ordinary death inheritance outside Hardcore, as in the source.
- Save/rejoin, die/respawn and change dimensions. Learned skills/stats should persist according to configuration; held actions must stop. Hamon and a Stand coexist. Becoming a vampire replaces active Hamon while retaining its original former-user memory.

## Optional shortcuts

```mcfunction
/rotp hamon grant @s
/hamonstat set strength @s 60
/hamonstat set control @s 60
/hamonstat set breathing @s 100
/give @s jojo:breath_control_mask
/give @s jojo:bubble_gloves
/give @s jojo:soap
/give @s jojo:clackers
/give @s jojo:molotov 16
/give @s minecraft:flint_and_steel
/locate structure jojo:hamon_temple
```

These bypass acquisition/training only. Buy the desired skills through H; use Creative reset buttons to try another tree. Return to Survival to judge costs and progression. Raw bubble gloves start empty; reload them with soap.

## Scope boundaries

The draft implements registered Hamon content against the retained 1.16 source, with modern Fabric/API adapters. Dormant source registrations stay dormant. Hermit Purple, Crazy Diamond, Magician's Red and other unported power/world systems cannot supply their cross-system integrations yet. Automated checks cover mechanics and reachable flows; user playtest still determines combat feel and visual acceptance.
