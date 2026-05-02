# Contributing to ForkFront

Thank you for your interest in contributing. This guide covers what you need to know before
submitting a pull request. The [parent project](https://github.com/fancypantalons/NetHack-Android-DS)
follows the same conventions and is worth reading if your work touches the native engine or build system.

## Before You Start

### Design Principles

Every change should respect the core design philosophy:

- The soft keyboard must never be required for gameplay.
- Interactions should feel native to Android, not like a ported desktop UI.
- Configurability is a feature. Don't hard-code what can reasonably be a preference.
- Fullscreen and immersive mode are always on. Don't add opt-outs.

When in doubt, ask yourself whether a seasoned Android gamer would find the interaction natural.

### Opening an Issue First

For significant new features or architectural changes, open a GitHub issue before writing code.
This avoids the situation where you invest effort in something that doesn't align with the project's
direction. For bug fixes and small improvements, a PR without a prior issue is fine.

## Code Style

ForkFront is a **Java-only** project. Do not introduce Kotlin or suggest migration.

All Java source must be formatted with [google-java-format](https://github.com/google/google-java-format)
at its default settings. Formatting is enforced mechanically — the pre-commit hook (see below) will
block any commit where staged files don't comply.

Beyond what the formatter enforces:

- Keep the existing package structure under `lib/src/com/tbd/forkfront/`. Do not reorganize packages
  without a discussion first.
- Comments should explain *why*, not *what*. Well-named identifiers make the what obvious. Skip
  the comment if a future reader wouldn't be confused without it.
- Do not use decorative separator comments (`// ------`, `// ______`, box-style section headers,
  etc.). They add visual noise without conveying information.

## Development Setup

### Formatting

To reformat all Java source files in place:

```
./gradlew googleJavaFormat
```

To check formatting without modifying any files (exits non-zero if anything is out of compliance):

```
./gradlew googleJavaFormatCheck
```

### Pre-commit Hook

A pre-commit hook is provided that enforces the formatting standard at commit time. It checks only
staged files, so it's fast and won't block you over issues in files you didn't touch.

Enable it once per clone:

```
git config core.hooksPath .githooks
```

If the hook blocks a commit, run `./gradlew googleJavaFormat`, re-stage the fixed files, and commit
again. The hook does not auto-fix anything.

## Pull Requests

### Commit Discipline

This is the most important section. PRs are reviewed commit by commit, so the quality of individual
commits matters as much as the final diff.

**Commits should:**

- Be small and focused. Aim for 200 lines or fewer per commit, excluding mechanical changes like
  renames or reformatting. This is a guideline, not a hard rule, but if you're consistently going
  over it, the commit probably does too much.
- Tell a coherent story. A reviewer should be able to read the commit series from top to bottom and
  understand not just what changed, but *why* and *in what order*.
- Build and run correctly in isolation. Don't leave the project in a broken state mid-series.

**Rewriting your commits before opening a PR is strongly encouraged.** Use `git rebase -i` to
squash fixups, split mixed concerns, reorder steps, and clean up commit messages. The goal is a
history that looks intentional, not a transcript of your working process.

### Commit Messages

All commit messages must follow the [Conventional Commits](https://www.conventionalcommits.org/)
standard:

```
<type>(<scope>): <short description>

[optional body]
```

Common types: `feat`, `fix`, `refactor`, `style`, `docs`, `chore`, `perf`.

The short description should be clear and readable on its own — imagine someone scanning a
`git log --oneline`. Lowercase, imperative mood, no trailing period.

A body is expected whenever the commit isn't self-explanatory: explain the motivation, the
trade-off, or the constraint that drove the decision. A one-line summary-only message is only
appropriate for genuinely trivial changes.

Good examples:
```
fix(gamepad): ignore axis events below deadzone threshold

fix(map): correct tile offset calculation for 16KB-aligned builds

feat(settings): add per-context gamepad binding editor

refactor(engine): split NetHackIO dispatch into separate handler methods
```

### Submitting a PR

- Open PRs against `master`.
- Write a clear PR description. Explain what the change does and why, reference any related issues
  (`Fixes #123`), and call out anything the reviewer should pay particular attention to.
- PRs should represent a complete, reviewable unit of work. Don't submit work-in-progress PRs
  expecting to finish them in review.
- Keep unrelated changes out of the PR. If you noticed a bug while working on something else,
  fix it in a separate PR.

### Quality Assurance

There is no automated test suite at this time. Contributors are expected to manually verify their
changes on a device or emulator before submitting. At a minimum:

- Build successfully via `./gradlew assembleDebug` from `sys/android/`.
- Launch the game and exercise the affected code paths.
- Check that nothing obviously regressed in adjacent functionality.

The maintainer may ask for specific QA steps as part of review.

### Merge Policy

Merging is the maintainer's call. A PR with a clean commit series may be merged as-is to preserve
the history. A PR with a messier history may be squashed. Either way, the individual commits in
your PR are the primary unit of review, so make them count.

## AI-Assisted Contributions

AI-generated contributions are welcome under the following conditions:

1. **Human review is required.** Every AI-generated change must be read and understood by a human
   contributor before the PR is opened. "The AI wrote it and it looks fine" is not review.
   You are responsible for what you submit.
2. **The same standards apply.** Commit discipline, message quality, PR description, and QA
   expectations are not relaxed for AI-generated code.
3. **No AI attribution in commit messages or PR descriptions.** Commits are attributed to the
   human who reviewed and submitted the work. Do not include "Generated by...", "Co-authored-by AI",
   or similar language anywhere in the commit history.
