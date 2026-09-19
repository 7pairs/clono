export default {
  name: "research-throwing-plugin",
  version: "0.0.0",
  apiVersion: 1,
  renderers: {
    column() {
      throw new Error("The renderer deliberately failed");
    },
  },
};
