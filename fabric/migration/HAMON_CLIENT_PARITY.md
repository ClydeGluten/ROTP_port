# Hamon client migration inventory

This maps the original Forge client to the Fabric 26.3 draft. The original `src/` tree remains read-only. Renderer and GUI calls use Minecraft's extracted render states; the texture files, localization, animation JSON, model cubes and UVs come from the original tree. This document distinguishes implemented presentation from visual verification; compilation alone does not establish pixel parity.

| Original component | Fabric implementation | Preserved behavior |
| --- | --- | --- |
| `HamonScreen`, `HamonIntroTabGui` | `HamonScreen` | 230×227 frame, original 212×200 clipped content, side tab art, original explanatory text, expanding breath-stability section, animated original energy bars, persistent read state, scroll/drag and H close |
| `HamonStatsTabGui`, `HamonAbandonTabGui` | `HamonScreen` | Strength/control/breathing bars, four implemented daily exercises, completion buffs and forecast, meditation click, training-mask description, deterioration/gap configuration, abandon confirmation |
| `HamonGeneralSkillsTabGui`, `HamonSkillsTabGui`, skill elements | `HamonScreen`, `HamonSkills` | Three original branches per stat, original tiers/coordinates, sprite frames for availability/learned/locked states, prerequisite icons that select their owning tab, scrolling description capture and scrollbar, scroll reset on skill selection, teacher requirement, learning packet and creative reset |
| `HamonTechniqueTabGui`, technique slot/box elements | `HamonScreen`, `HamonSkills` | Original configurable technique slots, occupied-slot selection and locked/next-free/later-empty hints, five registered characters, perk icons, original skill ordering, teacher and point requirements, optional technique mixing; metadata retains inaccessible definitions without making them learnable |
| `InputHandler`, original action overlay | `HamonClient`, `StandSelection`, `ActionCatalog`, `ActionHud`, `HamonHud` | Shared J non-Stand mode, H skills/teaching, middle-button breathing, V/B action focus, number selection/wheel traversal, Alt bypass, Shift variants, held START/KEEP/RELEASE/CANCEL, cooldown/charge display and energy/stability fills |
| Hamon/Stand coexistence | `VampireClient`, `StandSelection`, `ActionLayouts`, `ProgressionScreen` | Stand and non-Stand modes close each other; acquisition leaves mode manual; existing Stand layouts and legacy controls remain compatible; rows larger than nine retain all original actions and scroll in the layout editor |
| `HamonUtil.interactWithHamonTeacher`, learner hints | `HamonClient`, `HamonService` | Right-click an existing teacher to open HamonScreen, H request/accept player teaching, original target and pending-learner overlay messages, synchronized nearby teacher skills |
| Meditation input restrictions | `StandInputMixin`, `StandSelectionInputMixin` | No movement/actions during meditation, movement after 40 ticks cancels it, original common hitbox timing, stun/immobilize cancellation, wall jump and rebuff movement cancellation |
| KosmX Hamon animation handlers | `HamonPlayerAnimation`, `HamonPlayerModelMixin` | Unchanged sparse original JSON keyframes and easing, limb bend geometry, breath, beat speed 2.25, shock speed 2, meditation, SYO/Scarlet windup/attack frames, barrage/finisher phases, wave-kick variants, original fade durations and handedness where handlers use it; half-cycle wall transitions/contact sparks and original phased barrage arm afterimages via `HamonWallAnimation`/`HamonBarrageLayer` |
| `HamonMasterModel/Renderer` | `HamonMasterRenderer` | Original skin, extra robe cubes/pivots/UVs, original meditation loop starting at return tick 35 at half speed |
| `HamonProtectionLayer`, breath-control-mask armor | `HamonProtectionLayer`, `HamonMaskLayer`, `FrozenHandMixin` | Original energy texture scroll, third-person body and first-person hand overlay, original mask cubes/deformation/UVs, replacement of flat head-item rendering |
| Bubble/cutter/barrier/zoom/snake renderers | `HamonProjectileRenderer` | Original meshes and UVs, source cutter rotation, bubble size variation, stretching owner skin, repeated snake segments, particle-only Turquoise Blue wave |
| `HamonProjectileShieldRenderer` | `HamonShieldRenderer` | Original 8×4 double-sided shield and fullbright texture |
| `HamonSendoOverdriveRenderer/Particle` | `HamonParticleClient`, `HamonPresentationClient` | Invisible wave entity, expanding face-constrained sparks, original axis choice with two ±0.55 planes, PI×0.2/radius angular step, nearest-box-position spark sound and 15-tick particle lifetime |
| `AfterimageRenderer` | `HamonAfterimageRenderer` | Delegates the source owner's renderer at the delayed entity position, source speed gating and first-person local suppression; original source uses ordinary owner rendering, not added translucency |
| `LeavesGliderModel/Renderer` | `HamonContentModels`, `HamonContentRenderer`, `HamonGliderPresentation` | Original leaf geometry, block model particle texture, biome foliage tint, flight loop/fade, charge sparks, health-loss and removal foliage particles/sounds, busy hands, standing passenger legs, local passenger cannot target their glider |
| `ClackersModel`, `ClackersISTER` | `HamonContentModels`, `HamonClackersRenderer`, item owner/special model mixins | Original string/ball mesh, charge oscillation and walk swing, ordinary inventory sprite. Source first-person method is empty and stays empty; commented source charge sounds stay disabled |
| `CrimsonBubbleModel/Renderer` | `HamonContentModels`, `HamonContentRenderer` | Original translucent multi-cube bubble, dimensions and UV layout |
| `MolotovRenderer` | `HamonMolotovRenderer` | Original fullbright item sprite scale, alternating narrowing flame quads, rain/water extinguish the flame visual |
| Original aura families and charge particles | `HamonParticles`, `HamonParticleClient`, `HamonPresentationClient` | Orange/blue/yellow/red/silver sprite lists and original aura alpha/lifetime; tracked last-used action colors, energy/strength particle density, charged entities and blocks with short spark sounds, server-confirmed liquid-walk initial/per-tick foot sparks |
| Original advancement logo | `HamonLogoRenderer`, `HamonLogoModelMixin` | Original geo mesh, texture and item transform; implemented by progression owner |
| Hamon master spawn egg | `jojo:item/template_spawn_egg` | Exact 1.16.5 vanilla generated-item template and its two original layer sprites, retaining original Hamon master colors. Retrieved from Mojang's official 1.16.5 client, SHA-1 `37fd3c903861eeff3bc24b71eed48f828b5269c8`, because modern Minecraft removed the shared egg template |

## Verification

`HamonClientGameTest` is one cohesive script, repeated against a fresh integrated server and fresh dedicated server. It exercises actual right-click NPC acquisition, reopening the screen, Zoom Punch learning through the original grid/button, Jonathan selection through the technique tab, J/MMB input, release and editor cancellation, representative entity rendering, third-person breath/mask rendering, and Stand coexistence. It saves screenshots for the intro, learned skill, selected technique, breathing HUD, layout editor, entity fixtures and equipped-mask animation. Parent agent owns build execution and final test evidence. The final `verification8` lane passed compilation, all 197 required common GameTests and the focused integrated/dedicated client script with the final animation and particle methods included (`/tmp/rotp-draft15-verification8.log`, BUILD SUCCESSFUL). Manual motion comparison remains a separate visual-quality check.

## Modern adaptations and visual limits

- The fixed original tab/window geometry and assets are preserved. Font metrics, vanilla button skins, narration and tooltip extraction come from Minecraft 26.3. A renderer screenshot comparison is still needed for pixel-level certification.
- The original optional KosmX dependency is replaced by a local JSON sampler plus bend geometry. Active poses, timing, fades, wall transition-at-half-cycle state, alternating hand contact sparks and layered barrage arm afterimages are implemented. Wall motion is read from the existing synchronized entity motion instead of duplicating the old Forge motion-animation packet. A dedicated motion comparison remains validation work, not an absent renderer.
- Protection uses a separate regular player model with the original 0.20 cube deformation, original energy texture offsets, tint/alpha and fullbright light. Modern sleeves are child parts rather than separate Forge fields; the first-person submission includes their geometry through that hierarchy.
- Third-person limbs and held projectiles render through the modern model pipeline. Glider charge sparks use the original spark sprites at hand positions; the old custom particle attachment API is represented by per-tick emission. Standard first-person arms retain the Minecraft camera transform and original protection overlay. Colored aura/spark families and Turquoise center/radius are preserved.
- The source's unsupported technique definitions and commented-out breath-pose alternatives are not promoted into new content. No extra technique or invented progression menu is introduced.

## Original vanilla sound dependency

The original Jojo sound definition names `minecraft:entity.player.breath` as a file. The 1.16.5 vanilla event actually resolves to `random/breath`. The exact original OGG is bundled at the path the unchanged Jojo definition requests (`assets/minecraft/sounds/entity.player.breath.ogg`), without replacing an existing modern vanilla resource. Original asset SHA-1: `dd2f7209c2a58b0886beb34d49b0512b49adbb7e`; 14,331 bytes.

## Final Draft 15 verification

`/tmp/rotp-draft15-verification8.log`, **BUILD SUCCESSFUL in 53s**. All **197 required server GameTests** passed. The focused Hamon client flow passed in fresh integrated and dedicated worlds. All **91 core tests** pass (unchanged tasks reused); packaging passed **29 model checks**. No unrelated client matrix was repeated.

Bundle `0.2.2-fabric.15-dev+upstream.0.2.2.2`; manual playtest pending. Original source remains unchanged.

## Draft 16 source corrections

The source comparison in `HAMON_SOURCE_REVIEW.md` identified five client corrections, implemented by plan 05-17:

- HSR-05: `HamonSkillsTabGui.mouseClicked` / `mouseDragged` and `HamonSkillDescBox.onClick` / `onDrag` / `onRelease` now map to description-specific pointer capture and release. As in the original, only scrollable descriptions capture clicks; dragging the text moves its contents opposite the pointer, while dragging the scrollbar moves them with the pointer. Capture continues outside the box until release. The original 198×44 viewport, 192→186 text wrapping, thumb geometry, original background textures and 2.5-pixel wheel step are restored.
- HSR-06: `HamonSkillsTabGui.selectSkill` resets scrolling when a skill is selected, exposing its description and Learn button after selection below the viewport. The Fabric content has a fixed horizontal origin; its vertical tab offset now resets to zero.
- HSR-07: prerequisite icons resolve the owning Strength, Control or Technique tab before selecting the requested skill.
- HSR-08: occupied technique slots select their skill. `HamonTechniqueSlotElement.createSlots` / `drawTooltip` semantics distinguish locked slots, the next unlocked empty slot with the original free hint, and later unlocked empty slots without a tooltip. Only unlocked slots consume entries from the learned-skill iterator, matching the original configuration behavior.
- HSR-09: `KosmXSendoWaveKickHandler.getAnimPath` and `MCUtil.getHand` resolve physical left/right occupancy from the player's dominant arm. `HamonPlayerAnimation` now uses that mapping for the unchanged no-item, `_l`, `_r` and `_lr` clips. Fade durations, sampling and motion assets remain unchanged.

The existing `HamonClientGameTest` flow now includes actual description clicks/text drags/scrollbar drags, selection from below the viewport, prerequisite navigation to both stat tabs, a real Overdrive Barrage purchase followed by occupied-slot selection, and locked/free/later-empty tooltip checks at levels 20 and 40. A compact renderer-extraction case covers both dominant arms and all four main/offhand item combinations, restoring player items, action attachment and client hand options in `finally`. It adds a description-scroll screenshot to the existing integrated/dedicated captures. These assertions inspect the actual screen result and extracted animation clip without new production test hooks or a new suite.

The same flow also covers the root-owned Molotov prediction correction: direct client use without a nearby fire source or inventory fuel returns `FAIL` and retains the bottle stack. The temporary inventory fixture is restored in `finally`.

Worker verification is limited to `git diff --check` and reading cached Minecraft API signatures. The root agent owns the final build and focused integrated/dedicated execution; the Draft 15 results above do not verify these corrections. Exact motion and pixel fidelity remain pending manual comparison.

## Final Draft 16 verification

`/tmp/rotp-draft16-verification3.log`, **BUILD SUCCESSFUL in 1m 1s**. All **205 required server GameTests** and **94 core tests** passed. The focused `HamonClientGameTest` passed in fresh integrated and dedicated worlds; packaging passed **29 model checks**. No unrelated client matrix was repeated.

Root registered the progression cases and integrated all domain changes before this successful run. The earlier worker-only validation notes describe the handoff, not the final delivery status. Bundle `0.2.2-fabric.16-dev+upstream.0.2.2.2`; mod SHA-256 `6e7de829462ebcf9e43decb31437840a70408790ccd9e9853f5d4ddd936e301b`. See `HAMON_SOURCE_REVIEW.md` and `05-DRAFT16-VERIFICATION.md` for correction evidence and limitations. Manual visual/gameplay acceptance remains pending.
