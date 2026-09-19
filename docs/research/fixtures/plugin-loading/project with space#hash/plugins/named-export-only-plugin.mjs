export const plugin = {
  name: "research-named-export-only-plugin",
  version: "0.0.0",
  apiVersion: 1,
  renderers: {
    column() {
      return "<aside>unreachable</aside>";
    },
  },
};
