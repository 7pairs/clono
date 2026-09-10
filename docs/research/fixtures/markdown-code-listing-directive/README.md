# Markdownコードリストdirective検証用fixture

## 目的

Generic DirectivesのContainer directive内にフェンス付きコードブロックを記述し、clono本体と同じ低レベルAPIで解析、変換および再直列化できるかを検証する。変換後MarkdownをVFMへ渡し、番号付きコードリストに必要なHTML構造、キャプション位置およびコードの内容が保持されることも確認する。

このfixtureは、番号付きコードリストの著者向け記法、class名、HTML構造または診断契約を確定するものではない。仕様策定前の技術的な成立性を確認するため、合意した候補記法と出力構造を使用する。

## 検証対象

入力は[`input/numbered-listings.md`](input/numbered-listings.md)に保存している。言語指定のあるコードリストと言語指定のないコードリストを一つずつ記述し、コード内にはdirective風の文字列も含めている。

````markdown
:::listing[A &amp; &quot;B&quot; &lt;C&gt;]{#greeting}

```kotlin
fun greet(name: String): String {
    return "Hello, $name!"
}
```

:::
````

次の段階を一つの自動テストで検証する。

```text
候補記法を含むMarkdown
  → Generic Directivesを有効にしたmdast解析
  → 候補となるfigure構造へのAST変換
  → VFM向けMarkdownへの直列化と再解析
  → VFM 2.7.0によるHTML変換
```

フェンス開始行に`title=Main.kt`を付けた別の最小入力もテスト内で解析し、言語指定とメタ情報を独立して識別できることを確認する。

## 検証結果

- `listing`は`containerDirective`として解析される
- ラベルは`directiveLabel`を持つ段落、コード本体はその兄弟となる単一の`code`ノードとして参照できる
- コードフェンスの言語指定は`code.lang`、メタ情報は`code.meta`として独立して参照できる
- 言語を省略した場合、mdast上の`code.lang`と`code.meta`はともに`null`になる
- コード内のdirective風文字列はコード本文の一部として保持され、directiveノードとして解析されない
- Container directiveを開始raw HTML、元の`code`ノード、終了raw HTMLへ置き換えて直列化できる
- 変換後Markdownを再解析しても、言語指定とコード本文が保持される
- VFM 2.7.0は、変換後Markdownを`figure`、上側の`figcaption`、`pre`および`code`を持つHTMLへ変換する
- VFM 2.7.0は、Kotlinのコードへ構文強調を適用し、言語指定のないコードへ`language-text`を付与する

これにより、候補となる番号付きコードリスト記法を、コードフェンスの意味を壊さず参照可能な構造へ変換できることを確認した。

## 検証環境

- 実行確認: macOS、Node.js 24.19.0、Temurin JDK 21.0.12
- 対応範囲: Node.js 22.13.0以降の22系、または24系
- shadow-cljs 3.4.12
- `mdast-util-from-markdown` 2.0.3
- `mdast-util-to-markdown` 2.1.2
- `micromark-extension-directive` 4.0.0
- `mdast-util-directive` 3.1.0
- VFM 2.7.0
- `node-html-parser` 9.0.1

正確な依存関係は`package.json`と`package-lock.json`で固定する。`@vivliostyle/vfm`と`node-html-parser`は、clono側の変換後に行うHTML結合検証だけに使用する開発依存関係である。

## 実行方法

```shell
set -eu
npm ci
npm test
```

解析したmdastと変換後Markdownを目視する場合は、次のコマンドを実行する。

```shell
set -eu
npm run inspect
```

`target/`、`.shadow-cljs/`および`node_modules/`は生成物であり、Gitの管理対象には含めない。

## 自動検証

`src/test/clono/research/listing_directive_test.cljs`は、次を確認する。

- 各Container directiveのラベル、IDおよび単一の`code`ノードを参照できる
- コードフェンスの言語指定が任意であり、指定時は`lang`として参照できる
- コードフェンスのメタ情報を`lang`とは独立して参照できる
- コード内のdirective風文字列が追加のdirectiveノードとして解析されない
- 候補となるraw HTMLでコードブロックを囲み、VFM向けMarkdownへ直列化できる
- 直列化したMarkdownの再解析後も、言語指定とコード本文が保持される
- VFM変換後に、`figure`、上側の`figcaption`、`pre`および`code`が保持される
- VFM変換後も、Kotlinの構文強調と、言語指定のないコードへVFMが付与する`language-text`をそれぞれ識別できる

## 検証範囲の境界

このfixtureは、候補記法の意味検証、エラー診断、IDの重複検査、参照解決、CSSカウンター、PDF生成または書籍プロジェクト内の原稿間処理を検証しない。コードフェンスのメタ情報はASTで識別できることだけを確認し、初期仕様で許可するものではない。

Vivliostyleへ委譲する番号表示、キャプション配置、構文強調、長いコードリストの改ページおよびPDF内部リンクは、既存の[コードリストID・キャプション・連番・相互参照fixture](../vivliostyle-code-listing-references/)と[参照機能の結合fixture](../vivliostyle-reference-integration/)で検証している。

## 参照資料

- [VivliostyleのコードリストID・キャプション・連番・相互参照に関する調査](../../vivliostyle-code-listing-references.md)
- [Markdown AST変換と出力方式に関する調査](../../markdown-ast-transformation.md)
- [Generic DirectivesとmdastによるMarkdown変換パイプラインのADR](../../../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [Generic directives/plugins syntax](https://talk.commonmark.org/t/generic-directives-plugins-syntax/444)
- [`mdast-util-directive` 3.1.0](https://github.com/syntax-tree/mdast-util-directive/tree/3.1.0)
- [VFM 2.7.0](https://github.com/vivliostyle/vfm/tree/v2.7.0)
