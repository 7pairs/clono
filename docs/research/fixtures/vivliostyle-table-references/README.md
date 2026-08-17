# Vivliostyle表参照検証用fixture

## 目的

Markdown表のID、キャプション、章ごとの連番、同一Markdownファイル内および別Markdownファイル間の相互参照を、VFMとVivliostyleで実現できるか検証する。

番号付き表と番号なし表を区別し、表内のMarkdown表現が維持されることも確認する。

## 検証対象

- 番号付き表がIDとキャプションを別々に保持できること
- 番号付き表だけが表カウンターをインクリメントすること
- 表番号が章ごとにリセットされ、`表1.1`、`表1.2`、`表2.1`の形式で表示されること
- キャプションが表の下へ配置されること
- 表内の強調、インラインコード、リンク、文字寄せが維持されること
- 番号のみの参照と、`番号 + 半角スペース + キャプション`の参照を生成できること
- 同一Markdownファイル内と別Markdownファイル間の参照がPDF内部リンクになること

HTMLで記述する複雑な表、セル内改行、複数ページにまたがる表は検証対象外とする。

## 入力と出力の考え方

VFMはMarkdown表を`table`へ変換し、強調、インラインコード、リンク、列の文字寄せもHTMLへ反映する。ただし、Markdown表だけでは参照用IDとキャプションを指定できない。

このfixtureでは、番号付き表について、clonoによる変換後を次の構造で模している。

```markdown
<figure class="numbered-table" id="table-runtime">

| 項目 | 値 |
| --- | --- |
| 言語 | `ClojureScript` |

<figcaption id="table-runtime-caption">実行環境</figcaption>
</figure>
```

VFMは`figure`内のMarkdown表を`table`へ変換し、IDとキャプションを維持する。表全体のIDと表カウンターを`figure`へ持たせ、キャプションにはタイトル参照用の別のIDを持たせる。

表全体を参照先として`target-text()`を使用すると、キャプションだけでなく表のセル内容も参照文字列へ含まれる。このため、番号とクリック先には`href`で表全体を参照し、タイトルには`data-caption-href`でキャプションを参照する。

clonoの著者向け記法は未決定であり、このHTMLを原稿へ直接記述することを仕様とはしない。

番号なし表は通常のMarkdown表として記述する。`numbered-table`クラスを持たないため、表カウンターをインクリメントしない。

## CSSによる連番と参照

章カウンターは、`@page :first`で書籍全体の先頭ページに初期化し、`@page :nth(1)`で各Markdownファイルの先頭ページごとにインクリメントする。

表カウンターは各文書の`body`でリセットし、番号付き表だけでインクリメントする。

```css
body {
  counter-reset: table;
}

.numbered-table {
  counter-increment: table;
}
```

参照文字列は、`target-counter()`で章番号と表番号を、`target-text()`で参照先のキャプションを取得して生成する。

```css
a.xref-table::before {
  content: "表" target-counter(attr(href url), chapter) "." target-counter(attr(href url), table);
}

a.xref-title::after {
  content: " " target-text(attr(data-caption-href url), content);
}
```

## 構成

- `chapter-one.md`: 番号付き表二つ、番号なし表一つ、表内Markdown、同一文書内参照、第2章への参照を含む
- `chapter-two.md`: 番号付き表一つ、第1章への逆向きの参照を含む
- `style.css`: 章・表カウンター、表、キャプション、参照表示のCSS
- `vivliostyle.config.mjs`: 二つのMarkdownファイルを一冊のPDFとしてビルドする設定
- `scripts/verify-html.mjs`: VFM変換後の表構造、ID、キャプション、表内Markdown、参照要素を検証する
- `scripts/verify-pdf.mjs`: PDFの表番号、キャプション、参照文字列、内部リンクを検証する

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

生成物は`output/`に出力され、Gitの管理対象には含めない。PDFを目視確認する場合は、`output/table-references.pdf`を開き、次を確認する。

- 二つの章が別ページにあり、表、見出し、キャプション、本文が欠けたり重なったりしていない
- 番号付き表の下に`表1.1 実行環境`、`表1.2 対応機能`、`表2.1 対応環境`が表示される
- 番号なし表に表番号が付かず、後続する番号付き表の連番にも影響しない
- 表内の強調、インラインコード、リンク、文字寄せが維持される
- 青色の参照文字列をクリックすると、同一文書内・別文書間とも対象の表へ移動する

## 未検証・未決定

- clonoで使用する番号付き表と参照の著者向け記法
- キャプションを省略した場合の補完規則
- キャプション内のMarkdown
- HTMLで記述する複雑な表とセル結合
- セル内改行
- 複数ページにまたがる表とヘッダー行の繰り返し
- 存在しないIDや重複IDに対する診断
- 章として数えない原稿ファイルを`entry`に含める場合のカウンター制御
- 製品用テーマでの表の幅と紙面レイアウト

## 参照資料

- [表 | Vivliostyle Flavored Markdown](https://docs.vivliostyle.org/ja/vfm/vfm/#表table)
- [チュートリアル⑤カウンタと柱のスタイル | Vivliostyle](https://vivliostyle.org/ja/tutorials/configure-counters-running-heads/)
- [チュートリアル⑥基本要素のスタイル | Vivliostyle](https://vivliostyle.org/ja/tutorials/configure-basic-elements/)
- [Supported CSS Features | Vivliostyle Documentation](https://docs.vivliostyle.org/ja/reference/supported-css-features/)
