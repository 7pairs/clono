const common = {
  title: "Minimal Footnote",
  language: "ja",
  size: "A5",
  theme: "cases/footnotes/minimal/footnotes.css",
  entry: ["cases/footnotes/minimal/manuscript.md"],
  copyAsset: {
    excludes: ["output/**"],
  },
};

export default ["pandoc", "dpub", "gcpm"].map((mode) => ({
  ...common,
  vfm: {
    footnote: mode,
  },
  output: [`output/footnotes/minimal/${mode}.pdf`],
  workspaceDir: `output/footnotes/minimal/workspace-${mode}`,
}));
