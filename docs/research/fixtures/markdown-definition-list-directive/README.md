# Markdown定義リストdirective検証用fixture

## 目的

Generic Directivesの入れ子を使用して、複数の用語と説明を持つ定義リストをclono本体と同じ低レベルAPIで解析、変換および再直列化できるか検証する。変換後MarkdownをVFMとVivliostyleへ渡し、定義リストの意味構造、説明内のインラインMarkdown、および自然なページ境界における項目単位の分断防止も確認する。

このfixtureは、定義リストの正式な著者向け記法、class名、HTML構造または診断契約を確定するものではない。仕様策定前の技術的な成立性を確認するため、候補記法と候補出力を使用する。

検証結果、評価および未確認事項は、[Generic Directivesと定義リストのMarkdown ASTに関する調査](../../markdown-definition-list-directive.md)を参照する。

## 候補記法

入力は[`input/definition-list.md`](input/definition-list.md)へ保存している。外側の`definition-list`がリスト全体、内側の各`definition`が一組の用語と説明を表す。用語は`definition`直下の`term` Leaf directive、説明はその後の段落へ記述する。

内側に3個のコロンを使用するため、外側は4個のコロンで囲む。

````markdown
::::definition-list
:::definition
::term[`READY`]

処理を開始できる**待機状態**。詳細は[状態遷移の仕様](https://example.com/state)を参照する。
:::

:::definition
::term[DONE]

処理が正常に完了した状態を表す。終了コードは`0`となる。
:::
::::
````

初期仕様では各`definition`へ`term`を一つだけ許可する想定である。将来、同じ説明を共有する複数の用語が必要になった場合は、`term`の個数制約だけを緩められることも別の入力で検証する。

```markdown
:::definition
::term[一塁手]
::term[二塁手]

内野手です。
:::
```

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

各`term`は`dt`へ、説明の段落は一つの`dd`へ配置する。各組を`div.clono-definition-item`で囲み、用語と説明へ項目単位のCSSを適用できるようにする。複数の`term`がある場合は、同じ項目内へ複数の`dt`と一つの`dd`を生成する。

## 検証結果

- 外側の記法は一つの`containerDirective`として解析され、その子に二つの`definition` Container directiveを保持できる
- 各`term`は`leafDirective`として解析され、説明の通常段落と明確に区別できる
- 用語に記述したインラインコードは`inlineCode`として解析される
- 空の用語またはリンクを含む用語を位置付きで診断し、AST変換と出力生成を停止できる
- 一つの`definition`へ複数の`term`を記述し、複数の`dt`が一つの`dd`を共有する構造へ変換できる
- 複数の項目を、一つの`dl`と項目ごとの`div`、`dt`、`dd`を持つVFM向けMarkdownへ直列化できる
- VFM 2.7.0は候補HTML構造を保持し、説明内の強い強調、インラインコードおよびリンクをHTMLへ変換できる
- Vivliostyle.js 2.44.1は`break-inside: avoid`がない基準版ではページ末尾の用語と説明を分断し、同規則がある保護版では項目全体を次ページへ移動する

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

`npm test`はAST、変換後MarkdownおよびVFM変換後HTMLを検証する。`npm run verify:pdf`は`generate:manuscript`で変換後Markdownを再生成した上で、基準版と保護版のPDFを検証する。

`target/`、`output/`、`.shadow-cljs/`および`node_modules/`は生成物であり、Gitの管理対象には含めない。

## 自動検証

`src/test/clono/research/definition_list_directive_test.cljs`は、次を確認する。

- 入れ子のContainer directiveからリストと各項目を区別できる
- `term` Leaf directiveと説明段落を明確に区別できる
- 用語のインラインコードを参照できる
- 空の用語または許可しないリンクを含む用語では、診断を返して部分的な出力を生成しない
- 一つの項目に複数の`term`がある場合、複数の`dt`が一つの`dd`を共有する
- 一つの`dl`と二つの項目を持つVFM向けMarkdownへ変換できる
- VFM変換後に`dl`、項目単位の`div`、`dt`および`dd`が保持される
- 説明内の強い強調、インラインコードおよびリンクが変換される

`scripts/verify-pdf.mjs`は、同じ変換後Markdownから次の二つのPDFを生成して比較する。

- `style-baseline.css`を使用し、項目の分断防止を指定しない基準版
- `style.css`を使用し、`.clono-definition-item`へ`break-inside: avoid`を指定する保護版

ページ末尾の残り領域は、fixture専用の空要素で決定的に不足させる。基準版では`READY`の説明が用語の直後のページへ分かれ、保護版では用語と説明の両方がそのページへ移動することを確認する。`DONE`の用語と説明も保護版で同じページにあることを確認する。

PDF検証は絶対座標や特定フォントのメトリクスを固定しない。項目内の用語と説明、および基準版と保護版の相対的なページ関係を検証する。

## 検証範囲の境界

このfixtureは、用語ラベルの空値と許可ノード以外について、候補記法の意味検証、エラー診断、空のリスト、空の説明、複数段落、リスト、コードブロック、属性、入れ子の制限または基盤CSSの最終的な内容を検証しない。

`style-base.css`の固定したページ寸法、行高および空要素は、比較可能なページ境界を作るためのfixture専用設定であり、clonoの基盤CSS候補ではない。`style.css`にある`.clono-definition-item { break-inside: avoid; }`だけが分断防止の候補規則である。項目自体が一ページより長い場合の挙動と製品用テーマとの統合は検証していない。

## 参照資料

- [Generic Directivesと定義リストのMarkdown ASTに関する調査](../../markdown-definition-list-directive.md)
- [Vivliostyleの基本表現機能に関する調査](../../vivliostyle-basic-presentation.md)
- [Markdown AST変換と出力方式に関する調査](../../markdown-ast-transformation.md)
- [Generic DirectivesとmdastによるMarkdown変換パイプラインのADR](../../../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [Generic directives/plugins syntax](https://talk.commonmark.org/t/generic-directives-plugins-syntax/444)
- [`mdast-util-directive` 3.1.0](https://github.com/syntax-tree/mdast-util-directive/tree/3.1.0)
- [VFM 2.7.0](https://github.com/vivliostyle/vfm/tree/v2.7.0)
