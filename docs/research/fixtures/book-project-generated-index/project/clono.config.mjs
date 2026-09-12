export default {
  sourceRoot: 'manuscripts',
  outputRoot: 'build/manuscripts',
  publication: [
    {
      type: 'document',
      path: 'preface.md',
      kind: 'frontmatter',
      includeInToc: true,
    },
    {
      type: 'document',
      path: 'chapter-one.md',
      kind: 'chapter',
      includeInToc: true,
    },
    {
      type: 'document',
      path: 'nested/chapter-two.md',
      kind: 'chapter',
      includeInToc: true,
    },
    {
      type: 'document',
      path: 'appendix #notes.md',
      kind: 'appendix',
      includeInToc: true,
    },
    {
      type: 'index',
      path: 'generated/index.md',
      title: '索引',
      includeInToc: true,
    },
    {
      type: 'document',
      path: 'afterword.md',
      kind: 'backmatter',
      includeInToc: false,
    },
  ],
};
