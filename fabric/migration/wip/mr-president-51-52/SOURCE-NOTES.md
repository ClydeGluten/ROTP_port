# Mr President51/52 source audit

Read original MrPresidentStandType, WorldData, Inside/BackTeleporter, EnteredRoomEffect, CocoJumboTurtleEntity, MrPresidentKeyItem, GemBlock, RoomFeature, registrations, dimension JSON, EntityUtilCap boundary, StandEffectsTracker and inherited StandEffectInstance serialization. Original source remains read-only. Root owns shared hooks/build; drafts remain here.

Room mapping is source UUID→16³ section, y0..15 then original expanding diamond-ring algorithm. Entry generates template only first allocation, lands at integer corner+(8,6,8), no sound. Return prefers currently loaded turtle across all worlds, else saved dimension/top-center. No fallback overworld location is invented. Persist tracked return positions only for allocated rooms. Allocations are not removed on room destruction.

Automatic entry target: non-spectator, width/height<4, inside turtle bbox moved+.5Y/inflated.25; falling, airborne, above turtle top; exclude fresh player<20ticks, Stand, carrier indirect passenger, GE creator. Unassigned turtle unlocked; assigned without inserted key locked. Key removal transition expels living excluding ArmorStand; actual loaded owner removal destroys breakable contents then returns all entities/items. Chunk unload skips destruction. Entered effect has no additional save codec: roomId,enteredUUIDs,previouslock are transient; preserve effective reset on restart rather than promise persistent effect membership.

Boundary: all noncreative/nonspectator entities remember initial section transiently and snap to section+(8.5,6,8.5) after crossing. This is distinct from entry integer coordinate.

Coco: vanilla Turtle base,1.2×.4, health60 armor2, no despawn. Stand grant only own animal type (not Arrow pool); virus every10ticks grant chance1−.15*amplifier. Master key bypasses missing Stand/identity. First ordinary key requires no NBT; assigned turtle requires exact UUID. Removal produces named bound key. Death/reversion drop is deliberately plain unbound key. Carry consumes offhand availability, source reposition/half-scale/60° rotation, swap releases. Natural companion spawn is additive to ordinary turtle, chance table depends nearest player's arrow/advancements, once per world dayTime globally.

Root shared hooks agreed in principle. GE worker TurtleBridge created remembers position and teleports before newborn world-add; reverseUnspawned stops effects; beforeRevert drops key.

Pending source geometry mesh handling, modern dimension conversion, carry/arrow active caller adaptation, exact new APIs and actual world tests.
