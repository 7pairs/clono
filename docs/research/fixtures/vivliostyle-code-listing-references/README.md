# Vivliostyleコードリスト参照検証用fixture

## 目的

コードリストのID、任意のキャプション、章ごとの連番、同一Markdownファイル内および別Markdownファイル間の相互参照を、VFMとVivliostyleで実現できるか検証する。

番号付きコードリストと番号なしコードブロックを区別し、Kotlinの構文強調と、複数ページにまたがる長いコードリストも確認する。

## 検証対象

- 番号付きコードリストがIDと任意のキャプションを保持できること
- 番号付きコードリストだけがリストカウンターをインクリメントすること
- リスト番号が章ごとにリセットされ、`リスト1.1`、`リスト1.2`、`リスト2.1`の形式で表示されること
- キャプションがコードの上へ配置されること
- Kotlinコードの構文強調用HTMLが維持されること
- 番号のみの参照と、`番号 + 半角スペース + キャプション`の参照を生成できること
- 同一Markdownファイル内と別Markdownファイル間の参照がPDF内部リンクになること
- 長いコードリストが複数ページへ分割され、キャプションが最初のページだけに表示されること
- 長いコードリストの各検証行に欠落、重複、順序の崩れがないこと

行番号、特定行の強調、差分表示は検証対象外とする。

## 入力と出力の考え方

VFMは、言語を指定したコードフェンスを`pre`と`code`へ変換し、Prismによる構文強調用のHTMLを生成する。コードフェンスで`kotlin:Main.kt`のように指定すると、`figure`と`figcaption`も生成する。

一方、Thunder Clawではファイル名に限らない任意のキャプションと参照用IDが必要になる。このfixtureでは、番号付きコードリストについて、clonoによる変換後を次の構造で模している。

````markdown
<figure class="numbered-listing" id="listing-greeting">
<figcaption id="listing-greeting-caption">挨拶を表示する関数</figcaption>

```kotlin
fun greet(name: String): String {
    return "Hello, $name!"
}
```
</figure>
````

VFMは`figure`内のコードフェンスを構文強調された`pre`と`code`へ変換し、外側のID、class、キャプションを維持する。表の調査と同じく、番号とクリック先に使用する`figure`のIDと、タイトル取得に使用する`figcaption`のIDを分ける。

clonoの著者向け記法は未決定であり、このHTMLの外枠を原稿へ直接記述することを仕様とはしない。

番号なしコードブロックは通常のコードフェンスとして記述する。`numbered-listing`クラスを持たないため、リストカウンターをインクリメントしない。

## CSSによる連番と参照

章カウンターは、`@page :first`で書籍全体の先頭ページに初期化し、`@page :nth(1)`で各Markdownファイルの先頭ページごとにインクリメントする。

リストカウンターは各文書の`body`でリセットし、番号付きコードリストだけでインクリメントする。

```css
body {
  counter-reset: listing;
}

.numbered-listing {
  counter-increment: listing;
}
```

参照文字列は、`target-counter()`で章番号とリスト番号を取得する。コードリスト全体の`figure`を`target-text()`で参照するとコード内容まで取得するため、`data-caption-href`でキャプションを別に参照する。

```css
a.xref-listing::before {
  content: "リスト" target-counter(attr(href url), chapter) "." target-counter(attr(href url), listing);
}

a.xref-title::after {
  content: " " target-text(attr(data-caption-href url), content);
}
```

## 長いコードリスト

長いコードリストをページ間で分割できるように、`numbered-listing`と`pre`へ`break-inside: auto`を指定する。`figcaption`へ`break-after: avoid`を指定し、キャプションだけがページ末尾に残ることを防ぐ。

検証用の長いKotlinコードには、`LONG_LINE_001`から`LONG_LINE_060`までの一意な文字列を一度ずつ含める。PDF検証では、先頭と末尾が異なるページにあること、すべての文字列が一度ずつ順番どおりに存在すること、キャプションが先頭行と同じページに一度だけ表示され、末尾行のページには繰り返されないことを確認する。

## 構成

- `chapter-one.md`: 番号付きコードリスト二つ、番号なしコードブロック一つ、同一文書内参照、第2章への参照を含む
- `chapter-two.md`: 複数ページにまたがる番号付きコードリスト一つ、第1章への逆向きの参照を含む
- `style.css`: 章・リストカウンター、コード、構文強調、キャプション、参照表示のCSS
- `vivliostyle.config.mjs`: 二つのMarkdownファイルを一冊のPDFとしてビルドする設定
- `scripts/verify-html.mjs`: VFM変換後のコード構造、ID、キャプション、構文強調、参照要素を検証する
- `scripts/verify-pdf.mjs`: PDFのリスト番号、キャプション、長いコードの分割、参照文字列、内部リンクを検証する

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

生成物は`output/`に出力され、Gitの管理対象には含めない。PDFを目視確認する場合は、`output/code-listing-references.pdf`を開き、次を確認する。

- 各章が新しいページから始まり、コード、見出し、キャプション、本文が欠けたり重なったりしていない
- 番号付きコードの上に`リスト1.1 挨拶を表示する関数`、`リスト1.2 入力を検証する処理`、`リスト2.1 端末情報を収集する処理`が表示される
- 番号なしコードブロックにリスト番号が付かず、後続する番号付きコードリストの連番にも影響しない
- Kotlinコードのキーワード、関数名、文字列が色分けされる
- 長いコードリストがページをまたいで続き、継続ページにキャプションが繰り返されない
- 青色の参照文字列をクリックすると、同一文書内・別文書間とも対象のコードリストへ移動する

## 未検証・未決定

- clonoで使用する番号付きコードリストと参照の著者向け記法
- キャプションを省略した場合の補完規則
- キャプション内のMarkdown
- 言語指定を省略した番号付きコードリスト
- 行番号
- 特定行の強調と差分表示
- 製品用テーマでの長い行の折り返しと禁則処理
- キャプションがページ末尾の近くに配置される場合の改ページ
- 図、表、コードリストを同じ文書とテーマへ配置した場合のカウンターと参照規則
- 存在しないIDや重複IDに対する診断
- 章として数えない原稿ファイルを`entry`に含める場合のカウンター制御

## 参照資料

- [コード | Vivliostyle Flavored Markdown](https://docs.vivliostyle.org/ja/vfm/vfm/#コードcode)
- [チュートリアル⑤カウンタと柱のスタイル | Vivliostyle](https://vivliostyle.org/ja/tutorials/configure-counters-running-heads/)
- [Supported CSS Features | Vivliostyle Documentation](https://docs.vivliostyle.org/ja/reference/supported-css-features/)
