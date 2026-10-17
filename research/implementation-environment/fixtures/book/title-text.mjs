// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
export default async (node, chapter) => ({
  type: 'html', value: `<h1 class="chapter-title">TEXT_TITLE ${chapter.number} ${chapter.title}</h1>`,
});
