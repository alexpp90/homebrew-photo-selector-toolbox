# Code Health Backlog & Lessons

Refactoring candidates and structural lessons. Owned by `@code_health_agent`; any agent may append. Format for backlog items:

```markdown
## [OPEN|DONE] YYYY-MM-DD - Short title
**Where:** file paths / modules
**Debt:** what is wrong and why it matters
**Proposal:** the safe refactoring, referencing `.skills/refactoring_guide/SKILL.md` patterns
```

Lessons use the standard `.Jules/` format (Learning/Action).

---

## 2026-07-12 - GitHub Actions Workflow Rename Run Number Reset
**Learning:** Renaming or changing a GitHub Actions workflow filename resets the `github.run_number` count back to 1 for that new file. If `github.run_number` is used in version code calculations, the reset causes version code regressions. This will result in Google Play Store rollout errors (such as "does not allow any existing users to upgrade").
**Action:** When introducing or renaming a CI workflow that determines version codes, always add a baseline offset to ensure version codes remain strictly higher than any previously published builds.

## 2026-07-11 - Gradle Kotlin DSL 'java' Collision & GitHub Workflow Resilience
**Learning:** 
1. In Gradle Kotlin DSL, `java` is a reserved keyword referring to the Gradle Java plugin/extension. Referencing fully qualified names starting with `java.` (e.g. `java.util.Properties`) inside build files yields compiler errors like `Unresolved reference 'util'`.
2. When referencing repository configuration fields in GitHub Actions workflows, developers might store configuration values under either Secrets or Variables. Referencing them strictly via one scope can result in blank values if stored in the other.
**Action:** 
1. Place standard Java package imports at the very top of `.gradle.kts` files (e.g., `import java.util.Properties`) and instantiate them using their simple name (`Properties()`).
2. Implement workflow configuration fallbacks using the expression `${{ secrets.KEY || vars.KEY }}` to ensure the build remains resilient regardless of where the keys are stored.

## 2026-07-11 - Antigravity Workspace Customizations Root
**Learning:** Antigravity's customizations model discovers workspace rules and skills from the `.agents/` directory (when configured as the Workspace Customizations Root). It expects skills to be placed in `skills/<skill_name>/` relative to that root. Without a `.agents/skills` directory or symlink, workspace skills are ignored by Antigravity.
**Action:** Create and maintain a symlink `.agents/skills -> ../.skills` to ensure workspace skills are automatically discovered and loaded by the Antigravity IDE.
