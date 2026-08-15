# AGENTS.md

This file provides working instructions for AI agents contributing to `clono`.

## Documents to Read Before Working

- Read the [project charter](docs/project-charter.md) to understand the project's purpose, design principles, and development policy.
- Before working on design or technology choices, review the relevant architectural decision records in [docs/decisions](docs/decisions/).
- Refer to the [README](README.md) for development environment requirements and basic commands.
- Treat documents in the repository as the source of truth for specifications, design decisions, and development conventions. Do not rely only on conversation history.

## Language

- Write specifications, design documents, Issues, Pull Requests, and reviews in Japanese.
- Follow a lightweight form of Conventional Commits and write the commit subject in Japanese.
- Write program identifiers and code comments in English.
- Write GitHub Actions workflow, job, and step `name` values in concise English so they are easy to scan on GitHub.
- Avoid long explanatory code comments. Move such explanations to Japanese documentation when appropriate.

## Development

- Use npm as the entry point for package management and development commands.
- Do not invoke shadow-cljs directly or from a global installation. Use the npm scripts defined in `package.json`.
- Do not directly edit or commit generated output or caches such as `dist/`, `target/`, or `.shadow-cljs/`.
- When a change affects specifications or design decisions, update the relevant documentation in the same Pull Request as the implementation.
- Before implementing functionality that may overlap with Vivliostyle's responsibilities, investigate the relevant Vivliostyle specifications and implementation status.
- Design boundaries with the future plugin system in mind, but do not implement extension mechanisms before concrete requirements exist.

## Tests

- Use `cljs.test` for tests and suffix test namespaces with `-test`.
- Write `testing` descriptions in English.
- Use `When <condition or action>, then <observable result>` as the basic form.
- Describe externally observable behavior rather than implementation steps.
- Capitalize `When` and `Given`, write `then` in lowercase after the comma, and do not add a period at the end.
- When grouping test cases by multiple preconditions makes them easier to understand, an outer `testing` block described as `Given <context>` may contain inner `testing` blocks in the `When ..., then ...` form.
- Grouping with `Given` is optional. It may be used when it clarifies the test's intent even if it contains only one child `testing` block.
- Describe a state or input in `Given`, not the setup procedure itself.
- As a rule, limit `testing` nesting to two levels: `Given` and `When`. If a test becomes too large, split it into multiple `deftest` forms instead of adding deeper nesting.

```clojure
(deftest command-result-test
  (testing "Given no arguments"
    (testing "When the command is evaluated, then usage is returned successfully"
      ...)

    (testing "When the output destination is inspected, then standard output is selected"
      ...))

  (testing "Given an unknown argument"
    (testing "When the command is evaluated, then usage is returned as an error"
      ...)))
```

## Verification

- After changing ClojureScript implementation or tests, run at least `npm test`.
- After changing the CLI or build configuration, also run `npm run build:release` and `node dist/clono.js --help`.
- After documentation-only changes, verify links, documented commands, and consistency with the implementation.
- If any required verification could not be run, report why and identify what remains unverified.

## Git

- Do not commit, amend, rebase, merge, push, create branches, or delete branches unless explicitly requested by the project owner.
- Treat existing uncommitted changes as work owned by the project owner. Do not overwrite or discard changes outside the requested scope.
