# Draft 9: original Stand HUD and independent third-person camera

Draft 8's custom layout was not the original 1.16 overlay. Draft 9 ports the supported original Stand HUD and control behavior to the current Fabric renderer. It also removes the shoulder camera's target-dependent convergence, which could change camera direction as the aim ray found a different target.

This covers Star Platinum and The World. It does not claim a complete port of every original UI or power. Combined Draft 9 verification passed; user playtest is pending.

## HUD source mappings

Original sources stay read-only under `src/main/java/com/github/standobyte/jojo/client/`. Fabric adapters are under `fabric/src/client/java/com/github/standobyte/jojo/fabric/client/`.

| Original class/method | Fabric implementation | Preserved behavior |
| --- | --- | --- |
| `ClientModSettings.Settings`, `ActionsOverlayGui.updateElementPositions`, `PositionConfig.TOP_LEFT`, `getHotbarsYDiff` | `ActionHud` | Default top-left placement, original row offsets/spacing |
| `ActionsOverlayGui.renderActionsHotbar` | `ActionHud` | Mouse-button sprites, dim deselected/disabled rows, folding clips and focus pulse |
| `HotbarFold` | `OriginalHotbarFold` | Original slot ordering, positions, visible edges and render order |
| `HotbarRenderer` | `OriginalHotbarRenderer` | Original atlas table, ornamental margins and selection sprite |
| `FadeOut`, `ElementTransparency` | `OriginalHudFade` | 40-tick lifetime, final 10-tick fade, minimum text opacity |
| `ActionsOverlayGui.renderActionIcon` | `ActionHud`, `ActionCatalog`, `StandHudState` | 16px icons, availability dimming, cooldown wipe from authoritative remaining/total ticks, learning bar and finisher selection |
| `drawHotbarText`, `actionName`, `drawPowerName`, `renderPowerIcon` | `ActionHud`, `ActionCatalog` | Original translations, hold/Shift names, power textures, colors and fades |
| `BarsRenderer`, `HorizontalBarsRenderer` | `OriginalHudBars` | Textured full/short bars, source icons, Resolve level/record fill, interpolation and visible combat multiplier |
| `StandPower.tick`, `BarEffects.triggerRedHighlight/resetRedHighlight` | `OriginalHudBars`, `ActionHud.staminaRejected` | Low-stamina pulse; input rejection reuses the source warning effect |
| `renderIconBarAtCrosshair`, `renderFilledIcon`, `renderCrosshair` | `ActionHud` | Original finisher icon, hold charge fill and eight-tick completed-hold flash |
| `BlitFloat`, `ClientUtil.drawBackdrop/getHighlightAlpha` | `OriginalHudDraw` | Fractional geometry/UVs, text background and triangular pulse through the new extractor |
| `InputHandler`, `ActionsOverlayGui` mode/selection behavior | `StandSelection`, input mixins | Stand mode, held row focus, mouse actions, empty selection, Shift variants and temporary bypass |
| `HudLayoutEditingScreen` | `ActionLayoutScreen`, `ActionLayout`, `ActionLayouts` | Original editor texture/slot geometry, pick/place, cross-row movement, visibility toggle and persistent layout |

With the default 9px font, bars anchor at `(4,4)`. In Stand mode the power icon is at `(4,36)`, attack icons start at `(19,68)`, and ability icons start at `(19,103)`. Selected names are above their slots at `(16,55)` and `(16,90)`, following the source methods. Default mouse hotbars have mouse sprites, not invented numeric badges.

Assets are unchanged: `overlay.png` (256×256), `overlay_hotbar.png` (512×512), `radial_indicator.png` (256×256), `layout_editing.png`, original action icons and power icons. Star Platinum uses `0x8E45FF`; The World uses `0xFFD800`. The stamina texture itself has the original red/yellow/green gradient. It pulses red below half stamina unless Creative or Resolve burst bypasses the debuff. Creative hides the finite stamina bar.

Full bars are 200px long; inactive short bars are 150px and fade. Stamina interior is `x18..217, y5..10`, with filled width `floor(200 * stamina/maxStamina)`. These coordinates support pixel regression tests; the low-stamina red overlay must not be mistaken for remaining fill.

## Controls and persistence

M summons, O holds remote control, K toggles Stand mode, and H opens progression. Hold V for attack selection or B for ability selection, then use wheel or number keys. LMB/RMB use selected actions. Shift converts punch/barrage to heavy or the available finisher. Alt yields mouse actions to vanilla while V/B selection remains available.

Backslash opens the editor. Left-click picks/places, including across rows; right-click enables/disables. Number keys reposition a picked, hovered or keyboard-focused move. Done and Escape both save. Stand layouts and selections persist independently in `config/jojo/action-layouts.json`; invalid data uses validated defaults and a preserved invalid-file backup.

Mouse Time Stop requires holding through charge. Releasing early cancels. Completion activates the stop, and the same held press cannot immediately resume it; release and press again to Resume Time. R/Z/X/C/N/Y/J/U/I direct bindings remain available. Heavy and uppercut/kick direct bindings start unbound. The direct J shortcut retains toggle semantics.

`OriginalControlsMigration` updates recognized Draft 8 defaults once while leaving custom bindings untouched. Before saving, it copies options to `config/jojo/options-before-original-controls.txt`; `config/jojo/original-controls-v1` records completion. Draft 9.1 separately repairs the two invalid `key.keyboard.-1` bindings written by Draft 9, even with that marker present. It uses `InputConstants.UNKNOWN`, preserves custom bindings and saves an additional `options-before-unbound-key-repair.txt` backup.

## Camera implementation and provenance

[Better Third Person’s author page](https://www.curseforge.com/minecraft/mc-mods/better-third-person) advertises independent 360-degree orbit and movement in eight directions while retaining interactions. Its code is all rights reserved, so this port implements that behavior independently; no Better Third Person source was copied.

`OrbitRotation` stores orbit angles and converts camera-relative movement to player-facing movement. `StandCamera` handles mouse orbit, movement/action facing, front/rear perspective and world/control lifecycle. Before an action packet, the player rotation is synchronized to the visible aim. Target distance never feeds camera rotation. Vanilla continues to choose third-person distance and clip against walls.

Local Stand/finger opacity remains 16% in first person and when the Stand crosses a retracted third-person view. First-person manual control hides its local body. Returning to first person restores the visible direction; death/world changes/manual control reset orbit ownership. Other players' Stands retain normal opacity. External camera-mod interoperability remains unverified.

## Remaining gaps and adapters

- The original active radial indicator uses custom textured quads. This port uses the original baked radial atlas path retained in upstream `renderRadialIndicator`: the same artwork in one-degree fill steps. It does not claim to copy the dynamic-quad implementation.
- New GUI extraction accepts integer rectangles. `OriginalHudDraw` preserves fractional folding geometry/UVs with a 1/1024-pixel transform adapter.
- There is no gameplay action queue, so the original queued-action brightness state is absent. Availability reflects current unlocks, cooldowns, manifestation, stamina and recovery rather than every upstream action-condition class.
- This restores default Stand presentation, not Hamon/non-Stand UI, custom action-keybind slots, leap/bow widgets, skin selection, warning panels or alternate anchors/orientations.
- Original bar-cost previews are commented out in the source and remain absent. Existing Arrow infection feedback and regional Time Stop tint/countdown are retained.
- Camera settings and compatibility with external camera/movement mods need further work. This is not a bundled or complete port of Better Third Person.

Final combined tests, screenshots and packaged-version evidence belong in the draft manifest/logs and [migration status](STATUS.md). User playtest is the next acceptance step.
