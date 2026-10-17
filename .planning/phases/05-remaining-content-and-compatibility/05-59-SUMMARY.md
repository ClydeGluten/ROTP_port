---
phase: 05-remaining-content-and-compatibility
plan: 59
status: integrated-core-followups-open
requires: [05-25, 05-75, 05-76]
provides: [synchronized-stand-config, resolve-extra-levels, resolve-display-boosts]
---

# Plan59 — Shared Stand settings and Resolve persistence

Original source reviewed: JojoModConfig.Common/SyncedValues, ResolveCounter.onResolveEffectStarted/tickBoostRemoteControl/onChatMessage/getBoostVisible, ResolveLevelsMap.incExtraLevel, StandEntity.hurtTarget and StandStatFormulas resistance consumers. Original sources stay read-only; no original assets changed.

StandConfig loads config/jojo/stand.json with original defaults and bounded field fallbacks. StandConfigPayload sends settings during configuration before attachment deserialization and at join; /rotp reload synchronizes current players before normalization. Threshold arrays use get-or-last and do not change the four-level Stand maximum. Older saved records survive threshold normalization. Creative and skipStandProgression unlock source training; standStamina controls spending and regeneration. Damage is scaled at Stand attack/projectile boundaries; resistance feeds actual guard calculations. Time Stop range0 covers its dimension; Endermen exemptions and frozen-damage scaling feed actual server/client rules. keepStandOnDeath controls respawn ownership.

ResolveExtras stores per-Stand extra maximum-level activations, remote-control and chat display factors. ResolveEffectLifecycleMixin captures actual effect activation. Chat/remote factors only affect the visible multiplier, preserving the source gain formula. Death/reset clears transient boosts. The actual effect, chat, control, save/load, altered thresholds, free-stamina attacks and Time Stop multiplier cases passed in the258-test server run recorded in /tmp/rotp-draft18-verification3.log. That run's client fixture failed separately before late-join testing; it is not represented as a successful delivery.

Still open: soulAscension's concrete consumer is plan60; shared linked-Stand damage/event ordering and broader classification are plan55; other upcoming Stand modules must consume the same config once. Full59 acceptance awaits those consumers and the next complete delivery. No commit or push; root owns shared source staging.
