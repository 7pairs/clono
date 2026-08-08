const common = {
  title: "Long Footnote",
  language: "ja",
  size: "A5",
  theme: "cases/footnotes/long/footnotes.css",
  entry: ["cases/footnotes/long/manuscript.md"],
  copyAsset: {
    excludes: ["output/**"],
  },
};

export default ["dpub", "gcpm"].map((mode) => ({
  ...common,
  vfm: {
    footnote: mode,
  },
  output: [`output/footnotes/long/${mode}.pdf`],
  workspaceDir: `output/footnotes/long/workspace-${mode}`,
}));
