# Git Hooks

This directory contains git hooks that are automatically installed when you build the project.

## Available Hooks

### pre-commit
Automatically runs Spotless code formatter on staged Java files before each commit.

**What it does:**
1. Detects staged Java files
2. Runs `./gradlew spotlessApply` to format code
3. Re-stages the formatted files
4. Blocks the commit if formatting fails

## Installation

Git hooks are automatically installed when you run:

```bash
./gradlew build
```

Or manually install them with:

```bash
./gradlew installGitHooks
```

## How It Works

The `installGitHooks` Gradle task copies all files from `gradle/git-hooks/` to `.git/hooks/` with executable permissions. This happens automatically during the build process, ensuring all developers have the same hooks installed.

## Adding New Hooks

1. Create a new hook file in `gradle/git-hooks/` (e.g., `pre-push`, `commit-msg`)
2. Make sure it's executable: `chmod +x gradle/git-hooks/your-hook`
3. Commit the file to the repository
4. Run `./gradlew installGitHooks` or build the project

The hook will be automatically installed for all developers on their next build.

## Skipping Hooks

If you need to skip the pre-commit hook (not recommended), use:

```bash
git commit --no-verify
```

## Notes

- Git hooks are local to each developer's machine and are not committed to `.git/hooks/`
- The source hooks in `gradle/git-hooks/` ARE committed to the repository
- Hooks are automatically reinstalled on every build to ensure they stay up-to-date
