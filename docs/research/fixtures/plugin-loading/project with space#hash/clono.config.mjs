const plugins = await Promise.resolve([
  "./plugins/basic plugin.mjs",
  "./plugins/top-level-await#plugin.mjs",
  "./plugins/cached-plugin.mjs",
]);

export default { plugins };
