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

## 2026-07-11 - Antigravity Workspace Customizations Root
**Learning:** Antigravity's customizations model discovers workspace rules and skills from the `.agents/` directory (when configured as the Workspace Customizations Root). It expects skills to be placed in `skills/<skill_name>/` relative to that root. Without a `.agents/skills` directory or symlink, workspace skills are ignored by Antigravity.
**Action:** Create and maintain a symlink `.agents/skills -> ../.skills` to ensure workspace skills are automatically discovered and loaded by the Antigravity IDE.
