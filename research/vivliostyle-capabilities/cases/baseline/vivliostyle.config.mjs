export default {
  title: "Baseline",
  language: "ja",
  size: "A5",
  theme: "styles/base.css",
  entry: ["cases/baseline/manuscript.md"],
  copyAsset: {
    excludes: ["output/**"],
  },
  output: ["output/baseline/baseline.pdf"],
  workspaceDir: "output/baseline/workspace",
};
