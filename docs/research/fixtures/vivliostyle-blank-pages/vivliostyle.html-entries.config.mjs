const entry = (path, output) => ({ path, output, theme: 'style.css' });
const blankEntry = (output) => entry('manuscripts/blank.html', output);

export default {
  title: '空白ページ検証：HTMLエントリ',
  language: 'ja',
  entry: [
    blankEntry('blank-pages/html-start.html'),
    entry('manuscripts/content-before.md'),
    blankEntry('blank-pages/html-middle.html'),
    entry('manuscripts/content-middle.md'),
    blankEntry('blank-pages/html-consecutive-a.html'),
    blankEntry('blank-pages/html-consecutive-b.html'),
    entry('manuscripts/content-final.md'),
    blankEntry('blank-pages/html-end.html'),
  ],
};
