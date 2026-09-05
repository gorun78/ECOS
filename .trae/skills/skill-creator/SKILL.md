---
name: skill-creator
# CUSTOMIZE: version bumped from 2.4.0 → 2.5.0 | modified-by: qingting | modified-at: 2026-08-11
version: 2.5.0
description: Create new skills, modify and improve existing skills, and measure skill performance. Use when users want to create a skill from scratch, edit, or optimize an existing skill, run evals to test a skill, benchmark skill performance with variance analysis, or optimize a skill's description for better triggering accuracy.
---

# Skill Creator

A skill for creating new skills and iteratively improving them.

At a high level, the process of creating a skill goes like this:

- Decide what you want the skill to do and roughly how it should do it
- Write a draft of the skill
<!-- CUSTOMIZE: Phase 3+4 are optional — skip by default unless the user explicitly requests testing. | modified-by: qingting | modified-at: 2026-07-30 -->
- (Optional, skip by default) Create test prompts and run with-skill and baseline evaluations
- (Optional, skip by default) Grade results, aggregate benchmarks, and review
<!-- /CUSTOMIZE -->
- Rewrite the skill based on feedback from the user
- Repeat until you're satisfied
<!-- CUSTOMIZE: Phase 6 packaging is commented out — not needed in this environment. | modified-by: qingting | modified-at: 2026-07-30 -->
<!-- - Package the final skill into a .skill file -->
<!-- /CUSTOMIZE -->

Your job when using this skill is to figure out where the user is in this process and then jump in and help them progress through these stages. So for instance, maybe they're like "I want to make a skill for X". You can help narrow down what they mean, write a draft, write the test cases, figure out how they want to evaluate, run all the prompts, and repeat.

On the other hand, maybe they already have a draft of the skill. In this case you can go straight to the eval/iterate part of the loop.

Of course, you should always be flexible and if the user is like "I don't need to run a bunch of evaluations, just vibe with me", you can do that instead.

Then after the skill is done (but again, the order is flexible), you can also run the skill description improver, which we have a whole separate script for, to optimize the triggering of the skill.

Cool? Cool.

## Communicating with the user

The skill creator is liable to be used by people across a wide range of familiarity with coding jargon. If you haven't heard (and how could you, it's only very recently that it started), there's a trend now where the power of Claude is inspiring plumbers to open up their terminals, parents and grandparents to google "how to install npm". On the other hand, the bulk of users are probably fairly computer-literate.

So please pay attention to context cues to understand how to phrase your communication! In the default case, just to give you some idea:

- "evaluation" and "benchmark" are borderline, but OK
- for "JSON" and "assertion" you want to see serious cues from the user that they know what those things are before using them without explaining them

It's OK to briefly explain terms if you're in doubt, and feel free to clarify terms with a short definition if you're unsure if the user will get it.

---

## Creating a skill

<!-- CUSTOMIZE: add triggers section with recommended prompt phrases | modified-by: qingting | modified-at: 2026-07-30 -->
### Triggers — Recommended Prompt Phrases

The skill-creator should activate when the user says any of the following:

| 触发短语 | 示例 |
|---------|------|
| `/skill-creator` | `/skill-creator` |
| 创建技能 | 创建一个代码审查技能 |
| 修改技能 | 修改一下代码审查技能，增加安全检查维度 |
| 编辑技能 | 编辑 code-review skill，把触发词改成... |
| 把流程变成技能 | 把刚才的审查流程变成一个技能 |
<!-- /CUSTOMIZE -->

### Capture Intent

<!-- CUSTOMIZE: rewrite Capture Intent as progressive conversational flow | modified-by: qingting | modified-at: 2026-07-30 -->
Start by understanding the user's intent. Use **progressive disclosure** — ask only what's missing, never dump all questions at once.

**Flow:**

1. User says something like "幽默趣事" or "创建一个代码审查技能"
2. Assess how much info is already present:
   - **Info complete** (name + triggers + purpose all in one sentence) → confirm with a one-line summary and proceed directly to creating the SKILL.md. No further questions.
   - **Name only** (e.g., "幽默趣事") → ask for triggers and purpose, with an example format:
     > 「幽默趣事，触发词是讲个笑话、来点乐子，用来在对话中穿插轻松幽默的小故事」
   - **Fully blank** (e.g., "创建技能") → give 2-3 example skill types to inspire the user, then ask what they want.

3. User confirms → extract name, triggers, purpose, and infer a category.

**Key principles:**
- Never list all four questions upfront as a bullet-point questionnaire.
- Mimic natural conversation — one exchange at a time.
- A user who provides everything in one sentence (like REQ-022's one-liner pattern) should trigger zero follow-up questions.
- If the current conversation already contains a workflow the user wants to capture (e.g., they say "turn this into a skill"), extract answers from the conversation history first.
<!-- /CUSTOMIZE -->

### Interview and Research

Proactively ask questions about edge cases, input/output formats, example files, success criteria, and dependencies. Wait to write test prompts until you've got this part ironed out.

Check available MCPs - if useful for research (searching docs, finding similar skills, looking up best practices), research in parallel via subagents if available, otherwise inline. Come prepared with context to reduce burden on the user.

### Write the SKILL.md

Based on the user interview, fill in these components:

- **name**: Skill identifier. Naming rules:
  <!-- PATCH-2: name naming rules | modified-by: qingting | modified-at: 2026-07-30 -->
  - **MUST match the skill directory name exactly** (case-sensitive)
  - Use **lowercase letters, digits, and hyphens (`-`) only**
  - **No spaces, no underscores, no uppercase letters**
  - Example (valid): `pdf-editor`, `code-reviewer`, `brand-guidelines`
  - Example (invalid): `PDF_Editor`, `codeReviewer`, `brand guidelines`
  - If the `name` and the directory name disagree, the skill will fail to load — verify with `Glob` before finalizing.
  <!-- /PATCH-2 -->
- **version**: Skill version identifier. Rules:
  <!-- CUSTOMIZE: add version metadata field | modified-by: qingting | modified-at: 2026-07-30 -->
  - Use **Semantic Versioning** format: `MAJOR.MINOR.PATCH` (e.g., `1.0.0`, `2.1.3`)
  - New skills start at `1.0.0`
  - Increment PATCH for bug fixes / minor wording changes
  - Increment MINOR for new features / sections added
  - Increment MAJOR for breaking changes to the skill's behavior or trigger words
  - Bump the version on every edit — do not leave it stale
  <!-- /CUSTOMIZE -->
- **description**: When to trigger, what it does. This is the primary triggering mechanism - include both what the skill does AND specific contexts for when to use it. All "when to use" info goes here, not in the body. Note: currently Claude has a tendency to "undertrigger" skills -- to not use them when they'd be useful. To combat this, please make the skill descriptions a little bit "pushy". So for instance, instead of "How to build a simple fast dashboard to display internal Anthropic data.", you might write "How to build a simple fast dashboard to display internal Anthropic data. Make sure to use this skill whenever the user mentions dashboards, data visualization, internal metrics, or wants to display any kind of company data, even if they don't explicitly ask for a 'dashboard.'"
- **compatibility**: Required tools, dependencies (optional, rarely needed)
- **the rest of the skill :)**

### Skill Writing Guide

#### Anatomy of a Skill

```
skill-name/
├── SKILL.md (required)
│   ├── YAML frontmatter (name, version, description required)
│   └── Markdown instructions
└── Bundled Resources (optional)
    ├── scripts/    - Executable code for deterministic/repetitive tasks
    ├── references/ - Docs loaded into context as needed
    └── assets/     - Files used in output (templates, icons, fonts)
```

#### Progressive Disclosure

Skills use a three-level loading system:
1. **Metadata** (name + description) - Always in context (~100 words)
2. **SKILL.md body** - In context whenever skill triggers (<500 lines ideal)
3. **Bundled resources** - As needed (unlimited, scripts can execute without loading)

These word counts are approximate and you can feel free to go longer if needed.

**Key patterns:**
- Keep SKILL.md under 500 lines; if you're approaching this limit, add an additional layer of hierarchy along with clear pointers about where the model using the skill should go next to follow up.
- Reference files clearly from SKILL.md with guidance on when to read them
- For large reference files (>300 lines), include a table of contents

<!-- PATCH-5: references must be loaded via Read, never from memory | modified-by: qingting | modified-at: 2026-07-30 -->
**Non-negotiable rule for references/:**

When SKILL.md instructs you to consult a file under `references/` (or any other bundled resource), you **MUST use the `Read` tool to load the file at runtime**. **NEVER attempt to recall the reference content from memory** — even if you believe you have seen it before, even if the content looks familiar, even if the reference file is small.

Rationale:
- Reference files may have been updated since the skill was written
- Memory recall is unreliable for exact schemas, prompts, JSON structures, and code
- Skipping the `Read` breaks the Progressive Disclosure contract and produces silently wrong outputs

Violation of this rule is treated as a skill-execution bug, not a stylistic preference.
<!-- /PATCH-5 -->

**Domain organization**: When a skill supports multiple domains/frameworks, organize by variant:
```
cloud-deploy/
├── SKILL.md (workflow + selection)
└── references/
    ├── aws.md
    ├── gcp.md
    └── azure.md
```
Claude reads only the relevant reference file.

#### Principle of Lack of Surprise

This goes without saying, but skills must not contain malware, exploit code, or any content that could compromise system security. A skill's contents should not surprise the user in their intent if described. Don't go along with requests to create misleading skills or skills designed to facilitate unauthorized access, data exfiltration, or other malicious activities. Things like a "roleplay as an XYZ" are OK though.

#### Writing Patterns

Prefer using the imperative form in instructions.

**Defining output formats** - You can do it like this:
```markdown
## Report structure
ALWAYS use this exact template:
# [Title]
## Executive summary
## Key findings
## Recommendations
```

**Examples pattern** - It's useful to include examples. You can format them like this (but if "Input" and "Output" are in the examples you might want to deviate a little):
```markdown
## Commit message format
**Example 1:**
Input: Added user authentication with JWT tokens
Output: feat(auth): implement JWT-based authentication
```

### Writing Style

Try to explain to the model why things are important in lieu of heavy-handed musty MUSTs. Use theory of mind and try to make the skill general and not super-narrow to specific examples. Start by writing a draft and then look at it with fresh eyes and improve it.

### Test Cases

<!-- CUSTOMIZE: Phase 3 is optional — skip by default unless user explicitly requests testing. | modified-by: qingting | modified-at: 2026-07-30 -->

See `references/eval-test-cases.md`.

## Running and evaluating test cases <!-- CUSTOMIZE: Phase 4 is optional — skip by default unless user explicitly requests testing. | modified-by: qingting | modified-at: 2026-07-30 -->

See `references/eval-run-and-review.md`.

---

## Improving the skill

This is the heart of the loop. You've run the test cases, the user has reviewed the results, and now you need to make the skill better based on their feedback.

### How to think about improvements

1. **Generalize from the feedback.** The big picture thing that's happening here is that we're trying to create skills that can be used a million times (maybe literally, maybe even more who knows) across many different prompts. Here you and the user are iterating on only a few examples over and over again because it helps move faster. The user knows these examples in and out and it's quick for them to assess new outputs. But if the skill you and the user are codeveloping works only for those examples, it's useless. Rather than put in fiddly overfitty changes, or oppressively constrictive MUSTs, if there's some stubborn issue, you might try branching out and using different metaphors, or recommending different patterns of working. It's relatively cheap to try and maybe you'll land on something great.

2. **Keep the prompt lean.** Remove things that aren't pulling their weight. Make sure to read the transcripts, not just the final outputs — if it looks like the skill is making the model waste a bunch of time doing things that are unproductive, you can try getting rid of the parts of the skill that are making it do that and seeing what happens.

3. **Explain the why.** Try hard to explain the **why** behind everything you're asking the model to do. Today's LLMs are *smart*. They have good theory of mind and when given a good harness can go beyond rote instructions and really make things happen. Even if the feedback from the user is terse or frustrated, try to actually understand the task and why the user is writing what they wrote, and what they actually wrote, and then transmit this understanding into the instructions. If you find yourself writing ALWAYS or NEVER in all caps, or using super rigid structures, that's a yellow flag — if possible, reframe and explain the reasoning so that the model understands why the thing you're asking for is important. That's a more humane, powerful, and effective approach.

4. **Look for repeated work across test cases.** Read the transcripts from the test runs and notice if the subagents all independently wrote similar helper scripts or took the same multi-step approach to something. If all 3 test cases resulted in the subagent writing a `create_docx.py` or a `build_chart.py`, that's a strong signal the skill should bundle that script. Write it once, put it in `scripts/`, and tell the skill to use it. This saves every future invocation from reinventing the wheel.

This task is pretty important (we are trying to create billions a year in economic value here!) and your thinking time is not the blocker; take your time and really mull things over. I'd suggest writing a draft revision and then looking at it anew and making improvements. Really do your best to get into the head of the user and understand what they want and need.

### The iteration loop

See `references/eval-run-and-review.md`.

---

## Advanced: Blind comparison

For situations where you want a more rigorous comparison between two versions of a skill (e.g., the user asks "is the new version actually better?"), there's a blind comparison system. Read `agents/comparator.md` and `agents/analyzer.md` for the details. The basic idea is: give two outputs to an independent agent without telling it which is which, and let it judge quality. Then analyze why the winner won.

This is optional, requires subagents, and most users won't need it. The human review loop is usually sufficient.

---

## Description Optimization

See `references/description-optimization.md`.

---

<!-- CUSTOMIZE: Phase 6 packaging is commented out — not needed in this environment. | modified-by: qingting | modified-at: 2026-07-30 -->
<!--
### Package and Present (only if `present_files` tool is available)

Check whether you have access to the `present_files` tool. If you don't, skip this step. If you do, package the skill and present the .skill file to the user:

```bash
python -m scripts.package_skill <path/to/skill-folder>
```

After packaging, direct the user to the resulting `.skill` file path so they can install it.
-->
<!-- /CUSTOMIZE -->

---

## Claude.ai-specific instructions

See `references/platform-claude-ai.md`.

---

## Cowork-Specific Instructions

See `references/platform-cowork.md`.

---

## Reference files

The agents/ directory contains instructions for specialized subagents. Read them when you need to spawn the relevant subagent.

- `agents/grader.md` — How to evaluate assertions against outputs
- `agents/comparator.md` — How to do blind A/B comparison between two outputs
- `agents/analyzer.md` — How to analyze why one version beat another

The references/ directory has additional documentation:
- `references/schemas.md` — JSON structures for evals.json, grading.json, etc.

---

Repeating one more time the core loop here for emphasis:

- Figure out what the skill is about
- Draft or edit the skill
<!-- CUSTOMIZE: Phase 3+4 are optional — skip by default. | modified-by: qingting | modified-at: 2026-07-30 -->
- (Optional) Run with-skill and baseline test cases
- (Optional) Grade, benchmark, and review via eval viewer
<!-- /CUSTOMIZE -->
- Repeat until you and the user are satisfied
<!-- CUSTOMIZE: Phase 6 packaging is commented out. | modified-by: qingting | modified-at: 2026-07-30 -->
<!-- - Package the final skill and return it to the user -->
<!-- /CUSTOMIZE -->

Good luck!
