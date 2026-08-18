# Vivliostyleの画像ID・キャプション・連番・相互参照に関する調査

- 状態: 調査済み
- 初回調査日: 2026-08-16
- 最終更新日: 2026-08-18
- 検証環境:
  - 実行環境: macOS、Node.js 24.19.0
  - HTML変換: `@vivliostyle/vfm` 2.7.0
  - PDF生成: `@vivliostyle/cli` 11.1.0
    - CLIが依存する`@vivliostyle/vfm` 2.7.0
    - CLIが使用するVivliostyle.js 2.44.1
  - PDF検証: `mupdf` 1.28.0

## 背景

Thunder Clawの前作では、番号とキャプションを付ける画像を独自記法で、番号を付けない画像をMarkdownの画像記法で書き分けていた。独自記法からは、参照用IDを持つHTMLと図番号を前身のビルドスクリプトで生成していた。

現在のVFMとVivliostyleへ委譲できる範囲と、clonoによる変換が必要な範囲を判断するため、画像のID、キャプション、連番、同一Markdownファイル内および別Markdownファイル間の相互参照を調査した。

表とコードリストについては、この調査の対象に含めない。

## Thunder Clawの要件

- 番号を付ける画像と付けない画像を原稿上で区別できる
- 番号付き画像へ安定した参照用IDとキャプションを付けられる
- 代替テキストとキャプションを別々に保持できることが望ましい
- 図番号は章ごとにリセットし、`図1.1`、`図1.2`、`図2.1`の形式にする
- 図、表、コードリストはそれぞれ独立した連番を使用する
- キャプションは画像の下へ配置する
- 番号とキャプションを参照する場合は、`図1.1 全体構成`のように番号、半角スペース、キャプションの順で表示する
- 同一Markdownファイル内だけでなく、別Markdownファイルの画像も参照できる
- 製品となるPDFで参照をクリックし、対象の画像へ移動できる
- 一つの図に含める画像は一つとする。複数画像をまとめた図は当面必要としない

## 公式情報の確認

VFMは、代替テキストを持つ単独行のMarkdown画像を`figure`で囲み、同じ文字列を`img`の`alt`と`figcaption`へ出力する。

```markdown
![Figure 1](./fig1.png)
```

```html
<figure>
  <img src="./fig1.png" alt="Figure 1">
  <figcaption aria-hidden="true">Figure 1</figcaption>
</figure>
```

Markdown画像へ属性を指定した場合、その属性は`figure`ではなく`img`へ出力される。

Vivliostyle公式チュートリアルでは、`figure`または`figcaption`で図カウンターをインクリメントし、`figcaption::before`へ図番号を表示する方法が紹介されている。また、`target-counter()`を使用するとリンク先の図カウンターを、`target-text()`を使用するとリンク先のテキストを取得できる。

章番号には、見出しの調査と同じく、`@page :first`による初期化と`@page :nth(1)`による原稿ファイルごとのインクリメントを利用できる。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/vivliostyle-figure-references/)をリポジトリ内へ保存している。このfixtureは`clono`本体から独立したnpmプロジェクトであり、`package.json`と`package-lock.json`によって検証環境の依存関係を固定する。

fixtureでは、第1章と第2章を別々のMarkdownファイルに記述し、Vivliostyle CLIの`entry`へ章順に登録する。第1章には番号付き画像二つと番号なし画像一つを、第2章には番号付き画像一つを配置する。

clonoの著者向け画像記法は未決定であるため、番号付き画像にはclonoによる変換後を模したHTMLを原稿へ直接記述する。

```html
<figure class="numbered-figure" id="figure-architecture">
  <img
    src="./images/architecture.svg"
    alt="入力、変換、出力を箱と矢印で表した図"
  >
  <figcaption>全体構成</figcaption>
</figure>
```

番号なし画像には、通常のMarkdown画像記法を使用する。

```markdown
![番号なしの画像](./images/unnumbered.svg)
```

参照元には、変換後の出力を模した空の`a`要素を直接記述する。

```html
<a class="xref-figure xref-title"
   href="chapter-two.html#figure-layout"></a>
```

HTMLの検証プログラムは、VFM変換後の番号付き画像が、`figure`のIDとclass、`img`の画像パスと代替テキスト、`figcaption`のキャプションを保持することを確認する。通常のMarkdown画像が`numbered-figure`クラスを持たないことと、参照用の`a`要素が保持されることも確認する。

PDFの検証プログラムは、既存のPDFを削除してからVivliostyle CLIで再生成し、次を自動で確認する。

- 番号付き画像の図番号とキャプションが期待する文字列で出力される
- 番号なし画像が表示され、図カウンターを消費しない
- 同一Markdownファイル内と別Markdownファイル間の参照文字列が出力される
- すべての参照が解決可能なPDF内部リンクになる
- 第1章から第2章、第2章から第1章の両方向へリンクできる

生成したPDFは全ページを画像へ変換し、画像、見出し、キャプション、本文の欠落や重なりがないことも目視確認した。

### 番号付き画像と番号なし画像

番号付き画像だけに`numbered-figure`クラスを付け、次のCSSで図カウンターをインクリメントした。

```css
body {
  counter-reset: figure;
}

.numbered-figure {
  counter-increment: figure;
}
```

通常のMarkdown画像を二つの番号付き画像の間へ配置した結果、番号付き画像は`図1.1`、`図1.2`となり、番号なし画像は図カウンターを消費しなかった。第2章の最初の番号付き画像は`図2.1`となり、章ごとのリセットも確認できた。

`figure`という要素名だけでは連番対象にせず、明示的なclassで区別することで、VFMが通常のMarkdown画像から生成した`figure`を番号なしのまま扱える。

図カウンターには`figure`という固有の名前を使用する。[結合検証](vivliostyle-reference-integration.md)では、表の`table`、コードリストの`listing`と同じ文書へ配置しても、三種類のカウンターが独立して動作することを確認した。

### ID、代替テキスト、キャプション

番号付き画像の参照用IDを`figure`へ付け、代替テキストを`img`、キャプションを`figcaption`へ付ける構造を使用した。これにより、参照先となる図全体を一つのIDで識別しながら、代替テキストと読者へ表示するキャプションを別々に保持できた。

VFM標準のMarkdown画像では属性が`img`へ付き、代替テキストがキャプションにも使用される。この構造だけでも画像とキャプションは表示できるが、今回の要件を満たす番号付き画像の出力契約としては、IDとclassを持つ`figure`へ変換する方法が明確である。

代替テキストとキャプションの両方を著者向け記法で必須にするか、一方を省略した場合に兼用するかは決定していない。

### キャプションと連番

`figcaption::before`へCSSで図番号を追加し、画像の下に`図1.1 全体構成`の形式で表示できた。図番号は変換時に文字列として埋め込まず、Vivliostyleが組版時に生成する。

キャプション内のMarkdownは検証していない。

### 番号とキャプションの参照

`target-counter()`を使用し、参照先の章番号と図番号を取得できた。`target-text()`を組み合わせることで、番号、半角スペース、キャプションの形式でも参照できた。

参照先を`figure`とすることで、同じIDから図カウンターと子要素である`figcaption`のテキストを取得できた。

### 別Markdownファイル間の参照

変換後のHTMLファイル名と図IDを組み合わせた`chapter-two.html#figure-layout`のような`href`を使用し、別Markdownファイルの番号とキャプションを参照できた。

生成したPDFでは参照が内部リンクになり、同一ファイル内、別ファイル間、前方参照、後方参照のすべてで対象の図が存在するページへ移動できた。

## 責務判断

画像の連番、キャプション表示、番号参照、番号とキャプションの参照は、VivliostyleとテーマCSSを利用して要件を満たせる。clonoが図番号や参照文字列を計算し、出力へ直接埋め込む処理は実装しない方針とする。

一方、VFM標準のMarkdown画像だけでは、番号付き画像に必要な`figure`のIDとclass、代替テキストと異なるキャプションを同時に表現する出力契約にならない。このため、番号付き画像の著者向け記法と、意味を保持した`figure`構造への変換はclonoが担う候補とする。

### Vivliostyleへ委譲する責務

- CSSカウンターを使用して章ごとの図番号を生成する
- 図番号を画像の下にあるキャプションへ表示する
- `target-counter()`と`target-text()`を使用して参照文字列を生成する
- 参照をPDF内部リンクとして保持する

### clonoが担う候補となる責務

- 番号付き画像と番号なし画像を区別できる著者向け記法を提供する
- 番号付き画像を、IDと用途別のclassを持つ`figure`、代替テキストを持つ`img`、タイトル参照用IDを持つ`figcaption`へ変換する
- 著者向けの参照記法を、図全体を指す`href`、キャプションを指す`data-title-href`、用途別のclassを持つ空の`a`要素へ変換する
- 別Markdownファイルを参照する場合に、変換後の出力パスを反映した`href`と`data-title-href`を生成する
- 未定義の参照ID、重複したID、不正な参照種別を診断する
- 番号なし画像の通常のMarkdown記法を壊さず、VFMへ渡す

個別fixtureでは`href`から図のキャプションを取得したが、各参照種別を同じテーマで扱う[結合検証](vivliostyle-reference-integration.md)により、タイトル取得先を`data-title-href`へ統一した。

## 成立条件と未確認事項

章番号と図番号の組み合わせは、「一つのMarkdownファイルが一つの章に対応し、Vivliostyle設定の`entry`が章順に並ぶ」構成を前提とする。

次の事項は未確認または未決定である。

- clonoで使用する番号付き画像と参照の著者向け記法
- 代替テキストまたはキャプションを省略した場合の補完規則
- キャプション内のMarkdown
- 一つの図へ複数の画像を含める構成
- 存在しないIDや重複IDに対するVFMおよびVivliostyleの挙動
- 章として数えない前付・後付などを別のMarkdownファイルとして`entry`へ含める場合のカウンター制御
- 製品用テーマでの画像サイズと紙面レイアウト

複数画像をまとめた図は、Thunder Clawの次回作で必要となる可能性が低いため、具体的な必要性が生じるまで調査しない。

## 再現方法

検証に使用する入力、HTMLとPDFの自動検証、PDFの目視確認手順は、[検証用fixtureのREADME](fixtures/vivliostyle-figure-references/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm run verify`で再検証できる。

## 再調査する条件

- VFMがMarkdown画像から生成するHTML構造または属性の配置が変更された場合
- Vivliostyle.jsのCSSカウンター、`target-counter()`または`target-text()`の実装が変更された場合
- 書籍の原稿ファイル構成が、一章一ファイルの前提を満たさなくなった場合
- 製品用テーマで画像、番号、キャプションまたは参照の表示が期待どおりにならなかった場合
- Thunder Clawで複数画像をまとめた図が必要になった場合

## 参照資料

- [画像 | Vivliostyle Flavored Markdown](https://docs.vivliostyle.org/ja/vfm/vfm/#画像image)
- [チュートリアル⑤カウンタと柱のスタイル | Vivliostyle](https://vivliostyle.org/ja/tutorials/configure-counters-running-heads/)
- [チュートリアル⑥基本的な要素のスタイル | Vivliostyle](https://vivliostyle.org/ja/tutorials/configure-basic-elements/)
- [Supported CSS Features | Vivliostyle Documentation](https://docs.vivliostyle.org/ja/reference/supported-css-features/)
