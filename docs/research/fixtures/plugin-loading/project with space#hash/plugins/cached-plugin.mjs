const evaluationKey = Symbol.for("clono.research.plugin-loading.cached-plugin");
const evaluationCount = (globalThis[evaluationKey] ?? 0) + 1;

globalThis[evaluationKey] = evaluationCount;

export default {
  marker: "cached",
  evaluationCount,
  name: "research-cached-plugin",
  version: "0.0.0",
  apiVersion: 1,
  renderers: {
    column(input) {
      return `cached:${input.title}:${input.body}`;
    },
  },
};
