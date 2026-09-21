function escapeHtmlText(value) {
  return value
    .replace(/&/gu, "&amp;")
    .replace(/</gu, "&lt;")
    .replace(/>/gu, "&gt;")
    .replace(/"/gu, "&quot;")
    .replace(/'/gu, "&#39;")
    .replace(/\0/gu, "&#0;");
}

export function customColumnRenderer(input) {
  return [
    '<aside class="clono-column custom-column">',
    '<div class="custom-column-outer">',
    '<div class="custom-column-inner">',
    '<p class="clono-column-title custom-column-title">',
    '<span class="custom-column-title-mark" aria-hidden="true">COLUMN</span>',
    `<span class="custom-column-title-text">${escapeHtmlText(input.title)}</span>`,
    "</p>",
    '<div class="custom-column-body">',
    "",
    input.body,
    "",
    "</div>",
    "</div>",
    "</div>",
    "</aside>",
  ].join("\n");
}
