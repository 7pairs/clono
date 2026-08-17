# Vivliostyleの表ID・キャプション・連番・相互参照に関する調査

- 状態: 調査済み
- 初回調査日: 2026-08-17
- 最終更新日: 2026-08-17
- 検証環境:
  - 実行環境: macOS、Node.js 24.19.0
  - HTML変換: `@vivliostyle/vfm` 2.7.0
  - PDF生成: `@vivliostyle/cli` 11.1.0
    - CLIが依存する`@vivliostyle/vfm` 2.7.0
    - CLIが使用するVivliostyle.js 2.44.1
  - PDF検証: `mupdf` 1.28.0

## 背景

Thunder Clawの前作では、表へ参照用ID、キャプション、章番号を含む連番を付与し、別の場所から番号または番号とタイトルを参照する処理を独自のビルドスクリプトで実装していた。

現在のVFMとVivliostyleへ委譲できる範囲と、clonoによる変換が必要な範囲を判断するため、Markdown表のID、キャプション、連番、同一Markdownファイル内および別Markdownファイル間の相互参照を調査した。

HTMLで記述する複雑な表、セル内改行、複数ページにまたがる表は、この調査の対象に含めない。

## Thunder Clawの要件

- Markdown表へ安定した参照用IDとキャプションを付けられる
- 番号を付ける表と付けない表を原稿上で区別できることが望ましい
- 表番号は章ごとにリセットし、`表1.1`、`表1.2`、`表2.1`の形式にする
- 図、表、コードリストはそれぞれ独立した連番を使用する
- キャプションは原則として表の下へ配置する。実現が著しく困難な場合は上への配置を許容する
- 番号とキャプションを参照する場合は、`表1.1 実行環境`のように番号、半角スペース、キャプションの順で表示する
- 同一Markdownファイル内だけでなく、別Markdownファイルの表も参照できる
- 製品となるPDFで参照をクリックし、対象の表へ移動できる
- 表内の強調とインラインコードを保持する
- 表内のリンクと文字寄せも、可能であれば保持する

## 公式情報の確認

VFMはCommonMarkとGitHub Flavored Markdownを基礎としており、Markdown表をHTMLの`table`、`thead`、`tbody`、`tr`、`th`、`td`へ変換する。列の文字寄せは、`th`と`td`の`align`属性へ反映される。

VFMは、そのまま記述したHTMLの内側に空行を置くことで、内側のMarkdownもHTMLへ変換できる。この仕組みを利用すると、Markdown表を意味のあるHTML要素で囲みながら、表内のMarkdown記法をVFMへ処理させられる。

Vivliostyle公式チュートリアルでは、CSSカウンターを`counter-reset`で初期化し、`counter-increment`で増加させ、`counter()`で表示する方法が紹介されている。また、リンク先のカウンターを取得する`target-counter()`と、リンク先のテキストを取得する`target-text()`を使用できる。

章番号には、見出しと画像の調査と同じく、`@page :first`による初期化と`@page :nth(1)`による原稿ファイルごとのインクリメントを利用できる。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/vivliostyle-table-references/)をリポジトリ内へ保存している。このfixtureは`clono`本体から独立したnpmプロジェクトであり、`package.json`と`package-lock.json`によって検証環境の依存関係を固定する。

fixtureでは、第1章と第2章を別々のMarkdownファイルに記述し、Vivliostyle CLIの`entry`へ章順に登録する。第1章には番号付き表二つと番号なし表一つを、第2章には番号付き表一つを配置する。

clonoの著者向け表記法は未決定であるため、番号付き表にはclonoによる変換後を模したHTMLの外枠とMarkdown表を原稿へ直接記述する。

```markdown
<figure class="numbered-table" id="table-runtime">

| 項目 | 値 |
| --- | --- |
| 言語 | `ClojureScript` |

<figcaption id="table-runtime-caption">実行環境</figcaption>
</figure>
```

番号なし表には、通常のMarkdown表記法を使用する。

参照元には、変換後の出力を模した空の`a`要素を直接記述する。番号とタイトルを参照する要素は、クリック先と番号の取得に使用する`href`と、キャプションの取得に使用する`data-caption-href`を別々に持つ。

```html
<a class="xref-table xref-title"
   href="chapter-two.html#table-platforms"
   data-caption-href="chapter-two.html#table-platforms-caption"></a>
```

HTMLの検証プログラムは、VFM変換後の番号付き表が、`figure`のIDとclass、Markdownから変換された`table`、IDを持つ`figcaption`を保持することを確認する。表内の強調、インラインコード、リンク、文字寄せと、通常のMarkdown表が番号付き表とは区別されることも確認する。

PDFの検証プログラムは、既存のPDFを削除してからVivliostyle CLIで再生成し、次を自動で確認する。

- 番号付き表の表番号とキャプションが期待する文字列で出力される
- 番号なし表が表示され、表カウンターを消費しない
- 表内の強調、インラインコード、リンクの内容が出力される
- 同一Markdownファイル内と別Markdownファイル間の参照文字列が出力される
- 四つの論理的な表参照がそれぞれ期待する表IDを参照する
- 各参照が、対象の表と同じページにある固有のキャプションより上の表先頭座標へ解決される
- すべてのPDF内部リンク注釈が、検証対象のいずれか一つの論理的な表参照に属する

生成したPDFは全ページを画像へ変換し、表、罫線、見出し、キャプション、本文の欠落や重なりがないことも目視確認した。

### Markdown表と表内の表現

VFMがMarkdown表を`table`へ変換し、表の外側に記述した`figure`と`figcaption`を維持することを確認した。

表内の強調は`strong`、インラインコードは`code`、リンクは`a`へ変換された。左寄せ、中央寄せ、右寄せは、対応する`th`と`td`の`align`属性へ反映され、生成したPDFでも期待する位置へ表示された。

### 番号付き表と番号なし表

番号付き表だけに`numbered-table`クラスを付け、次のCSSで表カウンターをインクリメントした。

```css
body {
  counter-reset: table;
}

.numbered-table {
  counter-increment: table;
}
```

通常のMarkdown表を二つの番号付き表の間へ配置した結果、番号付き表は`表1.1`、`表1.2`となり、番号なし表は表カウンターを消費しなかった。第2章の最初の番号付き表は`表2.1`となり、章ごとのリセットも確認できた。

表カウンターには`table`という固有の名前を使用する。図には`figure`という別のカウンターを使用しているため、設計上は独立している。ただし、図、表、コードリストの三種類を同じ文書へ配置した結合テストはまだ実施していない。

### IDとキャプション

番号付き表の参照用IDと`numbered-table`クラスを`figure`へ付け、その内側へMarkdown表と`figcaption`を配置する構造を使用した。キャプションはMarkdown表の後に記述することで、表の下へ表示できた。

今回確認したVFMの標準的なMarkdown表記法だけでは、表全体の参照用IDとキャプションを同時に表現できない。番号付き表の出力契約としては、IDとclassを持つ`figure`、Markdownから変換される`table`、キャプションを持つ`figcaption`の組み合わせが明確である。

### 番号とキャプションの参照

`target-counter()`を使用し、参照先の章番号と表番号を取得できた。

一方、表全体の`figure`を参照先として`target-text()`の`content`を取得すると、キャプションだけでなく表のすべてのセル内容も参照文字列へ含まれた。画像の`figure`では画像自体にテキストノードがないため、同じ方法でキャプションだけを取得できたが、表には適用できない。

この問題を避けるため、表全体の`figure`と`figcaption`へ別々のIDを付けた。参照要素の`href`は、クリック先と`target-counter()`による番号取得のために表全体を指す。`data-caption-href`は、`target-text()`によるタイトル取得のためにキャプションを指す。

```css
a.xref-table::before {
  content: "表" target-counter(attr(href url), chapter) "." target-counter(attr(href url), table);
}

a.xref-title::after {
  content: " " target-text(attr(data-caption-href url), content);
}
```

この構造により、リンクは表の先頭へ移動し、参照文字列には`表1.1 実行環境`のように番号とキャプションだけを表示できた。

### 別Markdownファイル間の参照

変換後のHTMLファイル名と表IDを組み合わせた`chapter-two.html#table-platforms`のような`href`と、キャプションIDを組み合わせた`data-caption-href`を使用し、別Markdownファイルの番号とキャプションを参照できた。

生成したPDFでは参照が内部リンクになり、同一ファイル内、別ファイル間、前方参照、後方参照のすべてで対象の表の先頭へ移動できた。

## 責務判断

表の連番、キャプション表示、番号参照、番号とキャプションの参照は、VFM、VivliostyleおよびテーマCSSを利用して要件を満たせる。clonoが表番号や参照文字列を計算し、出力へ直接埋め込む処理は実装しない方針とする。

一方、VFMの標準的なMarkdown表記法だけでは、番号付き表に必要な表全体のIDとclass、キャプションを同時に表現できない。このため、番号付き表の著者向け記法と、意味を保持した`figure`、`table`、`figcaption`構造への変換はclonoが担う候補とする。

### Vivliostyleへ委譲する責務

- Markdown表と表内の強調、インラインコード、リンク、文字寄せをHTMLへ変換する
- CSSカウンターを使用して章ごとの表番号を生成する
- 表番号を表の下にあるキャプションへ表示する
- `target-counter()`と`target-text()`を使用して参照文字列を生成する
- 参照をPDF内部リンクとして保持する

### clonoが担う候補となる責務

- 番号付き表と番号なし表を区別できる著者向け記法を提供する
- 番号付き表を、IDと用途別のclassを持つ`figure`、Markdownから変換される`table`、タイトル参照用IDを持つ`figcaption`へ変換する
- 著者向けの参照記法を、表全体を指す`href`、キャプションを指す`data-caption-href`、用途別のclassを持つ空の`a`要素へ変換する
- 別Markdownファイルを参照する場合に、変換後の出力パスを反映した`href`と`data-caption-href`を生成する
- 未定義の参照ID、重複したID、不正な参照種別を診断する
- 番号なし表の通常のMarkdown記法と表内のMarkdownを壊さず、VFMへ渡す

## 成立条件と未確認事項

章番号と表番号の組み合わせは、「一つのMarkdownファイルが一つの章に対応し、Vivliostyle設定の`entry`が章順に並ぶ」構成を前提とする。

次の事項は未確認または未決定である。

- clonoで使用する番号付き表と参照の著者向け記法
- キャプションを省略した場合の補完規則
- キャプション内のMarkdown
- HTMLで記述する複雑な表とセル結合
- セル内改行
- 複数ページにまたがる表、表の分割、ヘッダー行の繰り返し
- 図、表、コードリストを同じ文書へ配置した場合の独立した連番
- 存在しないIDや重複IDに対するVFMおよびVivliostyleの挙動
- 章として数えない前付・後付などを別のMarkdownファイルとして`entry`へ含める場合のカウンター制御
- 製品用テーマでの表の幅と紙面レイアウト

複雑な表と複数ページにまたがる表は、Thunder Clawの次回作で必要となる可能性が低いため、具体的な必要性が生じるまで調査しない。

## 再現方法

検証に使用する入力、HTMLとPDFの自動検証、PDFの目視確認手順は、[検証用fixtureのREADME](fixtures/vivliostyle-table-references/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm run verify`で再検証できる。

## 再調査する条件

- VFMがMarkdown表から生成するHTML構造、表内のMarkdown処理、またはHTML内のMarkdown処理が変更された場合
- Vivliostyle.jsのCSSカウンター、`target-counter()`または`target-text()`の実装が変更された場合
- 書籍の原稿ファイル構成が、一章一ファイルの前提を満たさなくなった場合
- 製品用テーマで表、番号、キャプションまたは参照の表示が期待どおりにならなかった場合
- Thunder Clawで複雑な表または複数ページにまたがる表が必要になった場合

## 参照資料

- [Vivliostyle Flavored Markdown](https://docs.vivliostyle.org/ja/vfm/vfm/)
- [GitHub Flavored Markdown Spec: Tables extension](https://github.github.com/gfm/#tables-extension-)
- [チュートリアル⑤カウンタと柱のスタイル | Vivliostyle](https://vivliostyle.org/ja/tutorials/configure-counters-running-heads/)
- [Supported CSS Features | Vivliostyle Documentation](https://docs.vivliostyle.org/ja/reference/supported-css-features/)
