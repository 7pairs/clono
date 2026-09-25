function escapeHtmlText(value) {
  return value
    .replace(/&/gu, "&amp;")
    .replace(/</gu, "&lt;")
    .replace(/>/gu, "&gt;")
    .replace(/"/gu, "&quot;")
    .replace(/'/gu, "&#39;");
}

export default {
  name: "layered-column",
  version: "1.0.0",
  apiVersion: 1,
  renderers: {
    column({ title, body }) {
      return [
        '<div class="clono-column column">',
        '<div class="column-frame">',
        '<div class="column-content">',
        '<h4 class="clono-column-title column-title">',
        '<span class="column-title-decoration" aria-hidden="true"></span>',
        `<span class="column-title-text">${escapeHtmlText(title)}</span>`,
        "</h4>",
        "",
        body,
        "",
        "</div>",
        "</div>",
        "</div>",
      ].join("\n");
    },
  },
};
