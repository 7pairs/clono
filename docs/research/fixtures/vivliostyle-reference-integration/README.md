# Vivliostyle相互参照結合検証用fixture

## 目的

個別に調査した見出し、画像、表、コードリストの連番と相互参照を、同じ文書とテーマCSSで同時に利用できるか検証する。基本的な脚注がこれらの要素と共存することも確認する。

## 検証対象

- 一つの`counter-reset`で図、表、コードリストのカウンターを同時に初期化できること
- 図、表、コードリストがそれぞれ独立して`1.1`、`1.2`と進み、第2章で`2.1`へリセットされること
- 番号なし画像、表、コードブロックが対応するカウンターを消費しないこと
- 見出し、画像、表、コードリストの番号参照と番号・タイトル参照を同時に利用できること
- すべてのタイトル取得先を`data-title-href`へ統一できること
- 同一Markdownファイル内、別Markdownファイル間、前方、後方の参照がPDF内部リンクになること
- VFMの`dpub`脚注がほかの要素と共存し、各章で番号を1から開始すること

個別fixtureで確認済みの長いコードリスト、複雑な表、製品用テーマのレイアウトは、このfixtureでは再検証しない。

## 統合した出力契約

### カウンター

個別fixtureの`body`は一種類のカウンターだけをリセットしていた。同じプロパティを複数の規則へ分けると後の宣言が前の宣言を上書きするため、統合テーマでは一つの宣言へまとめる。

```css
body {
  counter-reset: figure table listing;
}
```

番号付き要素だけが、それぞれ対応するカウンターをインクリメントする。

### タイトル参照

個別fixtureでは、見出しと画像が`href`を、表とコードリストが`data-caption-href`をタイトル取得に使用していた。この違いを統合し、すべての番号・タイトル参照へ`data-title-href`を出力する。

```html
<a
  class="xref-listing xref-title"
  href="#listing-greeting"
  data-title-href="#listing-greeting-caption"
></a>
```

`href`は番号取得とPDF内部リンクの移動先、`data-title-href`はタイトル取得先とする。見出しでは両方が同じIDを指し、画像、表、コードリストでは`data-title-href`がキャプションのIDを指す。

```css
a.xref-title::after {
  content: " " target-text(attr(data-title-href url), content);
}
```

## 構成

- `chapter-one.md`: 各種類の番号付き要素二つ、番号なし要素一つ、番号参照、番号・タイトル参照、第2章への参照、脚注を含む
- `chapter-two.md`: 各種類の番号付き要素一つ、第1章への逆向きの参照、脚注を含む
- `images/diagram.svg`: 番号付き画像と番号なし画像に使用する検証用画像
- `style.css`: 統合したカウンター、要素、相互参照、構文強調のCSS
- `vivliostyle.config.mjs`: 二つのMarkdownファイルと`dpub`脚注を設定する
- `scripts/verify-html.mjs`: VFM変換後の各要素、ID、参照属性、構文強調、脚注を検証する
- `scripts/verify-pdf.mjs`: 各カウンター、参照文字列、リンク先ID・ページ・座標、脚注リンクを検証する

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

生成物は`output/`に出力され、Gitの管理対象には含めない。PDFを目視確認する場合は、`output/reference-integration.pdf`を開き、次を確認する。

- 見出し、画像、表、コードリスト、参照文字列、脚注に欠落や重なりがない
- 図、表、コードリストの番号が独立し、番号なし要素が連番へ影響しない
- 番号・タイトル参照に対象種別の正しいタイトルだけが表示される
- 同一文書内、別文書間、前方、後方の参照をクリックして対象へ移動できる
- 脚注本文が参照と同じページの下部へ表示される

## 未検証・未決定

- clonoで使用する各要素と参照の著者向け記法
- `data-title-href`を含む最終的なHTML出力契約
- 未定義ID、重複ID、参照種別の不一致に対する診断
- コラム内から参照する脚注との結合
- 前付・後付を含む実際の書籍構成でのカウンター制御
- Thunder Clawの製品用テーマとの結合

## fixture固有の調整

見出しの個別fixtureと同じく、第2章の先頭に`chapter-start-spacer`を配置する。これは、二つ目の原稿ファイルの先頭見出しがページ上端で一部欠ける現象を避け、目視確認を安定させるためのfixture固有の処置である。製品用の原稿構文やテーマとして採用するものではない。

## 参照資料

- [見出しの調査記録](../../vivliostyle-heading-references.md)
- [画像の調査記録](../../vivliostyle-figure-references.md)
- [表の調査記録](../../vivliostyle-table-references.md)
- [コードリストの調査記録](../../vivliostyle-code-listing-references.md)
- [脚注の調査記録](../../vivliostyle-footnotes.md)
