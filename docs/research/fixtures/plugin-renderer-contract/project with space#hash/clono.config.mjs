const plugins = await Promise.resolve([
  "./plugins/project column#renderer.mjs",
]);

export default {
  sourceRoot: "manuscripts",
  outputRoot: "build/manuscripts",
  publication: [
    {
      type: "document",
      path: "chapter.md",
      kind: "chapter",
      includeInToc: true,
    },
  ],
  plugins,
};
