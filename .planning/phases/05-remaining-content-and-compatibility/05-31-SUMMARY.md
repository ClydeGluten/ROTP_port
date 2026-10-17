---
phase: 05-remaining-content-and-compatibility
plan: 31
subsystem: pillarman-wind
status: implemented-awaiting-integration-verification
---

# 05-31 — Pillar WIND implementation handoff

Created PillarWindActions, PillarSandstormEntity, PillarWindClient and four focused actual-world PillarWindGameTests. Small Sandstorm, Divine Sandstorm, Wind Cloak and Atmospheric Rift retain original mode gates, costs, timing, projectile parameters, terrain rules and held lifecycle. Server-synced action state drives particles and cloak rendering. No original Forge file was changed.

Root owns common/client bootstrap, player ticking and cleanup, shared input dispatch, knockback and cure filtering, renderer extraction/hand hooks, and test registration. Exact signatures and source quirks are recorded in `fabric/migration/PILLAR_WIND_PARITY.md`.

Public action API: `initialize()`, `state(LivingEntity)`, `isHolding(ServerPlayer)`, `receive(ServerPlayer, Action)`, `smallSandstorm(ServerPlayer)`, `startHeld(ServerPlayer, Held)`, `keepHeld(ServerPlayer)`, `stopHeld(ServerPlayer)`, `tick(ServerPlayer)`, `clear(ServerPlayer)`, `clearAllRuntime()`, `uncurable(Holder<MobEffect>)`. The service registers `jojo:sun_resistance`; shared effect registration must not duplicate it.

Public client integration: `initialize()`, `send(Action)`, `movementMultiplier(LivingEntity)`, `animation(LivingEntity)`, `firstPersonAuraCount(LivingEntity)`, `extract(Avatar, AvatarRenderState, float)`, `CloakLayer(RenderLayerParent<AvatarRenderState, PlayerModel>)`, and `submitHand(PoseStack, SubmitNodeCollector, ModelPart, ModelPart, int)`.

Remaining presentation is explicitly owned by 05-34: source charge animation playback and first-person green pseudo-aura draw using the exposed animation/count helpers. The resource handoff requires the three original particle JSONs named in the parity ledger. The shared damage hook must apply source factor .1 to PillarSandstormEntity before its explicit directional knockback.

Validation: static source/API inspection and staged whitespace check only. No build or GameTest execution by this worker; root's combined lane supplies compilation/runtime results. Java files are frozen for that lane. Stage only; no commit or push.
