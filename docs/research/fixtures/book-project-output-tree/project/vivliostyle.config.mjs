import { outputRoot, publication } from './book.mjs';

export default {
  title: '書籍プロジェクト出力ツリー検証',
  language: 'ja',
  entry: publication.map(({ path }) => ({
    path: `${outputRoot}/${path}`,
    theme: 'themes/theme.css',
  })),
};

