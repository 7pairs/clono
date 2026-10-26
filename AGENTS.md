<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# Instructions for AI Contributors

## Development Workflow

- Before discussing or performing development work, read the [development workflow](docs/development-workflow.md) and the project charter, Sisters Driven Development, and Sora Flow documents referenced in its first section. Follow their guidance.

## Japanese Documentation

- When creating or updating Japanese documentation, use the `yomiyasu` skill to revise the text for readability and natural Japanese. This includes document drafts and proposed document revisions presented in chat, without requiring a separate request to use the skill.
- Preserve the meaning, requirements, author's intent, voice, and style when revising. For updates, apply this rule to the text you create or change; do not rewrite unrelated existing content.
- The following are exempt from this requirement:
  - Ordinary conversation, discussion, and explanations in chat that are not document drafts or proposed document revisions.
  - Handoff documents created or updated using the `session-handoff` skill.

## Information Intended for Publication

- Follow [the publication checks in the development workflow](docs/development-workflow.md#82-公開する情報の確認). Before saving or sharing deliverables, inspect added or changed content, including documentation, code, configuration, PR descriptions, review replies, logs, and images, using searches and visual inspection as appropriate.
- Remove, redact, or generalize unnecessary personal environment paths, machine names, private identifiers, and credentials. Preserve intentionally public information, such as copyright notices, public account names, and third-party metadata. If uncertain, discuss the information's location and purpose without copying its value into another deliverable or reply.
- For agreed edits to saved records, identify the published copy as processed, preserve technical meaning, and check the diff, structured data, hashes, and verification references as applicable. Do not regenerate experiments or rewrite historical conclusions merely to sanitize a record.
- Changes to Git history or external services require a separate explicit agreement on scope and an execution request. Updating current files does not remove previously published copies. Do not add a dedicated automated checker or CI job for these checks.
