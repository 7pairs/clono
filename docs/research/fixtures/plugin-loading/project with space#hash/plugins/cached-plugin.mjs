const evaluationKey = Symbol.for("clono.research.plugin-loading.cached-plugin");
const evaluationCount = (globalThis[evaluationKey] ?? 0) + 1;

globalThis[evaluationKey] = evaluationCount;

export default {
  marker: "cached",
  evaluationCount,
};
