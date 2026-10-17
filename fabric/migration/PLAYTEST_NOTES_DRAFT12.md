# Draft 12 playtest corrections

User notes in `/home/alanno/notes` reported horizontal Stand attacks and no apparent way to use The World's knives.

## Vertical attacks

The server's ordinary melee ray already used the player's full look vector. The manifestation followed a different calculation: every attacking pose used a fixed, horizontal 0.8-block offset. This made attacks appear detached from vertical aim. The port now restores `StandEntityAction.front3dOffset` / `offsetToTarget` and `StandRelativeOffset.withXRot`: pitches the forward offset with the player, backs away from a target using its width, clamps light/heavy displacement to 0.5–2 blocks and barrage to 0.5–1.5 blocks, and respects nearer blocking geometry. Idle, manual control and independently positioned Time Stop Punch retain their separate position paths.

Original barrage JSON already binds arm pitch. Ordinary punch JSON keys arm pivots to zero; the original legacy `HumanoidStandModel` added `armsRotation`, `armsRotationFull` and `armsRotationBack` after its keyed poses. The migrated models now adapt that aiming layer to their JSON limb pivots. Light aim builds through windup and leaves during follow-through; heavy aim persists until recovery. The World's kick applies pitch to its leg pivots. This is an explicit adaptation for the reported visual bug, rather than a claim that the upstream JSON contained these rotations. Geometry, texture and numeric animation keyframes remain generated from the read-only original assets.

Regression checks aim above and below a player with both Stands and light/heavy/barrage attacks, verify real damage and exclude a horizontal decoy. Real-client tests change mouse pitch during an ongoing barrage in first person and F5, check server hits, synchronized manifestation pitch/elevation and the actual baked model, and exercise both integrated and dedicated connections. The same baked models verify normal punches, finishers, Time Stop Punch and kick aim without leaking rotations into idle.

## Finding and throwing knives

Knives were already registered, in the Creative Combat/search inventories and craftable from an iron ingot above a stick (four per recipe). Original `InputHandler` gives the selected Stand ability priority over ordinary item use. Thus the default guard consumes right-click while the Stand hotbars are active. Original `disableHotbars` (default Left Alt) temporarily yields the click to the held item. Those controls and priority rules are preserved.

Actual item tooltips now explain obtaining knives, throwing up to eight, sneaking to throw one, the hotbar bypass, and The World's extra volley from the remaining stack. The World's progression overview also exposes acquisition and throwing instructions before a player owns a knife. A contextual held-knife hint shows the currently bound bypass/use/sneak keys, wrapping at smaller GUI widths. It is present only while a power hotbar and a held knife make it relevant. Remapping controls updates the instructions. These discoverability additions address the user's report without replacing the original controls or inventing a Stand action that dispenses free knives.

The existing real-input tests still verify guard priority, temporary Alt bypass, the original stack consumption, The World's extra volley and Shift single throws on integrated and dedicated connections. Added checks verify the actual Creative inventory entries and generated inventory tooltip, including rebound keys.

## Knife and Stone Mask

With the newly ported vampirism backend, `KnifeItem.use` also restores its original early single-knife/Stone Mask branch: successful activation applies one point of self-damage and consumes neither the knife nor a throw cooldown. Failed activation continues the ordinary throw path. Vampire tests cover this integration; ordinary knives do not gain an invented bleeding effect.
