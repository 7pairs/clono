# Vivliostyleの見出しID・連番・相互参照に関する調査

- 状態: 調査済み
- 初回調査日: 2026-08-16
- 最終更新日: 2026-08-16
- 検証環境:
  - 実行環境: macOS、Node.js 24.19.0
  - HTML変換: `@vivliostyle/vfm` 2.7.0
  - PDF生成: `@vivliostyle/cli` 11.1.0
    - CLIが依存する`@vivliostyle/vfm` 2.7.0
    - CLIが使用するVivliostyle.js 2.44.1
  - PDF検証: `mupdf` 1.28.0

## 背景

Thunder Clawの前作では、見出し、画像、表、コードリストへ参照用IDと連番を付与し、別の場所から番号または番号とタイトルを参照する処理を独自のビルドスクリプトで実装していた。

現在のVivliostyleへ委譲できる範囲を判断するため、最初に見出しを対象として、VFMによるID付与、CSSカウンターによる章・節・小節番号、同一Markdownファイル内および別Markdownファイル間の相互参照を調査した。

画像、表、コードリストについては、この調査の対象に含めない。

## Thunder Clawの要件

- 参照する見出しへ安定したIDを付与できる
- 章ごとに節と小節の番号をリセットする
- 章番号は`第2章`、節と小節の番号は`2.1`、`2.1.1`の形式にする
- 番号とタイトルを参照する場合は、`第1章 はじめに`や`2.1 構造`のように番号、半角スペース、タイトルの順で表示する
- 同一Markdownファイル内だけでなく、別Markdownファイルの見出しも参照できる
- 製品となるPDFで参照をクリックし、対象の見出しへ移動できる
- 一章を一つのMarkdownファイルとして構成する

## 公式情報の確認

VFMでは、見出しの末尾へ`{#identifier}`を記述すると、生成する見出し要素の`id`にその値を使用できる。

```markdown
# はじめに {#chapter-introduction}
```

VivliostyleはCSSカウンターに加え、リンク先のカウンターを取得する`target-counter()`と、リンク先のテキストを取得する`target-text()`に対応している。これらの関数は、疑似要素の`content`で使用できる。

Vivliostyle公式チュートリアルでは、次のページセレクターを使用し、複数の原稿ファイルへ連続した番号を付ける方法が紹介されている。

- `@page :first`: 全原稿ファイルの中で最初のページ
- `@page :nth(1)`: 各原稿ファイルの中で最初のページ

この仕組みを章番号へ適用すると、書籍全体の先頭で章カウンターを初期化し、各Markdownファイルの先頭ページで一度だけインクリメントできる。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/vivliostyle-heading-references/)をリポジトリ内へ保存している。このfixtureは`clono`本体から独立したnpmプロジェクトであり、`package.json`と`package-lock.json`によって検証環境の依存関係を固定する。

fixtureでは、第1章と第2章を別々のMarkdownファイルに記述し、Vivliostyle CLIの`entry`へ章順に登録する。参照される見出しには明示的なIDを付ける。

clonoの著者向け参照記法は未決定であるため、変換後の出力を模した空の`a`要素を原稿へ直接記述する。

```html
<a class="xref-chapter xref-title"
   href="chapter-two.html#chapter-design"></a>
```

CSSは、この要素の`href`を使用して番号とタイトルを生成する。

```css
a.xref-chapter::before {
  content: "第" target-counter(attr(href url), chapter) "章";
}

a.xref-title::after {
  content: " " target-text(attr(href url), content);
}
```

HTMLの検証プログラムは、VFMが明示的な見出しIDと参照用の`a`要素を保持することを確認する。

PDFの検証プログラムは、既存のPDFを削除してからVivliostyle CLIで再生成し、次を自動で確認する。

- 章・節・小節の番号とタイトルが期待する文字列で出力される
- 同一Markdownファイル内と別Markdownファイル間の参照文字列が出力される
- すべての参照が解決可能なPDF内部リンクになる
- 第1章から第2章、第2章から第1章の両方向へリンクできる

生成したPDFは全ページを画像へ変換し、見出しや本文の欠落、重なり、参照文字列の表示も目視確認した。

### 見出しID

VFMが`{#identifier}`で指定したIDを、対応する`h1`、`h2`、`h3`要素へ出力することを確認した。参照用IDをVFMとは別の方法で生成または付け直す必要はない。

IDの自動生成は検証していない。参照先を安定させ、タイトル変更の影響を避けるため、参照する見出しには著者が明示的なIDを指定する方針を候補とする。

### 章・節・小節番号

章カウンターは次のCSSで管理した。

```css
@page :first {
  counter-reset: chapter;
}

@page :nth(1) {
  counter-increment: chapter;
}
```

節と小節のカウンターは、VFMが生成する`section.level1`、`section.level2`、`section.level3`でリセットまたはインクリメントした。

この方法により、Markdownのfrontmatterで章番号の開始値を指定せずに、第1章を`第1章`、第2章を`第2章`と表示できた。節と小節についても、`1.1`、`1.1.1`、`2.1`、`2.1.1`の形式で出力できた。

### 番号とタイトルの参照

`target-counter()`を使用し、章、節、小節の番号を参照できた。`target-text()`を組み合わせることで、番号、半角スペース、タイトルの形式でも参照できた。

見出しにインラインコードが含まれる場合も、`target-text()`は表示上のプレーンテキストを取得し、`1.2 clonoの概要`と出力した。

### 別Markdownファイル間の参照

変換後のHTMLファイル名と見出しIDを組み合わせた`chapter-two.html#chapter-design`のような`href`を使用し、別Markdownファイルの番号とタイトルを参照できた。

生成したPDFでは参照が内部リンクになり、同一ファイル内、別ファイル間、前方参照、後方参照のすべてで対象の見出しが存在するページへ移動できた。

### fixture固有のレイアウト調整

検証中、二つ目の原稿ファイルの先頭見出しがページ上端で一部欠ける現象を確認した。これはfrontmatterによる章番号指定と`@page`による章番号指定の両方で発生したため、章カウンター方式とは独立したレイアウト上の問題と判断した。

fixtureでは目視確認を安定させるため、第2章の先頭へ`chapter-start-spacer`を配置している。これは製品用の原稿構文やテーマとして採用するものではない。実際の書籍テーマによるレイアウトは別途確認する。

## 責務判断

見出しのID、連番、番号参照、番号とタイトルの参照は、VFM、VivliostyleおよびテーマCSSを利用して要件を満たせる。clonoが見出し番号やタイトルを計算し、出力へ直接埋め込む処理は実装しない方針とする。

### Vivliostyleへ委譲する責務

- 明示的な見出しIDをHTMLへ出力する
- CSSカウンターを使用して章・節・小節番号を生成する
- `target-counter()`と`target-text()`を使用して参照文字列を生成する
- 参照をPDF内部リンクとして保持する

### clonoが担う候補となる責務

- 著者向けの参照記法を、適切な`href`と用途別のclassを持つ空の`a`要素へ変換する
- 別Markdownファイルを参照する場合に、変換後の出力パスを反映した`href`を生成する
- 未定義の参照ID、重複したID、不正な参照種別を診断する
- 見出しIDと参照要素を壊さず、VFMとVivliostyleへ渡す

著者が指定する見出しIDの記法にはVFMの`{#identifier}`をそのまま利用できるため、clono独自のID付与記法を追加する必要性は現時点で確認できない。

## 成立条件と未確認事項

`@page :nth(1)`による章番号は、「一つのMarkdownファイルが一つの章に対応し、Vivliostyle設定の`entry`が章順に並ぶ」構成を前提とする。

次の事項は未確認または未決定である。

- 章として数えない前付・後付などを別のMarkdownファイルとして`entry`へ含める場合のカウンター制御
- 一つのMarkdownファイルへ複数の章を記述する場合の番号制御
- `h4`以降の番号形式
- 見出しIDと著者向け参照記法の最終仕様
- 未定義IDと重複IDに対するVFMおよびVivliostyleの挙動
- 製品用テーマでのページ先頭見出しのレイアウト

Thunder Clawの次回作で前付・後付を別ファイルとして構成する場合は、実際の`entry`構成を決定した後にカウンター制御を追加検証する。

## 再現方法

検証に使用する入力、HTMLとPDFの自動検証、PDFの目視確認手順は、[検証用fixtureのREADME](fixtures/vivliostyle-heading-references/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm run verify`で再検証できる。

## 再調査する条件

- VFMが生成する見出しまたはセクションのHTML構造が変更された場合
- Vivliostyle.jsのCSSカウンター、`target-counter()`または`target-text()`の実装が変更された場合
- 書籍の原稿ファイル構成が、一章一ファイルの前提を満たさなくなった場合
- 製品用テーマで番号または参照の表示が期待どおりにならなかった場合
- Thunder Clawで`h4`以降の番号参照が必要になった場合

## 参照資料

- [セクション分け（Sectionization） | Vivliostyle Flavored Markdown](https://docs.vivliostyle.org/ja/vfm/vfm/#セクション分けsectionization)
- [チュートリアル⑤カウンタと柱のスタイル | Vivliostyle](https://vivliostyle.org/ja/tutorials/configure-counters-running-heads/)
- [Supported CSS Features | Vivliostyle Documentation](https://docs.vivliostyle.org/ja/reference/supported-css-features/)
