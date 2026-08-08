const common = {
  title: "Footnote Reference Page",
  language: "ja",
  size: "A5",
  theme: "cases/footnotes/reference-page/footnotes.css",
  entry: ["cases/footnotes/reference-page/manuscript.md"],
  copyAsset: {
    excludes: ["output/**"],
  },
};

export default ["dpub", "gcpm"].map((mode) => ({
  ...common,
  vfm: {
    footnote: mode,
  },
  output: [`output/footnotes/reference-page/${mode}.pdf`],
  workspaceDir: `output/footnotes/reference-page/workspace-${mode}`,
}));
