---
phase: 05-remaining-content-and-compatibility
plan: 19
subsystem: hamon-content
requirements: [PARITY-03, PARITY-04]
---

# Glider landing and Molotov eligibility

Restored the original glider fall-damage override, preventing vanilla passenger damage propagation. Molotov client prediction uses the same read-only ignition search as server use; fuel mutation and projectile spawning remain server-owned. Focused cases cover mounted/unmounted falls, no ignition, carried fuel, nearby-fire priority and exactly-once consumption. The existing client flow also checks failed local Molotov use.

Implementation is complete. Final shared runtime results belong to 05-20-SUMMARY.md; this summary does not claim a separate test run. Original Forge source and user saves remain unchanged. No commit or push was performed.
