---
name: sync-requirements
description: "Decide whether a change alters observable behaviour and, if so, update the owning product's docs/products/<product>/REQUIREMENTS.md (or docs/shared/ for cross-product policy) in the same commit. Use after any behaviour change and during the retrospective."
allowed-tools: Read, Grep, Glob, Edit
---

# Keep the requirements true

Requirements that lag the code are worse than no requirements: agents read them as fact and
build on a false premise. The rule is therefore **same commit, not same branch**.

## Which file owns it

| Change affects | File |
|---|---|
| Desktop only | `docs/products/desktop/REQUIREMENTS.md` |
| Android Desktop only | `docs/products/android-desktop/REQUIREMENTS.md` |
| PhotoTok only | `docs/products/phototok/REQUIREMENTS.md` |
| `products/android/core/` | **both** Android product files |
| A rule that must hold for more than one product | `docs/shared/` |
| Visual/UX styling for an Android product | that product's `DESIGN.md` |

There is no repository-root `REQUIREMENTS.md`. If an instruction tells you to read one,
that instruction is stale — fix it via the `sync-framework` skill.

## Does this change require an update?

Yes if a user, or another agent reading the docs, could observe the difference:

- new or removed feature, mode, or command
- changed layout, keyboard binding, gesture, or navigation
- changed threading model, cache size, or concurrency limit
- changed algorithm semantics — score meaning, duplicate criteria, sort order
- new, removed or reclassified dependency
- changed build target, artifact name, SDK version, or CI gate
- changed data access: a new permission, endpoint, SDK, or OAuth scope
  (also triggers the `release-compliance` skill)
- a deprecation

No if the change is behaviour-preserving: a refactoring, a test-only change, a comment, or a
rename with no external effect. Say so explicitly in your task summary rather than leaving it
ambiguous.

## How to write it

1. Find the section that already governs the area. Extend it — do not append a parallel rule
   somewhere else, which is how two contradictory requirements end up in one file.
2. Write the rule as a constraint, not a changelog entry. "The preview cache is
   `(1200, 900)`" — not "changed the preview cache".
3. Keep the numbering and section conventions already in the file.
4. If the change deprecates an existing requirement, mark it deprecated rather than deleting
   it silently, so a reader can tell the difference between "removed" and "never existed".
5. Cross-product policy changes go to `docs/shared/`, and the per-product files point at it
   rather than restating it.

## Feature parity

When a feature lands in one product, evaluate it for the others against
`docs/shared/FEATURE_PARITY.md` and record the decision — including a decision *not* to port
it, with the reason. The three products are deliberately different; an unrecorded gap is
indistinguishable from an oversight.

## Related

- `retrospective` step 2 invokes this skill
- `release-compliance` for changes touching permissions, endpoints or data flow
- `sync-framework` when the stale text is in an agent or skill definition rather than in
  product docs
