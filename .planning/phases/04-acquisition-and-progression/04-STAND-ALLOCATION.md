# Stand allocation generations

Status: Draft 8 implements the normal-play distribution rule for Star Platinum and The World. World-level state is shared across dimensions and saved; per-player provisional choices and a permanent migration marker survive death/relogin. Direct operator grants bypass natural allocation. Existing pre-update owners seed once when they first join. New catalog entries join an unfinished generation; permanent ownership uses stable IDs. Crash recovery across partially saved world/player files, offline legacy-owner scanning and fully configurable pools remain future work.

The confirmed distribution rules below are implemented. The broader recovery and compatibility items under the original implementation plan remain acceptance targets; they are not all claimed complete.

## Confirmed user rules

- Every eligible player receives a tentative random integer from `1..n`, where `n` is the number of playable Stands.
- That integer maps to a playable Stand. It is a candidate, not a reservation, and may change before awakening.
- A successful awakening reserves and permanently binds the Stand. This is the user's clarified boundary; joining, starting an awakening and first summon do not reserve it.
- Within each server-wide generation, each playable Stand may be awarded once. If a candidate was already claimed, choose a random unused Stand from that generation.
- Once all playable Stands have been awarded, the next successful awakening starts a new generation with the full pool. Duplicates across generations are expected.

Example: four playable Stands and five players. Regardless of join order or tentative collisions, the first four successful awakenings award four different Stands. The fifth successful awakening starts generation two and can receive any of the four. Pending players never consume slots.

## Implementation plan

Use a server-owned allocator shared across dimensions and persisted with the world. Store a generation number, its ordered registry of stable Stand IDs, and its remaining unclaimed IDs. The integer mapping must remain stable for that generation; save stable IDs alongside candidates so registration order changes cannot silently change permanent ownership.

The successful-awakening transaction must validate eligibility, resolve or reroll the candidate against the current pool, grant ownership, remove the awarded ID, record the generation and persist both sides. Run it serially on the server thread. Two awakenings completing in the same tick must see each other's reservations. A failed grant, cancelled infection, death during acquisition or retry must not consume another slot. Add a persisted award identity or equivalent recovery mechanism to prevent a restart between world/player saves from duplicating an award.

Once awarded, death, dismissal, first summon, reconnect and ordinary world reload must not reroll the Stand or return its slot. Administrative removal must not retroactively replenish a generation; otherwise clearing and reawakening could defeat the distribution rule. Preserve acquisition history and define the reawakening policy before exposing it to players.

Only implemented, enabled and playable Stands belong in the pool. With `n = 1`, every successful awakening exhausts one generation and still gives Star Platinum. With `n = 0`, fail cleanly without consuming acquisition costs or recording an award. Proposed update policy: add newly playable Stands at the next generation, retire unavailable entries safely, and retain historical ownership by stable ID. Migration of pre-allocator worlds and operator-forced grants need explicit policies in the implementation plan; do not silently reinterpret existing players' Stands.

## Acceptance checks for the future milestone

1. Four Stand types and five successful awakenings produce four unique awards followed by one award in the next generation.
2. Many simultaneous joins and identical tentative integers do not consume or duplicate reservations.
3. Two completions in the same tick award different remaining Stands; crossing the final slot starts exactly one new generation.
4. Cancelled/failed awakenings consume no slots; replaying a completion does not consume twice.
5. Reconnect, death, dimension changes and restart preserve ownership and allocator state, including recovery after a partial save.
6. Pool sizes zero and one, content updates, historical duplicates and operator actions follow documented policies.

This is a custom distribution rule requested by the user, separate from original Forge parity. Implement it as a Phase 4 extension, with meaningful multiplayer uniqueness testing as more playable Stands arrive in Phase 5.
