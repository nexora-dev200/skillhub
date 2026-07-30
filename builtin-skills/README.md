# Built-in Skills

This directory contains the reviewed source used to build SkillHub's official starter Skill
packages. Each child of `skills/` is a complete package; generated ZIP files are release artifacts
and are not committed.

The first batch contains 15 general-purpose Skills covering study, office work, personal
productivity, content creation, weather, media, and frontend design. Every package includes:

- a `SKILL.md` adapted for SkillHub;
- `LICENSE.txt` and `NOTICE.md` with pinned upstream provenance;
- only the scripts and references required at runtime.

Build and verify the packages with:

```bash
make build-builtin-skills
make test-builtin-skills
```

The build writes deterministic, uncompressed ZIPs and `artifacts.json` to
`builtin-skills/dist/`. The artifact index records each ZIP's SHA-256 for the release step; runtime
manifest integration is maintained separately from the reviewed source collection.

Do not copy a new upstream Skill directly into this directory. First pin an upstream commit,
confirm redistribution terms, inspect every bundled file, add a realistic case to `evals.json`,
and complete the same package and security review recorded in
`docs/23-builtin-skills-first-round-test-report.md`.
