const marker = await Promise.resolve("top-level-await");

export default {
  marker,
  name: "research-top-level-await-plugin",
  version: "0.0.0",
  apiVersion: 1,
  renderers: {
    column(input) {
      return `top-level-await:${input.title}:${input.body}`;
    },
  },
};
