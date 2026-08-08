const common = {
  title: "Footnote in Table",
  language: "ja",
  size: "A5",
  theme: "cases/footnotes/table/footnotes.css",
  entry: ["cases/footnotes/table/manuscript.md"],
  copyAsset: {
    excludes: ["output/**"],
  },
};

export default ["dpub", "gcpm"].map((mode) => ({
  ...common,
  vfm: {
    footnote: mode,
  },
  output: [`output/footnotes/table/${mode}.pdf`],
  workspaceDir: `output/footnotes/table/workspace-${mode}`,
}));
