# Vivliostyle自動目次検証用fixture

## 目的

Vivliostyle CLIの自動目次生成を利用し、Thunder Clawの次回作に必要な目次を事前生成なしで構成できるか検証する。

## 検証対象

- 前付、本文、付録、索引、後付の章と節を目次へ掲載する
- 表紙相当、目次自身、空白ページ、奥付を目次へ掲載しない
- 小節を目次へ掲載しない
- 前付、索引、後付には番号を表示しない
- 本文を`第1章`、`1.1`、付録を`付録A`、`A.1`の形式で表示する
- 各項目へ、紙面に表示される連続したページ番号を表示する
- 各項目を、対応する見出しへのPDF内部リンクにする
- 章と節へ異なるスタイルを適用できる
- 原稿の掲載順と目次の掲載対象を、一つの書籍構造定義から導出する

## 構成

`vivliostyle.config.mjs`の`publication`配列を、書籍構造の唯一の定義とする。各要素は、原稿のパス、種類、目次への掲載有無を持つ。この配列からVivliostyle CLIの`entry`を生成し、自動目次の変換処理も同じ情報を参照する。

外部の`catalog.js`や事前生成する目次Markdownは使用しない。

目次は、`toc.sectionDepth`へ`2`を指定して章と節を抽出する。`transformDocumentList`は目次へ掲載しない原稿を除外し、掲載する原稿へ前付、本文、付録、後付の分類を付与する。`transformSectionList`は、自動抽出された見出しを紙面用の構造へ包む。見出しの抽出、タイトル、リンク先の生成はVivliostyle CLIへ委譲する。

目次ページには`toc-template.html`を使用する。このテンプレートは空の`nav[role="doc-toc"]`だけを用意し、Vivliostyle CLIが目次項目を挿入する。これにより、前作と同じく目次ページへ書籍タイトルを重複して表示しない。

## 連番とページ番号

`chapter.css`と`appendix.css`を対象となる原稿だけに適用し、本文と付録のカウンターを分ける。

- 本文: `chapter`と`section`
- 付録: `appendix`と`section`

目次の番号は、リンク先のカウンターを`target-counter()`で参照して表示する。ページ番号も同様に、リンク先の`page`カウンターを参照する。番号やページ番号を目次HTMLへ直接埋め込まない。

ページ下部には確認用のノンブルを表示し、全10ページで1から10まで連続することを検証する。

## 依存関係

- Node.js 22.13.0以降の22系、または24系
- `@vivliostyle/cli` 11.1.0
- `@vivliostyle/vfm` 2.7.0
- `mupdf` 1.28.0

依存関係はこのディレクトリの`package-lock.json`に固定している。

## 実行方法

```shell
set -eu
npm ci
npm run verify
```

個別に確認する場合は、次のコマンドを使用する。

```shell
set -eu
npm run verify:webpub
npm run verify:pdf
```

生成物は`output/`に出力され、Gitの管理対象には含めない。

## 自動検証

`scripts/verify-webpub.mjs`は、次を確認する。

- 自動生成された`toc.html`が目次の意味を持つ`nav`を含む
- 章と節の12項目が期待する順序、タイトル、リンク先、分類で出力される
- 小節と目次対象外の原稿が掲載されない
- すべてのリンク先HTMLと見出しIDが存在する
- `publication.json`の`readingOrder`が書籍構造定義の順序と一致する
- 目次文書が`rel: contents`として識別される

`scripts/verify-pdf.mjs`は、次を確認する。

- PDFが10ページである
- 全ページのノンブルが1から10まで連続する
- 目次が3ページ目にあり、1ページに収まる
- 各項目の番号、タイトル、参照先ページ番号が期待どおりに表示される
- 12件のPDF内部リンクが、それぞれ期待する見出しID、ページ、見出し付近の座標へ解決される
- 小節と目次対象外の原稿が掲載されない
- 目次以外のPDF内部リンクが存在しない

## 目視確認

`output/table-of-contents.pdf`を開き、次を確認する。

- 目次が1ページに収まり、文字やページ番号に欠落、重なり、意図しない改行がない
- 章と節の階層がインデント、文字サイズ、太さによって区別できる
- タイトルとページ番号の間にリーダーが表示される
- 本文、付録、番号なし項目の番号形式が期待どおりである
- すべての項目をクリックして対応する見出しへ移動できる

## 未検証・未決定

- Thunder Clawの実際の原稿数と長いタイトルを含む複数ページの目次
- 製品用テーマにおける目次の紙面レイアウト
- 目次掲載対象を指定するclonoの設定または入力契約
- `vivliostyle.config.mjs`をclonoが生成するか、利用者の設定として維持するか
- 一つの原稿ファイルへ複数の章を記述する構成

## 参照資料

- [目次の作成 | Vivliostyle Documentation](https://docs.vivliostyle.org/ja/cli/toc-page/)
- [チュートリアル⑦目次の作成 | Vivliostyle](https://vivliostyle.org/ja/tutorials/create-table-of-contents/)
- [Vivliostyle CLI Config Reference](https://github.com/vivliostyle/vivliostyle-cli/blob/main/docs/config.md)
