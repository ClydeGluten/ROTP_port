# Project guidance

Preserve original mod behavior by default across gameplay, progression, controls, HUD and assets. This is the user's project-wide instruction from 2026-10-14. Use the original Forge source in `src/` as the reference and keep it read-only while implementing the Fabric port in `fabric/`.

Explicit user-requested changes, including Stand allocation generations and independent third-person orbit, remain intentional exceptions. Document unavoidable platform adaptations and known parity gaps in `fabric/migration/`; do not silently substitute newly designed behavior.

Continue the existing GSD plan in `.planning/STATE.md`. Keep chat concise using the requested caveman skill. Delegate only when it saves time; Antigravity previously exhausted account quota, so use available coding agents for isolated work when useful.
