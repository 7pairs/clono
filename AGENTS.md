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

## Names and Forms of Address

- Follow [the naming rules in the development workflow](docs/development-workflow.md#83-名前と呼称の使用). Sora and Umi may be named in definitions, explanations of roles and relationships, work records, and rules that identify who is responsible for an action.
- Express status fields and completion criteria through roles, stages, and their states, without personal names. Work records may identify reviewers by name.
- Do not use private sibling forms of address for the developer in project documents, including work records, or in public text such as PR descriptions and review replies. This does not prohibit describing the defined sibling relationships. Ordinary chat forms of address remain unchanged.
- Preserve names used in historical records, including former names. Check context and follow the agreed scope when correcting wording; do not rename people throughout past records or rewrite past decisions and events. Check naming and forms of address together with the publication checks before saving or sharing deliverables.
