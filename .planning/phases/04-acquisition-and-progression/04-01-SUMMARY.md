---
phase: 04-acquisition-and-progression
plan: 01
subsystem: gameplay
requires: [03-star-platinum-abilities]
provides: [stand-arrow-items, desert-pyramid-loot, persistent-arrow-acquisition, draft-5]
affects: [04-acquisition-and-progression, 05-remaining-content-and-compatibility]
requirements-completed: [BUILD-01, TEST-01]
key-files:
  created:
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/StandItems.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/StandArrowService.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/ArrowProgress.java
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/StandArrowLoot.java
    - fabric/src/gametest/java/com/github/standobyte/jojo/fabric/AcquisitionGameTests.java
    - fabric/src/gametest/java/com/github/standobyte/jojo/fabric/AcquisitionClientGameTest.java
  modified:
    - fabric/src/main/java/com/github/standobyte/jojo/fabric/StandAttachments.java
    - fabric/src/client/java/com/github/standobyte/jojo/fabric/client/RipplesClient.java
    - fabric/tools/package_draft.py
---

# Draft 5: Survival Stand Arrow acquisition

The user approved Stand Arrow acquisition as the first Phase 4 slice. Normal and Beetle arrows now use original handheld models/textures and 25/250 durability. An independent 2% Beetle Arrow roll augments desert-pyramid chest loot, providing a Survival path to Star Platinum without operator commands. Both items also appear in Creative's Combat tab.

Self-piercing starts the original default virus: 30 XP levels plus 5 per previous arrow awakening, one level every ten ticks, 0.75 damage while paying or 7.5 without XP. Paying protects very low health using the original rule. Paying the requirement or surviving the full `(required + 1)` seconds grants Star Platinum, then restores 0.25 health per tick until full. Creative grants immediately and skips virus/XP/durability costs. Milk preserves the virus while curing other effects.

A separate persistent owner-synchronized attachment stores infection, payments, acquisition history and recovery without changing the existing Stand ownership save schema. Save/reload and dedicated reconnect resume infection. Death cancels the attempt and keeps history; actual respawn is tested. Repeated or already-owned use rejects without extra damage/durability. Operator Stand replacement and forced effect removal cancel pending acquisition; forced removal deliberately does not award a Stand. Dynamic HUD text and item tooltips explain XP requirements, danger, current pool and infection progress.

## Delegation and review

Antigravity CLI (`claude-sonnet-5-5-medium`) produced five scoped asset/loot files in `../ROTP_port-worktrees/arrow-assets-loot`. Worker output is retained at `/tmp/rotp-arrow-assets-result.json`. All files were inspected and integrated; parent code handled registration, acquisition, persistence, milk behavior, UI and tests. Main HEAD was not changed by the isolated snapshot.

Runtime testing found the worker's `source.isBuiltin()` check excluded the desert-pyramid table in this environment, which reports it as a datapack source. The integrated callback now matches the original global modifier's table-ID behavior, including replacement tables, while preserving existing contents. A deterministic 5,000-seed loot test verifies the rare bonus and an unrelated table.

## Validation

Passed 12 core tests, 56 server GameTests (55 mod tests plus one framework test), 24 model checks and both real-client suites. Eleven new acquisition server tests cover paid awakening and healing, natural expiration, unpaid death, respawn history, Creative bypass, repeat/owned rejection, escalating costs, milk, last durability, operator cancellation and loot.

The new real-client suite uses actual item-use packets. It verifies both arrow models, owner HUD/effect icon, mid-virus save/reload, ownership/history persistence and dedicated reconnect before awakening. The dedicated check waits for separate ownership, arrow-history and vanilla XP packets before asserting synchronized state. Arrow/cost, infection and dedicated awakening screenshots were inspected.

Final release log: `/tmp/rotp-draft5-final-verification2.log`; a copy, model results and hashes accompany the versioned bundle. Main changes are staged only. Draft 4 remains available, and latest selects Draft 5.

## Remaining scope

PARITY-02 remains open for Resolve, training/unlocks and the original ability-selection UI. Only Star Platinum is migrated. Normal-arrow recipes/materials, meteorite world generation, projectile/dispenser behavior, enchantments, mob acquisition and configurable pools/costs remain pending. This is a self-piercing acquisition slice, not full original arrow or mod parity. Draft 5 manual playtest remains pending.

## Self-check

Passed: `fabric/src/main/java/com/github/standobyte/jojo/fabric/StandArrowService.java` and `fabric/src/gametest/java/com/github/standobyte/jojo/fabric/AcquisitionGameTests.java` exist. Integrated build, server/client tests, model checks and packaged checksums pass. Main changes are staged under the user's stage-only workflow.
