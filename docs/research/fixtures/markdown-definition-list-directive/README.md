# Markdown定義リストdirective検証用fixture

## 目的

Generic Directivesの入れ子を使用して、複数の用語と説明を持つ定義リストをclono本体と同じ低レベルAPIで解析、変換および再直列化できるか検証する。変換後MarkdownをVFMとVivliostyleへ渡し、定義リストの意味構造、説明内のインラインMarkdown、および項目単位の改ページ制御が保持されることも確認する。

このfixtureは、定義リストの著者向け記法、class名、HTML構造または診断契約を確定するものではない。仕様策定前の技術的な成立性を確認するため、候補記法と候補出力を使用する。

検証結果、評価および未確認事項は、[Generic Directivesと定義リストのMarkdown ASTに関する調査](../../markdown-definition-list-directive.md)を参照する。

## 候補記法

入力は[`input/definition-list.md`](input/definition-list.md)へ保存している。外側の`definition-list`がリスト全体、内側の各`definition`が一つの用語と説明を表す。内側に3個のコロンを使用するため、外側は4個のコロンで囲む。

````markdown
::::definition-list
:::definition[`READY`]
処理を開始できる**待機状態**。詳細は[状態遷移の仕様](https://example.com/state)を参照する。
:::

:::definition[DONE]
処理が正常に完了した状態を表す。終了コードは`0`となる。
:::
::::
````

## 検証する変換

候補記法を、次の構造を持つVFM向けMarkdownへ変換する。

```html
<dl class="clono-definition-list">
<div class="clono-definition-item">
<dt><code>READY</code></dt>
<dd>

処理を開始できる**待機状態**。詳細は[状態遷移の仕様](https://example.com/state)を参照する。

</dd>
</div>
</dl>
```

用語は`dt`へ、説明の段落は`dd`へ配置する。各組を`div.clono-definition-item`で囲み、用語と説明へ項目単位のCSSを適用できるようにする。

## 検証結果

- 外側の記法は一つの`containerDirective`として解析され、その子に二つの`definition` Container directiveを保持できる
- 各`definition`のラベルと本文は、`directiveLabel`を持つ段落と通常段落として区別できる
- 用語に記述したインラインコードは`inlineCode`として解析される
- 複数の項目を、一つの`dl`と項目ごとの`div`、`dt`、`dd`を持つVFM向けMarkdownへ直列化できる
- VFM 2.7.0は候補HTML構造を保持し、説明内の強調、インラインコードおよびリンクをHTMLへ変換できる
- Vivliostyle.js 2.44.1は項目コンテナへ指定した改ページ規則を反映し、用語と説明を同じページへ配置できる

## 検証環境

- 実行確認: macOS、Node.js 24.19.0、Temurin JDK 21.0.12
- 対応範囲: Node.js 22.13.0以降の22系、または24系
- shadow-cljs 3.4.12
- `mdast-util-from-markdown` 2.0.3
- `mdast-util-to-markdown` 2.1.2
- `micromark-extension-directive` 4.0.0
- `mdast-util-directive` 3.1.0
- `@vivliostyle/vfm` 2.7.0
- `@vivliostyle/cli` 11.1.0
  - CLIが使用するVivliostyle.js 2.44.1
- `node-html-parser` 9.0.1
- `mupdf` 1.28.0

正確な依存関係は`package.json`と`package-lock.json`で固定する。

## 実行方法

```shell
set -eu
npm ci
npm run verify
```

`npm test`はAST、変換後MarkdownおよびVFM変換後HTMLを検証する。`npm run verify:pdf`は`generate:manuscript`で変換後Markdownを再生成した上でPDFを検証する。

`target/`、`output/`、`.shadow-cljs/`および`node_modules/`は生成物であり、Gitの管理対象には含めない。

## 自動検証

`src/test/clono/research/definition_list_directive_test.cljs`は、次を確認する。

- 入れ子のContainer directiveからリストと各項目を区別できる
- 用語のインラインコードと、各項目の単一の説明段落を参照できる
- 一つの`dl`と二つの項目を持つVFM向けMarkdownへ変換できる
- VFM変換後に`dl`、項目単位の`div`、`dt`および`dd`が保持される
- 説明内の強調、インラインコードおよびリンクが変換される

`scripts/verify-pdf.mjs`は、次を確認する。

- Vivliostyle CLIが空でないPDFを生成する
- 各項目の用語と説明が同じページにある
- fixture専用の改ページ規則により、二番目の項目が一番目より後のページへ移動する

PDF検証はページ数、絶対座標または特定フォントのメトリクスを固定しない。項目内の用語と説明の相対的なページ関係だけを検証する。

## 検証範囲の境界

このfixtureは、候補記法の意味検証、エラー診断、空のリスト、空の用語、空の説明、複数段落、リスト、コードブロック、属性、入れ子の制限または基盤CSSの最終的な内容を検証しない。

`style.css`の`break-before: page`は、項目単位の改ページを決定的に確認するためのfixture専用規則であり、clonoの基盤CSS候補ではない。`break-inside: avoid`を基盤CSSへ含めるかは、仕様策定時に決定する。

## 参照資料

- [Generic Directivesと定義リストのMarkdown ASTに関する調査](../../markdown-definition-list-directive.md)
- [Vivliostyleの基本表現機能に関する調査](../../vivliostyle-basic-presentation.md)
- [Markdown AST変換と出力方式に関する調査](../../markdown-ast-transformation.md)
- [Generic DirectivesとmdastによるMarkdown変換パイプラインのADR](../../../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [Generic directives/plugins syntax](https://talk.commonmark.org/t/generic-directives-plugins-syntax/444)
- [`mdast-util-directive` 3.1.0](https://github.com/syntax-tree/mdast-util-directive/tree/3.1.0)
- [VFM 2.7.0](https://github.com/vivliostyle/vfm/tree/v2.7.0)
