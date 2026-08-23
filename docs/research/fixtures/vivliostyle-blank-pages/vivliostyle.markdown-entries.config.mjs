const entry = (path, output) => ({ path, output, theme: 'style.css' });
const blankEntry = (output) => entry('manuscripts/blank.md', output);

export default {
  title: '空白ページ検証：Markdownエントリ',
  language: 'ja',
  entry: [
    blankEntry('blank-pages/markdown-start.html'),
    entry('manuscripts/content-before.md'),
    blankEntry('blank-pages/markdown-middle.html'),
    entry('manuscripts/content-middle.md'),
    blankEntry('blank-pages/markdown-consecutive-a.html'),
    blankEntry('blank-pages/markdown-consecutive-b.html'),
    entry('manuscripts/content-final.md'),
    blankEntry('blank-pages/markdown-end.html'),
  ],
};
