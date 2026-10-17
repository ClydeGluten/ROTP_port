# Hamon source review — Draft 16 follow-up

The user requested a source-driven review after Draft 15: import missing active behavior and bring existing adaptations closer to the original. The reference is the retained Forge source at upstream commit `72862a5826ed45d1dc26e90b37853097acda35de`. The original `src/` tree remains read-only.

This is a method comparison, not a claim that every original method or visual frame has been verified. Existing parity inventories describe the intended implementation; findings below come from reading both implementations. Commented registrations and interactions requiring unported Stands remain outside this Hamon correction pass.

## Confirmed findings

| ID | Original source method | Draft 15 difference | Disposition |
| --- | --- | --- | --- |
| HSR-01 | `GameplayEventHandler.onLivingDamage` | Taking damage does not remove Hamon Shock. | Restore the post-absorption damage hook for mobs and players; preserve Shock on rejected or fully absorbed hits. |
| HSR-02 | `HamonUtil.cancelDamageFromBlock` / `preventBlockDamage` | Cactus/berry protection checks only once, uses action consumption, and omits master immunity and contact sparks. | Restore overlapping-block iteration, raw resource consumption, charged-organism fallback and source presentation. |
| HSR-03 | `PowerBaseImpl.tick` / `HamonPowerType.tickUser` | Daily rollover runs before the last exercise tick. | Count exercise progress before rolling over the day. |
| HSR-04 | `HamonData.tickExercises`, `updateExerciseAttributes`, `readNBT` | Completion bonuses derive continuously from counters, unlike the original cached bonuses at breathing level 100. | Preserve effective completion state and original refresh sites, including reload. |
| HSR-05 | `HamonSkillsTabGui.mouseClicked` / `mouseDragged`; `HamonSkillDescBox.onDrag` | Clicking description text clears selection; dragging scrolls the tab. | Capture and scroll the description, including its scrollbar. |
| HSR-06 | `HamonSkillsTabGui.selectSkill` | Selecting a skill preserves the previous tab scroll and can hide its description/Learn button. | Reset tab scroll when selecting a skill. |
| HSR-07 | `HamonSkillsTabGui.mouseClicked` | Clicking a prerequisite does not switch to its owning tab. | Select the prerequisite's tab and skill together. |
| HSR-08 | `HamonTechniqueTabGui.mouseClicked`; `HamonTechniqueSlotElement.drawTooltip` | Occupied slots do nothing; every empty slot says it is locked. | Restore slot selection and locked/next-free/later-empty hints. |
| HSR-09 | `KosmXSendoWaveKickHandler.setAnimEnabled` | Kick animation treats main hand as physical right hand. | Resolve physical hand occupancy for left-handed players. |
| HSR-10 | `HamonPlantInfusion.getTargetRequirement`, `HamonOrganismInfusion.checkTarget/perform` | A simultaneous entity/block ray result lets Plant Infusion charge a mob; invalid organisms can pass validation because of a plant behind them. | Preserve the block-only target and cost for Plant Infusion; validate the selected organism before spending. |
| HSR-11 | `HamonPlantInfusion.checkTarget`, `HamonOrganismInfusion.canChargeBlock` | Invalid infusion targets fail without original condition feedback. | Restore `living_plant` and `living_mob` messages. |
| HSR-12 | `HamonLifeMagnetism.checkHeldItems` | Every Life Magnetism cast bypasses the free-hand requirement. | Only leaves held in either hand provide the original exemption. |
| HSR-13 | `LeavesGliderEntity.causeFallDamage` | Inherited vanilla fall handling forwards landing damage to passengers. | Restore the original `false` override without superclass delegation. |
| HSR-14 | `MolotovItem.use/useFire` | The client always predicts a successful throw, even without ignition. | Share read-only ignition predicates; consume fuel and spawn only on the server. |

The corrections are implemented by plans 05-15 through 05-19 and verified in the shared integration lane in 05-20; plan 05-21 records delivery. The independent scoped verification found no blockers across all 14 corrections. Raw counters remain visible, while original cached completion state also controls daily forecast/skip-day behavior where the original methods use it.

## Rejected external suggestions

| Suggestion | Source check and disposition |
| --- | --- |
| Charged Molotovs lack two Sendo wave groups | Rejected: Fabric `MolotovEntity.onHitBlock` already creates both groups. Adding another hook in `HamonCharges.impact` would duplicate them. |
| Bubble Barrier must outlive its global age cap after capturing a rider | Rejected: original `ModdedProjectileEntity.tick` applies the same `tickCount > ticksLifespan()` check; changing this would alter source behavior. |
| Hamon Shock never resets a creeper's fuse | Rejected: the inherited Fabric `VampireCombatService.StunEffect` already resets swell; the action need not repeat it. |
| Clackers should raycast toward their owner | Rejected: original `JojoModUtil.rayTrace` uses the projectile's view vector. The proposed owner-directed ray was not source-equivalent. |
| Failed Molotov server use spends durability and plays throw sound | Rejected portion of HSR-14: server validation already precedes consumption/sound. Only unconditional client success was confirmed. |

## Provenance, ownership and verification

Original/Fabric method pairs are listed above and in the four existing Hamon parity inventories. Evidence comes from direct source reading and, for glider damage propagation and damage-event hook timing, cached Minecraft 26.3 bytecode. Disposition ownership: root owns 05-15 events, 05-18 actions, 05-19 content and integration; the progression worker owns 05-16; the client worker owns 05-17. Verification uses focused regressions inside the existing server/core/client suites, with one root Gradle lane. Unchanged methods are not newly runtime-certified by this review.

## Audit coverage and boundaries

- Progression review compared Hamon data, resources, XP, exercise formulas, teacher acquisition, skill managers, technique slots and persistence with `HamonProgress`, `HamonSkills`, `HamonService` and attachments. It found HSR-03/04; no additional confirmed learning/resource formula mismatch.
- Client review compared original screen tabs, descriptions, technique slots, teaching input and the breath/beat/shock/meditation/windup/rebuff/barrage/kick animation handlers. It found HSR-05 through HSR-09. Runtime rendering and motion remain separate checks.
- Root reviewed original damage-event hooks and block protection, finding HSR-01/02.
- Two actual AntigravityCLI audits used `gemini-4-argon-high`; they reached their print limits without a final response. A bounded continuation with `gemini-3.8-flash-low` returned their findings. The reports and metadata are retained under `reviews/hamon-draft16/`; accepted and rejected suggestions are distinguished above. Their covered action pairs include Cutter, Bubble Cutter, Overdrive Beat, Zoom Punch, Speed Boost, Rebuff, Rope Trap, infusion and Life Magnetism. Content pairs include clacker charging/merging/pickup, Molotov ignition and waves, glider acquisition/fall handling and oil hits.
- A separate AntigravityCLI progression implementation attempt with `gemini-4-argon-medium` timed out without edits. A built-in worker completed that code; the failed attempt is not credited as implementation or verification.
- “REA” was not identifiable among installed commands, skills or MCP tools. Clarification was requested; source comparison proceeded independently without claiming to invoke an unidentified tool.

The existing inventories remain useful for registration coverage: five character techniques, 46 skill definitions and 31 actions. Dormant `syo_barrage`/`cheat_death` definitions remain dormant. Hermit Purple, Crazy Diamond and Magician's Red cross-links require their own Stand ports. Exact animation motion and pixel fidelity still require visual comparison/playtest.

## Final Draft 16 evidence

`/tmp/rotp-draft16-verification3.log`, **BUILD SUCCESSFUL in 1m 1s**. All **205 required server GameTests** and **94 core tests** passed. The focused `HamonClientGameTest` passed in fresh integrated and dedicated worlds; packaging passed **29 model checks**. No unrelated client matrix was repeated.

The verified bundle is `0.2.2-fabric.16-dev+upstream.0.2.2.2`, mod SHA-256 `6e7de829462ebcf9e43decb31437840a70408790ccd9e9853f5d4ddd936e301b`. `drafts/latest` selects it; Draft 15 remains available. Shock, block contacts, progression boundaries, action/content eligibility and client navigation/clip selection have focused assertions. Source message keys and ShortSpark wiring were inspected; perceived feedback and animation remain manual checks. Description-scroll and technique screenshots were inspected without claiming pixel-perfect fidelity. Original `src/` matches HEAD.

## Subsequent playtest correction — Draft 16.1

The user's crash reports exposed a liquid-walking movement adaptation missed by this bounded review and its flat-floor client fixture. Draft 16.1 removes the recursive vanilla fluid predicate and restores the retained original `EntityLiquidWalkingMixin` algorithm at the post-collision movement point. See `HAMON_LIQUID_WALKING_FIX.md` for the cause, actual surface regression and verified delivery. The 14 findings above remain the historical scope of Draft 16, not an exhaustive certification.
