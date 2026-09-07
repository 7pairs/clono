# Markdown表directive検証用fixture

## 目的

Generic DirectivesのContainer directive内にGFM形式のMarkdown表を記述し、clono本体と同じ低レベルAPIで解析、変換および再直列化できるかを検証する。変換後MarkdownをVFMへ渡し、番号付き表に必要なHTML構造と表内のMarkdownが保持されることも確認する。

このfixtureは、番号付き表の著者向け記法、class名、HTML構造または診断契約を確定するものではない。仕様策定前の技術的な成立性を確認するため、合意した候補記法と出力構造を使用する。

## 検証対象

入力は[`input/numbered-table.md`](input/numbered-table.md)に保存している。

```markdown
:::table[実行環境]{#runtime}

| 項目 | 値 | 備考 |
| :--- | :---: | ---: |
| 言語 | `ClojureScript` | **必須** |
| 文書 | [VFM](https://vivliostyle.github.io/vfm/) | *推奨*、~~旧形式ではない~~ |

:::
```

次の段階を一つの自動テストで検証する。

```text
候補記法を含むMarkdown
  → Generic DirectivesとGFM表を有効にしたmdast解析
  → 候補となるfigure構造へのAST変換
  → VFM向けMarkdownへの直列化と再解析
  → VFM 2.7.0によるHTML変換
```

## 検証結果

- `table`は`containerDirective`として解析される
- ラベルは`directiveLabel`を持つ段落、表本体はその兄弟となる単一の`table`ノードとして参照できる
- 列の左寄せ、中央寄せ、右寄せは、`table`ノードの`align`へ保持される
- インラインコード、強い強調、強調、リンクは、それぞれ独立したmdastノードとして表のセル内に保持される
- clono本体と同じ解析拡張では、GFMの取り消し線は専用の`delete`ノードにならず、`~~`を含む`text`ノードとして保持される
- Container directiveを開始raw HTML、元の`table`ノード、キャプションと終了raw HTMLへ置き換えて直列化できる
- 変換後Markdownを再解析しても、表、文字寄せ、表内のMarkdownの意味が保持される
- VFM 2.7.0は、変換後Markdownを`figure`、`table`、表の下にある`figcaption`を持つHTMLへ変換する
- VFM変換後も、セルの文字寄せ、インラインコード、強い強調、強調、リンクが保持される
- `text`ノードとして往復した取り消し線記法は、VFMによって`del`へ変換される

これにより、候補となる番号付き表記法を、GFM表を壊さず参照可能な構造へ変換できることを確認した。

## 検証環境

- 実行確認: macOS、Node.js 24.19.0、Temurin JDK 21.0.12
- 対応範囲: Node.js 22.13.0以降の22系、または24系
- shadow-cljs 3.4.12
- `mdast-util-from-markdown` 2.0.3
- `mdast-util-to-markdown` 2.1.2
- `micromark-extension-directive` 4.0.0
- `mdast-util-directive` 3.1.0
- `micromark-extension-gfm-table` 2.1.1
- `mdast-util-gfm-table` 2.0.0
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

`src/test/clono/research/table_directive_test.cljs`は、次を確認する。

- Container directiveのラベルと単一のGFM表を別々の子ノードとして参照できる
- 文字寄せと表内のインラインMarkdownをmdastで識別できる
- 候補となるraw HTMLで表を囲み、VFM向けMarkdownへ直列化できる
- 直列化したMarkdownの再解析後も表の意味が保持される
- VFM変換後に、表、表の下のキャプション、文字寄せおよびインラインMarkdownが保持される
- 取り消し線記法がclono側では`text`として保持され、VFM側で`del`へ変換される

## 検証範囲の境界

このfixtureは、候補記法の意味検証、エラー診断、IDの重複検査、参照解決、CSSカウンター、PDF生成または書籍プロジェクト内の原稿間処理を検証しない。これらのうちVivliostyleへ委譲する機能は、既存の[表ID・キャプション・連番・相互参照fixture](../vivliostyle-table-references/)と[参照機能の結合fixture](../vivliostyle-reference-integration/)で検証している。

表内の画像、脚注、raw HTML、directiveおよび強制改行は検証対象に含めない。特に表内脚注は将来必要になる可能性があるが、初期仕様では許可せず、必要性が具体化した時点でVFMとVivliostyleを含む結合検証を行う。

取り消し線は最終HTMLまで保持できる一方、現在のclono解析器は取り消し線専用のGFM拡張を使用していないため、AST上では通常テキストと区別できない。初期仕様で取り消し線を許可する場合は、clonoがその構文だけを個別に検証することを保証せず、VFMへ意味を保持して渡すものとして扱う。

## 参照資料

- [Vivliostyleの表ID・キャプション・連番・相互参照に関する調査](../../vivliostyle-table-references.md)
- [Markdown AST変換と出力方式に関する調査](../../markdown-ast-transformation.md)
- [Generic DirectivesとmdastによるMarkdown変換パイプラインのADR](../../../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [Generic directives/plugins syntax](https://talk.commonmark.org/t/generic-directives-plugins-syntax/444)
- [`mdast-util-gfm-table` 2.0.0](https://github.com/syntax-tree/mdast-util-gfm-table/tree/2.0.0)
- [`micromark-extension-gfm-table` 2.1.1](https://github.com/micromark/micromark-extension-gfm-table/tree/2.1.1)
- [VFM 2.7.0](https://github.com/vivliostyle/vfm/tree/v2.7.0)
