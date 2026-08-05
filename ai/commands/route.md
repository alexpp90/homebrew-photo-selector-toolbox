---
description: Identify the target product and owning agent for a path or feature
---

For the following path or feature description, determine the route.

**Input:** $ARGUMENTS

Answer in this order, using `ai/ROUTING.md`:

1. **Product** — Desktop, Android Desktop, PhotoTok, or `products/android/core/` (which
   affects both Android products). If more than one, say so and split the work into one
   independent subtask per product; never let one product's implementation leak into another.
2. **Agent** — the owning agent from the routing tables, by its exact name.
3. **Requirements** — the `docs/products/<product>/REQUIREMENTS.md` sections that bind this
   work, plus `docs/shared/` if the change crosses products.
4. **Memory** — which `ai/memory/` file to read first (`bolt` performance, `palette` UI/a11y,
   `sentinel` security, `code_health` debt).
5. **Playbook** — whether an `ai/skills/playbook-*` skill already covers this task type.
6. **Consultants** — whether `@shared-photo-researcher-agent`, `@shared-ux-agent` or
   `@shared-publish-agent` should be consulted before implementation starts.

Do not begin implementing. This command answers *who and what to read*.
