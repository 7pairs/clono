const publication = [
  { path: 'title.md', kind: 'excluded', includeInToc: false },
  { path: 'preface.md', kind: 'frontmatter', includeInToc: true },
  { kind: 'contents', includeInToc: false },
  { path: 'chapter-one.md', kind: 'chapter', includeInToc: true },
  { path: 'chapter-two.md', kind: 'chapter', includeInToc: true },
  { path: 'appendix-a.md', kind: 'appendix', includeInToc: true },
  { path: 'index.md', kind: 'backmatter', includeInToc: true },
  { path: 'afterword.md', kind: 'backmatter', includeInToc: true },
  { path: 'blank.md', kind: 'excluded', includeInToc: false },
  { path: 'colophon.md', kind: 'excluded', includeInToc: false },
];

const documentByOutput = new Map(
  publication
    .filter(({ path }) => path)
    .map((document) => [document.path.replace(/\.md$/u, '.html'), document]),
);

function element(tagName, properties = {}, children = []) {
  return { type: 'element', tagName, properties, children };
}

function text(value) {
  return { type: 'text', value };
}

function raw(value) {
  return { type: 'raw', value };
}

function addDocumentKind(node, kind) {
  if (node.type !== 'element' || node.tagName !== 'li') return node;
  return {
    ...node,
    properties: {
      ...node.properties,
      'data-document-kind': kind,
    },
  };
}

function transformDocumentList(nodeList) {
  return (propsList) =>
    element(
      'ol',
      {},
      nodeList.flatMap((document, index) => {
        const metadata = documentByOutput.get(document.href);
        if (!metadata?.includeInToc) return [];

        const children = [propsList[index].children].flat(2);
        if (document.sections?.length === 1 && document.sections[0].level === 1) {
          return children.flatMap((child) =>
            child.type === 'element' && child.tagName === 'ol'
              ? child.children.map((item) => addDocumentKind(item, metadata.kind))
              : child,
          );
        }

        return [
          element('li', { 'data-document-kind': metadata.kind }, [
            element('a', { href: document.href }, [text(document.title)]),
            ...children,
          ]),
        ];
      }),
    );
}

function transformSectionList(nodeList) {
  return (propsList) =>
    element(
      'ol',
      {},
      nodeList.map((section, index) => {
        const title = element('span', { className: ['toc-title'] }, [
          raw(section.headingHtml),
        ]);
        const label = section.href
          ? element('a', { href: section.href }, [title])
          : element('span', {}, [title]);
        return element('li', { 'data-section-level': section.level }, [
          label,
          ...[propsList[index].children].flat(2),
        ]);
      }),
    );
}

function themeFor(kind) {
  if (kind === 'chapter') return ['style.css', 'chapter.css'];
  if (kind === 'appendix') return ['style.css', 'appendix.css'];
  return 'style.css';
}

function toEntry(document) {
  if (document.kind === 'contents') {
    return {
      path: 'toc-template.html',
      output: 'toc.html',
      rel: 'contents',
      theme: 'style.css',
    };
  }
  return {
    path: document.path,
    theme: themeFor(document.kind),
  };
}

export default {
  title: '目次自動生成検証',
  language: 'ja',
  entry: publication.map(toEntry),
  toc: {
    title: '目次',
    htmlPath: 'toc.html',
    sectionDepth: 2,
    transformDocumentList,
    transformSectionList,
  },
};
