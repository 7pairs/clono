const common = {
  title: "Multiple Short Footnotes",
  language: "ja",
  size: "A5",
  theme: "cases/footnotes/multiple-short/footnotes.css",
  entry: ["cases/footnotes/multiple-short/manuscript.md"],
  copyAsset: {
    excludes: ["output/**"],
  },
};

export default ["dpub", "gcpm"].map((mode) => ({
  ...common,
  vfm: {
    footnote: mode,
  },
  output: [`output/footnotes/multiple-short/${mode}.pdf`],
  workspaceDir: `output/footnotes/multiple-short/workspace-${mode}`,
}));
