# Vivliostyle見出し参照検証用fixture

## 目的

見出しのID、章・節・小節番号、同一Markdownファイル内および別Markdownファイル間の相互参照を、Vivliostyleで実現できるか検証する。

このfixtureはclonoによる変換後の原稿を模している。clono独自の参照記法は未決定のため、参照元には空の`a`要素を直接記述し、CSSの`target-counter()`と`target-text()`で表示内容を生成する。

## 検証対象

- 明示した見出しIDがVFMのHTMLへ保持されること
- 章・節・小節が、それぞれ`第1章`、`1.1`、`1.1.1`の形式で表示されること
- 章ごとに節番号と小節番号がリセットされること
- 番号のみの参照と、`番号 + 半角スペース + タイトル`の参照を生成できること
- インラインコードを含む見出しからプレーンテキストのタイトルを取得できること
- 同一Markdownファイル内と別Markdownファイル間の参照がPDF内リンクになること

検証範囲は`h1`から`h3`までとする。参照される見出しには明示的なIDを指定する。

## 構成

- `chapter-one.md`: 第1章。同一文書内参照と第2章への参照を含む
- `chapter-two.md`: 第2章。第1章への逆向きの参照を含む
- `style.css`: CSSカウンターとターゲット関数による番号・参照表示
- `vivliostyle.config.mjs`: 2つのMarkdownファイルを1冊のPDFとしてビルドする設定
- `scripts/verify-html.mjs`: VFM変換後のIDと参照要素を検証する
- `scripts/verify-pdf.mjs`: PDFの表示文字列と内部リンクを検証する

章カウンターは、`@page :first`で書籍全体の先頭ページに初期化し、`@page :nth(1)`で各Markdownファイルの先頭ページごとにインクリメントする。このため、Markdownごとのfrontmatterで章番号の開始値を指定する必要はない。

この方法は「1つのMarkdownファイルが1つの章に対応し、設定ファイルの`entry`が章順に並ぶ」構成を前提とする。章として数えない前付・後付などを別のMarkdownファイルとして`entry`へ含める場合に、章カウンターを増やさない指定が可能かは未検証である。

この方式は、Vivliostyle公式の[チュートリアル⑤カウンタと柱のスタイル](https://vivliostyle.org/ja/tutorials/configure-counters-running-heads/)をもとにしている。

`chapter-two.md`先頭の`chapter-start-spacer`は、2つ目の文書の先頭見出しがページ上端で欠けることを避け、目視確認を安定させるためのfixture専用要素である。製品用の原稿構文やテーマとして採用するものではない。

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

生成物は`output/`に出力され、Gitの管理対象には含めない。PDFを目視確認する場合は、`output/heading-references.pdf`を開き、次を確認する。

- 2つの章が別ページにあり、見出しや本文が欠けたり重なったりしていない
- 章・節・小節番号が期待どおり表示されている
- 青色の参照文字列をクリックすると、同一文書内・別文書間とも対象の見出しへ移動する

## 未検証・未決定

- clonoで使用する見出しID・参照記法
- 見出しIDの自動生成
- `h4`以降の番号形式
- 存在しないIDや重複IDに対する診断
- 章として数えない原稿ファイルを`entry`に含める場合のカウンター制御
- 製品用テーマでの紙面レイアウト
