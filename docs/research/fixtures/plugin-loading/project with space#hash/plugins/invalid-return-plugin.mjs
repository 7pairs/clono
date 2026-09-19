export default {
  name: "research-invalid-return-plugin",
  version: "0.0.0",
  apiVersion: 1,
  renderers: {
    column() {
      return { html: "<aside>invalid</aside>" };
    },
  },
};
