# Vivliostyleの目次生成に関する調査

- 状態: 調査済み
- 調査日: 2026-08-19
- 検証環境:
  - 実行環境: macOS、Node.js 24.19.0
  - HTML変換: `@vivliostyle/vfm` 2.7.0
  - PDF生成: `@vivliostyle/cli` 11.1.0
    - CLIが依存する`@vivliostyle/vfm` 2.7.0
    - CLIが使用するVivliostyle.js 2.44.1
  - PDF検証: `mupdf` 1.28.0

## 背景

Thunder Clawの前作では、Vivliostyleの手動目次作成機能を利用していた。独自の`catalog.js`へ前付、本文、付録、索引、後付の原稿ファイルを列挙し、正規表現で抽出した章と節の見出しから目次用Markdownを事前生成していた。

現在のVivliostyle CLIには目次の自動生成機能がある。前作の事前生成処理を再実装せずにThunder Clawの次回作の要件を満たせるか、またVivliostyleとclonoのどちらが何を担うべきかを判断するため、実際の書籍に近い原稿構成で調査した。

## Thunder Clawの要件

- 章番号とタイトル、参照先の紙面上のページ番号を表示する
- 各項目を、対応する見出しへ移動するPDF内部リンクにする
- 初期仕様では章と節の2階層を掲載し、小節は掲載しない
- 章と節へ異なるスタイルを適用できることが望ましい
- 前付は番号なし、本文は`第1章`と`1.1`、付録は`付録A`と`A.1`、後付は番号なしとする
- 前付、本文、付録、索引、後付を掲載対象とし、表紙、空白ページ、奥付は掲載しない
- 紙面上のページ番号は、前付から後付まで連続したアラビア数字とする
- 原稿の掲載順序は一つの定義を正本とし、目次生成のためだけに二重管理しない
- 要件を満たせるなら、自動生成と事前生成のどちらを使用するかは問わない
- 必要でなければ、前作の`catalog.js`と同じ独立した設定ファイルは設けない

## 公式情報の確認

Vivliostyle CLIは、設定の`toc`を有効にすると原稿から見出しを抽出し、目次文書を自動生成できる。`sectionDepth`で掲載する見出しの深さを指定でき、目次用の原稿を`rel: 'contents'`として`entry`内の任意の位置へ配置できる。

`toc`をオブジェクトで指定すると、目次のタイトル、出力先、見出しの深さに加えて、`transformDocumentList`と`transformSectionList`で生成する目次のHASTを調整できる。独自のHTMLテンプレートを使用する場合は、空の`nav[role="doc-toc"]`へ自動生成した項目が挿入される。

VivliostyleはCSSの`target-counter()`に対応しているため、目次項目のリンク先から章、節、ページのカウンター値を取得できる。番号とページ番号を事前生成した目次HTMLへ埋め込む必要はない。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/vivliostyle-table-of-contents/)をリポジトリ内へ保存している。このfixtureはclono本体から独立したnpmプロジェクトであり、`package.json`と`package-lock.json`によって検証環境の依存関係を固定する。

fixtureでは、次の10文書を一冊の書籍として構成した。

1. 表紙相当
2. 前付
3. 自動生成する目次
4. 本文第1章
5. 本文第2章
6. 付録A
7. 索引
8. 後付
9. 空白ページ相当
10. 奥付

掲載対象の文書には章と節を置き、掲載しないことを確認するために小節も置いた。表紙相当、空白ページ相当、奥付にも見出しを置いた。

### 書籍構造の正本

`vivliostyle.config.mjs`の`publication`配列を、原稿順序と目次掲載方針の唯一の定義とした。各要素は原稿のパス、文書の種類、目次へ掲載するかを持つ。この配列からVivliostyle CLIの`entry`を生成し、自動目次の変換処理も同じ情報を参照する。

```javascript
const publication = [
  { path: 'title.md', kind: 'excluded', includeInToc: false },
  { path: 'preface.md', kind: 'frontmatter', includeInToc: true },
  { kind: 'contents', includeInToc: false },
  { path: 'chapter-one.md', kind: 'chapter', includeInToc: true },
  { path: 'appendix-a.md', kind: 'appendix', includeInToc: true },
  { path: 'index.md', kind: 'backmatter', includeInToc: true },
];
```

この構成では、前作の`catalog.js`に相当する独立したファイルや、事前生成する目次Markdownは必要なかった。ただし、書籍構造を`vivliostyle.config.mjs`へ直接記述するか、別の設定から読み込むかは、clonoの設定設計で改めて判断する。

### 掲載対象と階層

`sectionDepth`を`2`に設定し、章と節だけを抽出した。`transformDocumentList`では、書籍構造の`includeInToc`に従って文書を選別し、掲載する文書へ前付、本文、付録、後付の分類を付与した。

Web出版物の自動検証では、次の結果を確認した。

- 前付、本文2章、付録A、索引、後付の章と節が、期待する順序で12項目生成される
- 小節、表紙相当、空白ページ相当、奥付は掲載されない
- 各リンク先のHTML文書と見出しIDが存在する
- `publication.json`の`readingOrder`が書籍構造の順序と一致する
- 目次文書が`rel: contents`として識別される

### 章番号と目次の表示

本文だけに`chapter.css`、付録だけに`appendix.css`を適用し、ページ先頭でそれぞれのカウンターを増加させた。前付、索引、後付には共通CSSだけを適用し、番号を付けなかった。

目次では文書の分類と見出し階層に応じて、リンク先の`chapter`、`appendix`、`section`カウンターを`target-counter()`で参照した。この結果、本文を`第1章`と`1.1`、付録を`付録A`と`A.1`の形式で表示できた。

`transformSectionList`で見出し文字列を`span.toc-title`へ包み、CSS GridとFlexboxを組み合わせた。章と節を文字サイズ、太さ、インデントで区別し、タイトルとページ番号の間へドットリーダーを表示できた。

### ページ番号とPDF内部リンク

目次項目のページ番号は、リンク先の`page`カウンターを`target-counter()`で参照した。生成した全10ページには1から10まで連続するノンブルを表示し、目次に表示したページ番号と一致することを確認した。

PDFの自動検証では、12件の目次項目について次を個別に確認した。

- 期待する番号、タイトル、ページ番号が表示される
- リンクが期待する見出しIDを指す
- リンク先が期待するページにある
- リンク先の座標が対象見出しの付近にある

生成したPDFは全10ページを画像へ変換し、目次が1ページに収まること、文字、番号、ドットリーダーに欠落、重なり、意図しない改行がないことを目視確認した。

## 調査結果

- Vivliostyle CLIの自動目次生成で、章と節の見出し、タイトル、リンク先を原稿から取得できる
- `sectionDepth`と変換関数を組み合わせ、掲載する階層と文書を制御できる
- CSSカウンターと`target-counter()`で、本文、付録、番号なし文書を区別した番号と、紙面上のページ番号を表示できる
- 自動生成された各項目は、同じ原稿ファイル内と別原稿ファイルの見出しへ移動するPDF内部リンクになる
- 章と節へ異なるスタイルを適用し、ドットリーダーを含む目次らしい紙面を構成できる
- 原稿順序と目次掲載方針を一つの書籍構造定義から導出でき、目次用のファイル一覧を二重管理する必要はない
- Thunder Clawの基本要件では、clonoが見出し一覧を走査して目次Markdownを事前生成する必要はない

## 責務判断

目次項目の抽出、タイトルとリンク先の生成、参照先のページ番号取得は、Vivliostyle CLI、Vivliostyle.jsおよびテーマCSSへ委譲できる。clonoは見出し一覧、章・節番号、ページ番号を計算せず、目次Markdownも生成しない方針とする。

一方、どの原稿をどの順序で組版し、前付、本文、付録、後付のどれとして扱い、目次へ掲載するかという書籍構造は入力として必要である。これを利用者が`vivliostyle.config.mjs`へ直接記述するか、clonoが扱う設定からVivliostyle用の構成へ変換するかは未決定である。

### Vivliostyleへ委譲する責務

- 原稿から指定階層までの見出し、タイトル、リンク先を抽出する
- 抽出した情報から目次文書を生成する
- `target-counter()`でリンク先の章番号、節番号、付録番号、ページ番号を取得する
- 各項目をPDF内部リンクとして保持する
- 設定の変換関数に従って掲載文書と出力構造を調整する
- テーマCSSに従って階層、番号、ドットリーダー、ページ番号を表示する

### clonoが担う候補となる責務

- 原稿順序、文書の種類、目次への掲載有無を一つの書籍構造として表現できる入力契約を提供する
- 必要に応じて、その書籍構造をVivliostyle CLIの`entry`と目次設定へ変換する
- 見出しの明示的なIDを壊さずVFMへ渡す
- 書籍構造の不足、重複、矛盾や、目次対象となる見出しのIDを診断する候補とする

目次の紙面デザイン、章・節・付録の番号形式、掲載階層は、書籍ごとの差異を許容すべきテーマまたは設定の責務とする。これらをclono本体へ固定しない。

## 成立条件と未確認事項

今回の結果は、一つのMarkdownファイルが一つの章または付録に対応し、各文書の先頭に一つの章見出しがある構成を前提とする。

次の事項は未確認または未決定である。

- Thunder Clawの実際の原稿数と長いタイトルを含む複数ページの目次
- 製品用テーマにおける目次の紙面レイアウト
- 目次掲載対象と文書種別を指定するclonoの設定または入力契約
- `vivliostyle.config.mjs`をclonoが生成するか、利用者の設定として維持するか
- 一つの原稿ファイルへ複数の章を記述する構成
- 目次項目として表示するタイトルを本文の見出しとは別に指定する必要性
- 見出しにインラインコード、強調、リンクなどを含む場合の表示

実際の原稿数、長いタイトル、製品用テーマとの統合は責務境界ではなく紙面の受け入れ条件であるため、次回作の原稿とテーマが具体化した段階で検証する。

## 再現方法

検証に使用する入力、Web出版物とPDFの自動検証、PDFの目視確認手順は、[検証用fixtureのREADME](fixtures/vivliostyle-table-of-contents/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm run verify`で再検証できる。

## 再調査する条件

- Vivliostyle CLIの自動目次設定、変換関数または生成するHASTの契約が変更された場合
- VFMが見出しから生成するHTML構造や明示的なIDの扱いを変更した場合
- Vivliostyle.jsのCSSカウンター、`target-counter()`またはPDF内部リンクの実装が変更された場合
- 書籍の原稿構成が、一章または一付録につき一ファイルという前提を満たさなくなった場合
- Thunder Clawの原稿や製品用テーマで、掲載対象、番号、改ページまたはリンクが期待どおりにならなかった場合

## 参照資料

- [目次の作成 | Vivliostyle Documentation](https://docs.vivliostyle.org/ja/cli/toc-page/)
- [チュートリアル⑦目次の作成 | Vivliostyle](https://vivliostyle.org/ja/tutorials/create-table-of-contents/)
- [Vivliostyle CLI Config Reference](https://github.com/vivliostyle/vivliostyle-cli/blob/main/docs/config.md)
