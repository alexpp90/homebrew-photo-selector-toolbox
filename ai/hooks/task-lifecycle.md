PHOTO SELECTOR TOOLBOX — MANDATORY LIFECYCLE

Three independent products. Identify which one a task targets before opening any file:
Desktop (products/desktop/), Android Desktop (products/android/android-desktop/), PhotoTok
(products/android/phototok/). products/android/core/ affects both Android products. Copying
code between products is a defect, not reuse.

BEFORE work — invoke the `task-lifecycle` skill. It covers the pre-work reads: the target
product's docs/products/<product>/REQUIREMENTS.md, the matching ai/memory/ lesson file, the
owning agent config per ai/ROUTING.md, and any matching ai/skills/playbook-* skill.

BEFORE finishing — invoke the `retrospective` skill. Tests via ./scripts/run_tests.sh (not a
subset), requirements synced, mentor consulted before any ai/memory/ write, debt filed,
framework drift fixed, no scratch files. A Stop hook checks this.

Other skills, invoked when their moment arrives rather than read up front: `verify-build`,
`sync-requirements`, `record-lesson`, `create-playbook`, `sync-framework`,
`release-compliance`, `refactoring-guide`.

Commands: /route (who owns this?), /verify (run the CI mirror), /retro (close out),
/sync-framework (regenerate + validate after editing ai/agents or ai/skills).

Everything under ai/ is canonical. .claude/, .gemini/ and .agents/ are symlinks to it —
writes through them are blocked. Set PST_SKIP_HOOKS=1 to bypass the guards in an emergency.
