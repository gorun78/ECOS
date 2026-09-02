### Test Cases

<!-- CUSTOMIZE: Phase 3 is optional — skip by default unless user explicitly requests testing. | modified-by: qingting | modified-at: 2026-07-30 -->
> **Default behavior**: Skip this section. Only proceed to test cases when the user explicitly says "run the tests", "test this skill", "let's evaluate", or similar.

After writing the skill draft, you may optionally create 2-3 realistic test prompts — the kind of thing a real user would actually say. Share them with the user: [you don't have to use this exact language] "Here are a few test cases I'd like to try. Do these look right, or do you want to add more?" Then run them.
<!-- /CUSTOMIZE -->

Save test cases to `evals/evals.json`. Don't write assertions yet — just the prompts. You'll draft assertions in the next step while the runs are in progress.

```json
{
  "skill_name": "example-skill",
  "evals": [
    {
      "id": 1,
      "prompt": "User's task prompt",
      "expected_output": "Description of expected result",
      "files": []
    }
  ]
}
```

See `references/schemas.md` for the full schema (including the `assertions` field, which you'll add later).
