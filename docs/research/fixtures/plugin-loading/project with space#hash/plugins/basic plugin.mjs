export default {
  marker: "basic",
  name: "research-basic-plugin",
  version: "0.0.0",
  apiVersion: 1,
  renderers: {
    column(input) {
      return `basic:${input.title}:${input.body}`;
    },
  },
};
