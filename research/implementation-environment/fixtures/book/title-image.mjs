// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
export default (node, chapter) => ({
  type: 'html',
  value: `<div style="display:none"><h1>${chapter.title}</h1></div><div class="chapter-title"><img src="../${chapter.image}" alt="TITLE_IMAGE"></div>`,
});
