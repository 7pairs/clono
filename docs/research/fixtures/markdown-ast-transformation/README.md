# Markdown AST変換・出力検証用fixture

## 目的

Generic Directivesを含む単一のMarkdown文字列をmdastとして解析し、既知のdirectiveを意味検証して用途別のraw HTMLへ変換し、VFMが処理できるMarkdownとして出力できるか検証する。出力をVFM 2.7.0でHTMLへ変換し、標準Markdown、コードフェンス、脚注と、clonoが生成する候補構造が共存できることも確認する。

このfixtureは、clonoの著者向け記法、class名、HTML構造、診断文、内部API、採用ライブラリを確定するものではない。実用途を模した仮の変換で、AST変換と出力方式の成立性を調査する。

## 検証パイプライン

```text
入力Markdown
  → mdast解析
  → 未知directiveの検出
  → 既知directiveの意味検証
  → AST変換
  → VFM向けMarkdown
  → VFM 2.7.0
  → HTML検証
```

ファイルへの出力、CLI、複数原稿、文書全体の情報収集、PDF生成は対象に含めない。

## 仮変換

| 想定用途 | 仮のdirective | 仮の出力 |
| --- | --- | --- |
| 右寄せ | Container directiveの`align` | `div.text-align-right` |
| 強制改ページ | Leaf directiveの`page-break` | `div.page-break[aria-hidden="true"]` |
| 索引指定 | Text directiveの`index` | `span.index-marker[data-index-reading]` |

これらは、Container、leaf、textの3種類を現実的な構造へ変換するための候補である。記法、属性、class名、HTML要素は仕様として採用していない。

## 入力と期待結果

### `input/valid.md`

- `align`、`page-break`、`index`がraw HTMLを含むMarkdownへ変換される
- 通常の見出し、段落、強調、リンク、インラインコード、強制改行が保持される
- VFMの脚注参照と定義がmdastの往復後にも保持される
- コードフェンス内のdirectiveらしい文字列は変換されない
- VFM 2.7.0が変換後のMarkdownをHTMLへ変換し、用途別のHTML構造と脚注構造を保持する

### `input/invalid.md`

- `position="right"`を持たない`align`
- 未定義の属性を持つ`page-break`
- 空でない`reading`属性を持たない`index`
- Text directiveとして誤って記述した`align`
- clonoが認識しない`third-party`

4件の不正な既知directiveと1件の未知directiveについて、入力ファイル、行、列、directive名、説明を持つ診断を入力位置順に返す。診断が一つでもある場合は出力Markdownを返さない。

### `input/unknown-nested.md`

- 未知の`third-party` Container directive
- その内側にある不正な既知の`index`
- Containerの外側にある不正な既知の`index`
- 独立した未知の`another-extension` Container directive

未知のContainer directiveについて一件だけ診断し、その子孫は検査しない。Containerの外側にある不正な既知directiveと、別の未知directiveは引き続き検査し、診断を入力位置順に返す。

## AST変換方式

Container directiveは、開始raw HTMLノード、元の`children`、終了raw HTMLノードへ置き換える。これにより、container内のMarkdownをVFMへ委譲する。

Leaf directiveは一つのraw HTMLノードへ置き換える。Text directiveは開始raw HTMLノード、元のインライン`children`、終了raw HTMLノードへ置き換える。動的なHTML属性値はエスケープしてから出力する。属性の境界を脱出しようとする値について、VFM変換後にも許可した`span`と`data-index-reading`だけが生成されることを確認する。

初期ポリシーでは未知のdirectiveをエラーとし、AST変換と直列化を行わない。一方、低レベルAPIが未知のdirectiveの名前、属性、本文を意味的に保持できることは、将来ポリシーを変更できる技術的能力として別のテストで維持する。これは未知のdirectiveを保持することを初期仕様として採用するものではない。

## 脚注の保持

VFMの脚注記法を通常テキストへ崩さずmdastとして往復させるため、解析に`micromark-extension-gfm-footnote`、解析と直列化に`mdast-util-gfm-footnote`を使用する。

このfixtureでは、脚注参照と定義がそれぞれ`footnoteReference`と`footnoteDefinition`になり、直列化後も脚注記法として残ることを確認する。そのMarkdownをVFMの`dpub`モードで変換し、`doc-noteref`と`doc-footnote`のHTML構造、脚注内のインラインコードとリンクを検証する。

## 依存関係

- Node.js 22.13.0以降の22系、または24系
- Temurin JDK 21
- shadow-cljs 3.4.12
- `mdast-util-from-markdown` 2.0.3
- `mdast-util-to-markdown` 2.1.2
- `micromark-extension-directive` 4.0.0
- `mdast-util-directive` 3.1.0
- `micromark-extension-gfm-footnote` 2.1.0
- `mdast-util-gfm-footnote` 2.1.0
- `@vivliostyle/vfm` 2.7.0
- `node-html-parser` 9.0.1

依存関係はこのディレクトリの`package-lock.json`に固定する。`@vivliostyle/vfm`と`node-html-parser`は、clono側の変換後に行うHTML結合検証だけに使用する開発依存関係である。

clono側の解析、検証、AST変換、Markdown直列化は、`remark-parse`、`remark-directive`、`unified`を直接使用せず、mdastおよびmicromarkの低レベルAPIで実装する。VFM 2.7.0自身の推移的依存関係には`remark-parse`と`unified`が含まれるが、これはVFMによる後段のHTML変換に使用される。

2026年8月21日の`npm audit`では、開発依存関係のVFM 2.7.0を経由する3件のmoderateと3件のhighが報告された。`npm audit --omit=dev`では0件であり、clono側の変換を模したproduction依存関係には報告されなかった。このfixtureは固定したVFMとの結合を再現する調査資材であり、外部から受け取った信頼できない入力の処理には使用しない。

## 実行方法

```shell
set -eu
npm ci
npm test
```

正常な入力から生成されるVFM向けMarkdownを表示するには、次を実行する。

```shell
set -eu
npm run inspect
```

不正な入力の診断と終了コードを確認するには、inspectorをビルドしてから対象を指定する。

```shell
set -eu
npm run build:inspect
node target/inspect.js input/invalid.md
```

`target/`と`.shadow-cljs/`は生成物であり、Gitの管理対象には含めない。

## 自動検証

`src/test/clono/research/transformer_test.cljs`は、次を確認する。

- 正常な入力では、診断なしでVFM向けMarkdownを返す
- 3種類の既知directiveをraw HTMLへ置き換える
- 動的なHTML属性値をエスケープし、属性または要素の境界を脱出できない
- 再解析後に既知directiveが残らない
- 通常のMarkdown、コードフェンス、脚注のmdast構造を保持する
- 不正な既知directiveをすべて位置付きで診断し、出力を返さない
- 未知のdirectiveを位置付きで診断し、出力を返さない
- 未知のContainer directiveの子孫を検査せず、外側の独立した問題は入力位置順に診断する
- 検証ポリシーを迂回した低レベルAPIでは、未知のdirectiveを意味的に保持できる
- VFM変換後に、通常のMarkdown、用途別HTML、脚注、コードフェンスの内容が保持される

## 検証範囲の境界

診断は、パーサーがdirectiveノードとして認識した入力に対する未知directiveの検出と、既知directiveの意味検証を扱う。Generic Directives自体の構文が壊れ、通常テキストなど別の構造になった場合の検出はこのfixtureに含めず、`markdown-ast` fixtureの追加調査で扱う。

変換処理は、意味検証がすべて成功した後にだけASTを変更する。失敗時には出力文字列を返さない。既存ファイルを残さないための一時ファイルや安全な置換は、ファイルI/Oを実装する段階で検証する。

入力位置は変換前のmdastから診断へコピーする。出力Markdownと元の入力とのsource mapは生成しない。

未知のdirectiveを警告または保持する将来のポリシー、その場合の子孫走査、複数エラーの表示形式、診断コード、HTML以外の出力、生成Markdownの整形方針は確定しない。

## 参照資料

- [Markdown AST変換と出力方式に関する調査](../../markdown-ast-transformation.md)
- [Generic DirectivesとMarkdown ASTに関する調査](../../markdown-ast.md)
- [ADR 0003: Generic DirectivesとmdastによるMarkdown変換パイプラインを採用する](../../../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [Generic directives/plugins syntax](https://talk.commonmark.org/t/generic-directives-plugins-syntax/444)
- [`mdast-util-from-markdown` 2.0.3](https://github.com/syntax-tree/mdast-util-from-markdown/tree/2.0.3)
- [`mdast-util-to-markdown` 2.1.2](https://github.com/syntax-tree/mdast-util-to-markdown/tree/2.1.2)
- [`micromark-extension-directive` 4.0.0](https://github.com/micromark/micromark-extension-directive/tree/4.0.0)
- [`mdast-util-directive` 3.1.0](https://github.com/syntax-tree/mdast-util-directive/tree/3.1.0)
- [`micromark-extension-gfm-footnote` 2.1.0](https://github.com/micromark/micromark-extension-gfm-footnote/tree/2.1.0)
- [`mdast-util-gfm-footnote` 2.1.0](https://github.com/syntax-tree/mdast-util-gfm-footnote/tree/2.1.0)
- [VFM 2.7.0](https://github.com/vivliostyle/vfm/tree/v2.7.0)
