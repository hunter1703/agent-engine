# Agent Guidelines

## Project Summary
Agent Engine is a modular Java 21/Quarkus agent runtime built on LangChain4j. It provides a
plugin-based tool system, configurable agent definitions, and multiple interface modules (CLI
and REST) for interacting with agents.

## Project Goals
- Provide a production-ready, pluggable agent runtime with clear interfaces for custom agents,
  tools, context management, and persistence.
- Support local and hosted model backends through model registry configs and plugin-delivered
  agent configs.
- Offer lightweight interface modules (CLI and REST) to validate and extend the agent ecosystem.

## Development Guidelines
1. Follow established software development best practices.
2. Maintain high coding standards throughout the codebase.
3. Prefer simple, straightforward solutions over cleverness.
4. Keep code readable and easy to maintain.
5. Ensure the code remains testable.
6. For any new feature, bug fix, or behavior change, add or update corresponding tests.
7. Keep implementations efficient.
8. Favor small, focused changes and avoid unnecessary refactors.
9. Update relevant documentation when behavior changes.
10. Keep code extensible with clear responsibilities and boundaries; add abstractions only when
    they clarify ownership and reduce duplication.
11. Use `final` wherever possible to emphasize immutability.
12. Prefer `static` methods for utility semantics.
13. Make an explicit choice to treat classes as singleton services or utility classes.
14. Reuse existing utility methods instead of reimplementing similar logic in private methods.
15. Create new utility classes or extend existing ones when it improves reuse.
16. When fixing a bug, first write a unit test that reproduces the bug and fails, then implement the fix, then rerun the test to verify it passes.
17. Leverage Java 21 features (virtual threads, string templates, records) where they improve clarity or performance.
18. Place shared Gradle configuration (toolchains, Spotless, preview flags) in the conventions plugin rather than repeating snippets in module build files.
19. Avoid redundant or low-value tests that do not exercise functional behavior.
