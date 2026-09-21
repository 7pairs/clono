function escapeHtmlText(value) {
  return value
    .replace(/&/gu, "&amp;")
    .replace(/</gu, "&lt;")
    .replace(/>/gu, "&gt;")
    .replace(/"/gu, "&quot;")
    .replace(/'/gu, "&#39;");
}

export default {
  name: "research-project-column-renderer",
  version: "0.0.0",
  apiVersion: 1,
  renderers: {
    column(input) {
      return [
        '<aside class="clono-column project-column">',
        `<p class="clono-column-title">${escapeHtmlText(input.title)}</p>`,
        '<div class="project-column-body">',
        "",
        input.body,
        "",
        "</div>",
        "</aside>",
      ].join("\n");
    },
  },
};
