# Vivliostyle画像参照検証用fixture

## 目的

画像のID、キャプション、章ごとの連番、同一Markdownファイル内および別Markdownファイル間の相互参照を、VFMとVivliostyleで実現できるか検証する。

番号付き画像と番号なし画像を区別し、番号なし画像が図カウンターを消費しないことも確認する。

## 検証対象

- 番号付き画像が、ID、代替テキスト、キャプションを別々に保持できること
- 番号付き画像だけが図カウンターをインクリメントすること
- 図番号が章ごとにリセットされ、`図1.1`、`図1.2`、`図2.1`の形式で表示されること
- キャプションが画像の下へ配置されること
- 番号のみの参照と、`番号 + 半角スペース + キャプション`の参照を生成できること
- 同一Markdownファイル内と別Markdownファイル間の参照がPDF内部リンクになること

一つの図には一つの画像を含める。複数画像をまとめた図は検証対象外とする。

## 入力と出力の考え方

VFMは、代替テキストを持つ単独行のMarkdown画像を`figure`、`img`、`figcaption`へ変換する。ただし、Markdownで画像に指定した属性は`figure`ではなく`img`へ出力され、代替テキストとキャプションには同じ文字列が使用される。

このfixtureでは、番号付き画像について、clonoによる変換後を次のHTMLで模している。

```html
<figure class="numbered-figure" id="figure-architecture">
  <img
    src="./images/architecture.svg"
    alt="入力、変換、出力を箱と矢印で表した図"
  >
  <figcaption>全体構成</figcaption>
</figure>
```

IDと図カウンターを`figure`へ持たせることで、一つの参照先から番号とキャプションを取得する。代替テキストと読者へ表示するキャプションも別々に保持できる。

clonoの著者向け記法は未決定であり、このHTMLを原稿へ直接記述することを仕様とはしない。

番号なし画像は通常のMarkdown画像記法で記述する。

```markdown
![番号なしの画像](./images/unnumbered.svg)
```

VFMはこの画像も`figure`へ変換するが、`numbered-figure`クラスを持たないため、図カウンターをインクリメントしない。表示されるキャプションにも図番号を付けない。

## CSSによる連番と参照

章カウンターは、`@page :first`で書籍全体の先頭ページに初期化し、`@page :nth(1)`で各Markdownファイルの先頭ページごとにインクリメントする。

図カウンターは各文書の`body`でリセットし、番号付きの`figure`だけでインクリメントする。

```css
body {
  counter-reset: figure;
}

.numbered-figure {
  counter-increment: figure;
}
```

参照文字列は、`target-counter()`で章番号と図番号を、`target-text()`で参照先のキャプションを取得して生成する。

```css
a.xref-figure::before {
  content: "図" target-counter(attr(href url), chapter) "." target-counter(attr(href url), figure);
}

a.xref-title::after {
  content: " " target-text(attr(href url), content);
}
```

## 構成

- `chapter-one.md`: 番号付き画像二つ、番号なし画像一つ、同一文書内参照、第2章への参照を含む
- `chapter-two.md`: 番号付き画像一つ、第1章への逆向きの参照を含む
- `images/`: 表示確認に使用するSVG画像
- `style.css`: 章・図カウンター、キャプション、参照表示のCSS
- `vivliostyle.config.mjs`: 二つのMarkdownファイルを一冊のPDFとしてビルドする設定
- `scripts/verify-html.mjs`: VFM変換後の画像構造、ID、代替テキスト、キャプション、参照要素を検証する
- `scripts/verify-pdf.mjs`: PDFの図番号、キャプション、参照文字列、内部リンクを検証する

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
npm run verify:html
npm run verify:pdf
```

生成物は`output/`に出力され、Gitの管理対象には含めない。PDFを目視確認する場合は、`output/figure-references.pdf`を開き、次を確認する。

- 二つの章が別ページにあり、画像、見出し、キャプション、本文が欠けたり重なったりしていない
- 番号付き画像の下に`図1.1 全体構成`、`図1.2 処理フロー`、`図2.1 配置構成`が表示される
- 番号なし画像のキャプションに図番号が付かない
- 青色の参照文字列をクリックすると、同一文書内・別文書間とも対象の図へ移動する

## 未検証・未決定

- clonoで使用する番号付き画像と参照の著者向け記法
- 代替テキストまたはキャプションを省略した場合の補完規則
- キャプション内のMarkdown
- 一つの図へ複数の画像を含める構成
- 存在しないIDや重複IDに対する診断
- 章として数えない原稿ファイルを`entry`に含める場合のカウンター制御
- 製品用テーマでの画像サイズと紙面レイアウト

## 参照資料

- [画像 | Vivliostyle Flavored Markdown](https://docs.vivliostyle.org/ja/vfm/vfm/#画像image)
- [チュートリアル⑤カウンタと柱のスタイル | Vivliostyle](https://vivliostyle.org/ja/tutorials/configure-counters-running-heads/)
- [チュートリアル⑥基本要素のスタイル | Vivliostyle](https://vivliostyle.org/ja/tutorials/configure-basic-elements/)
- [Supported CSS Features | Vivliostyle Documentation](https://docs.vivliostyle.org/ja/reference/supported-css-features/)
