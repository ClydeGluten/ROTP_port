---
phase: 05-remaining-content-and-compatibility
plan: 15
subsystem: hamon-events
requirements: [PARITY-03, PARITY-04]
---

# Source review and damage-event parity

Restored original Shock removal after absorption, including player and lethal paths. Restored per-block cactus/berry protection, raw resource spending, master immunity and charge fallback. Added the original short-spark packet branch, tracking/self delivery, particle offsets and sound throttle. Source findings and rejected external suggestions are in HAMON_SOURCE_REVIEW.md. New tests exercise health versus absorbed/rejected damage and multiple block contacts.

Implementation is complete. Final shared runtime results belong to 05-20-SUMMARY.md; this summary does not claim a separate test run. Original Forge source and user saves remain unchanged. No commit or push was performed.
