# Markdown AST検証用fixture

## 目的

Generic Directives Proposalに基づくMarkdownをmdastへ変換し、ClojureScriptから構造を読み書きできることと、Markdownへ意味を保って戻せることを検証する。また、mdastとmicromarkのイベントを利用して、不完全な既知のdirectiveをどこまで位置付きで検出できるか確認する。

このfixtureの目的は、remarkまたは個別のライブラリをclonoへ採用することではない。実際に生成されるmdastの形と、低レベルAPIを直接利用する構成の成立性を確認し、後続の技術選定に必要な事実を残すことである。

## 仮記法

`input/candidate.md`では、実際の用途を模した次の名前を使用する。

- Container directive: `column`、`align`
- Leaf directive: `page-break`
- Text directive: `index`、`xref`

これらはASTを現実的な入力で検証するための**仮記法**であり、clonoの著者向け記法、属性名、意味、出力を確定するものではない。

## 検証対象

- Container、leaf、text directiveが、それぞれ対応するmdastノードになる
- directiveの名前、日本語を含む属性、子要素、入力位置をClojureScriptから参照できる
- container directive内の段落、強調、インラインコード、リンク、リストが通常のmdastとして保持される
- ClojureScriptからJSオブジェクトのフィールドを変更し、Markdownへの直列化と再解析後にも変更を保持できる
- 元の文字列との完全一致ではなく、解析、直列化、再解析を通して構造と意味を保持できる
- clonoが認識しないdirectiveを意味解釈せずに保持できる
- 必須属性が欠けた既知のdirectiveでも、意味検証に利用できるノードと入力位置を取得できる
- Generic Directivesとして不完全な構文が、パーサーによってどのように扱われるかを確認できる
- コードフェンス内のdirectiveらしい文字列がdirectiveとして解析されない
- 閉じていない既知のContainer directiveを、micromarkのフェンスイベントから位置付きで検出できる
- 閉じていない属性がdirectiveの直後に通常テキストとして残る場合、mdastの終了位置と既知directiveの検証規則を組み合わせて位置付きで検出できる
- コードフェンス、インラインコード、エスケープした文字列、通常の時刻表記を構文診断として誤検出しない

## 入力

| ファイル | 用途 |
| --- | --- |
| `input/candidate.md` | 実用途を模した5種類の仮記法、通常のMarkdown、属性、入力位置、往復変換 |
| `input/unknown.md` | clonoが認識しない想定のdirective |
| `input/malformed.md` | 必須属性の欠落、閉じていない属性、閉じていないcontainer directive |
| `input/code-fence.md` | コードフェンス内のdirectiveらしい文字列 |
| `input/malformed-attributes.md` | 閉じていない既知のText directive属性に対する位置付き診断 |
| `input/unclosed-container.md` | 閉じていない既知のContainer directiveに対する位置付き診断 |
| `input/directive-like-literals.md` | コードフェンス、インラインコード、エスケープ、通常テキストの誤検出防止 |

## 依存関係

- Node.js 22.13.0以降の22系、または24系
- shadow-cljs 3.4.12
- `mdast-util-from-markdown` 2.0.3
- `mdast-util-to-markdown` 2.1.2
- `micromark` 4.0.2
- `micromark-extension-directive` 4.0.0
- `mdast-util-directive` 3.1.0
- Temurin JDK 21

依存関係はこのディレクトリの`package-lock.json`に固定する。解析と直列化には`remark-parse`、`remark-directive`、`unified`を使用せず、mdastおよびmicromarkの低レベルAPIを直接使用する。

## 実行方法

```shell
set -eu
npm ci
npm test
```

実際に生成されたmdastをJSONで表示するには、次のコマンドを使用する。

```shell
set -eu
npm run inspect
```

引数を指定して別の入力を確認する場合は、先にinspectorをビルドしてから実行する。

```shell
set -eu
npm run build:inspect
node target/inspect.js input/malformed.md
```

`target/`と`.shadow-cljs/`は生成物であり、Gitの管理対象には含めない。

## 自動検証

`src/test/clono/research/markdown_ast_test.cljs`は、次を確認する。

- 5種類の仮記法に対応するノード型、名前、属性、子要素
- container directive内にある通常のMarkdownのAST構造
- directiveの行、列、オフセット
- 解析、直列化、再解析後の意味の維持
- ClojureScriptから変更したJSオブジェクトの直列化
- 未知のdirectiveの保持
- 意味上の必須属性が欠けたdirectiveと、その入力位置の取得
- 閉じていない属性が通常テキストとして残る挙動
- 閉じていないcontainer directiveが文書末尾まで含む挙動
- コードフェンス内のdirectiveらしい文字列の保護
- 閉じていない属性とContainer directiveに対するファイル名、行、列付きの構文診断
- コードフェンス、インラインコード、エスケープした記法、通常テキストに対する構文診断の抑制

## 検証範囲の境界

このfixtureでは、パーサーがdirectiveとして認識した既知のノードについて、clonoが属性と入力位置を利用して意味検証できることまでを扱う。clono用directiveの検証規則や診断形式は確定しない。

構文が壊れている場合、パーサーがdirectiveの一部を通常テキストとして扱ったり、閉じていないcontainer directiveを文書末尾までのノードとして扱ったりする。追加検証では、micromarkのイベントに含まれる開始および終了フェンスの数と、mdastノードの終了位置に残る未解析の`{`を利用し、調査対象の二種類を位置付きで診断した。

この構文診断は、実現可能性を確認するための試作である。mdastとmicromarkのイベントを個別に生成するため入力を二度解析しており、production実装のAPIや処理回数を決定するものではない。また、構文の壊れ方によっては、Container directiveまたはLeaf directiveの開始行全体が通常テキストになり、directiveノードもdirectiveイベントも生成されない。既知のdirective名を通常テキストから追加で探索しなければ検出できない場合があるため、すべての構文誤りを検出できるとは結論づけない。

閉じていない属性の検出には、そのdirectiveが属性を必要とするという意味上の規則を利用する。属性を省略できるdirectiveの直後にある`{`を、著者が意図した属性と通常テキストのどちらとして扱うかは、Generic Directivesの解析結果だけでは決定できない。

また、AST全体をClojureScriptの永続データ構造へ変換せず、JavaScriptオブジェクトのまま扱う。内部表現、採用ライブラリ、変換パイプライン、著者向け記法は、検証結果をもとに別途決定する。

## 参照資料

- [Generic DirectivesとMarkdown ASTに関する調査](../../markdown-ast.md)
- [Generic directives/plugins syntax](https://talk.commonmark.org/t/generic-directives-plugins-syntax/444)
- [`mdast-util-from-markdown` 2.0.3](https://github.com/syntax-tree/mdast-util-from-markdown/tree/2.0.3)
- [`mdast-util-to-markdown` 2.1.2](https://github.com/syntax-tree/mdast-util-to-markdown/tree/2.1.2)
- [`micromark-extension-directive` 4.0.0](https://github.com/micromark/micromark-extension-directive/tree/4.0.0)
- [`mdast-util-directive` 3.1.0](https://github.com/syntax-tree/mdast-util-directive/tree/3.1.0)
