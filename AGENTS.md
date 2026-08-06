# Repository instructions

## Required context

Before making changes, read:

- `docs/project-charter.md`
- The relevant ADRs under `docs/decisions/`
- `docs/development.md` for the current development workflow

Do not silently override an established decision. If implementation needs
conflict with the charter or an ADR, raise the conflict before
changing the project direction.

## Language

- Use English for source code, identifiers, code comments, log messages, CLI
  messages, and `README.md`.
- Use Japanese for specifications, design documents, development documents,
  issues, pull requests, and discussions with the maintainer.
- Japanese text may be used in test data when testing Japanese documents or
  typesetting behavior.

## Development commands

- Install dependencies reproducibly with `npm ci`.
- Run tests with `npm test`.
- Run tests continuously with `npm run test:watch`.
- Create a release build with `npm run build`.
- Inspect the npm package with `npm pack --dry-run`.

## Engineering guidelines

- Preserve transformation correctness and inspectable VFM-compatible Markdown
  as prerequisites for new functionality.
- Keep clono responsible for preprocessing Markdown. Do not make it run or
  replace Vivliostyle without an explicit design decision.
- Prefer VFM, HTML, CSS, and existing Vivliostyle capabilities over adding
  custom syntax.
- Do not introduce syntax or public CLI behavior before its requirements have
  been discussed.
- Keep Node.js-specific I/O separate from pure transformation and decision
  logic where practical.
- Add or update tests for behavior changes.
- Update specifications, ADRs, and development documentation in
  the same change when they are affected.
- Avoid abstractions and dependencies that do not serve a current production
  use case.

## Generated files and publishing

- Do not edit or commit files under `dist/`, `target/`, or `.shadow-cljs/`.
- Do not publish the npm package or remove `private: true` without explicit
  approval.
- Keep `package.json` and `package-lock.json` consistent when package metadata
  or dependencies require lockfile changes.
