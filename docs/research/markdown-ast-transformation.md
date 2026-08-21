# Markdown AST変換と出力方式に関する調査

- 状態: 調査済み
- 調査日: 2026-08-21
- 検証環境:
  - OS: macOS 26.5.2
  - Node.js: 24.19.0
  - npm: 11.17.0
  - JDK: Temurin 21.0.12
  - ClojureScriptビルド: shadow-cljs 3.4.12
  - Markdown解析: `mdast-util-from-markdown` 2.0.3
  - Markdown直列化: `mdast-util-to-markdown` 2.1.2
  - directive構文拡張: `micromark-extension-directive` 4.0.0
  - directive用mdast拡張: `mdast-util-directive` 3.1.0
  - 脚注構文拡張: `micromark-extension-gfm-footnote` 2.1.0
  - 脚注用mdast拡張: `mdast-util-gfm-footnote` 2.1.0
  - HTML変換: `@vivliostyle/vfm` 2.7.0
  - HTML検証: `node-html-parser` 9.0.1

## 背景

[Generic DirectivesとMarkdown ASTに関する調査](markdown-ast.md)では、Generic Directivesの3種類をmdastへ変換し、名前、属性、子要素、入力位置をClojureScriptから扱えることを確認した。JavaScriptオブジェクトのままASTを変更し、Markdownへ意味を保って戻す構成も成立した。

一方、その調査ではdirectiveの意味検証や用途別構造への変換を行っておらず、直列化したMarkdownをVFMが処理できるか、通常のMarkdownやVFM固有の機能を壊さないかも未確認だった。また、clonoの主要な出力候補として、VFM向けMarkdownと完成したHTMLのどちらを採用するか判断する材料が不足していた。

今回の調査では、単一のMarkdown文字列を対象として、解析、意味検証、AST変換、Markdown直列化、VFMによるHTML変換を一つのパイプラインで実行した。

## 検証方針

主要な出力候補には、標準Markdownを維持し、必要な箇所だけraw HTMLを含むVFM向けMarkdownを使用する。MarkdownからHTMLへの変換はVFMへ委譲し、clono側では完成したHTMLを生成しない。

Container、leaf、textの各directiveを一つずつ確認するため、次の仮変換を使用した。

| 想定用途 | 仮のdirective | 仮の出力 |
| --- | --- | --- |
| 右寄せ | Container directiveの`align` | `div.text-align-right` |
| 強制改ページ | Leaf directiveの`page-break` | `div.page-break[aria-hidden="true"]` |
| 索引指定 | Text directiveの`index` | `span.index-marker[data-index-reading]` |

これらの記法、属性、class名、HTML要素、診断文は、実用途を模して出力方式を検証するための仮定である。clonoの仕様として採用したものではない。

未知のdirectiveには文字列の完全一致を要求せず、名前、属性、内容を再解析できる意味的保持を求める。既知のdirectiveに意味上の誤りがある場合は、文書全体の変換を失敗させ、部分的な出力Markdownを返さない。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/markdown-ast-transformation/)をリポジトリ内へ保存している。このfixtureはclono本体から独立したClojureScriptおよびnpmプロジェクトであり、`package.json`と`package-lock.json`によって依存関係を固定する。

検証したパイプラインは次のとおりである。

```text
入力Markdown
  → mdast解析
  → 既知directiveの意味検証
  → AST変換
  → VFM向けMarkdown
  → VFM 2.7.0
  → HTML検証
```

### 解析と直列化

Generic Directivesの解析と直列化には、前回の調査と同じ低レベルAPIを使用した。VFMの脚注記法をmdastとして扱うため、次の拡張も追加した。

- `micromark-extension-gfm-footnote`の`gfmFootnote()`: 脚注参照と定義のトークン化
- `mdast-util-gfm-footnote`の`gfmFootnoteFromMarkdown()`: 脚注用mdastノードへの変換
- `mdast-util-gfm-footnote`の`gfmFootnoteToMarkdown()`: 脚注用mdastノードのMarkdown直列化

clono側を模した解析、意味検証、AST変換、Markdown直列化では、`remark-parse`、`remark-directive`、`unified`を直接使用していない。

VFM 2.7.0は推移的依存関係として`remark-parse`と`unified`を含む。これらは、clono側の処理ではなく、後段のVFMによるHTML変換で使用される。

### 意味検証

既知のdirectiveについて、ノード型と属性をAST変換前に検証した。

- `align`はContainer directiveであり、`position="right"`を持つ
- `page-break`はLeaf directiveであり、属性を持たない
- `index`はText directiveであり、空でない`reading`属性を持つ

不正な入力には、次の情報を持つ診断を生成した。

- 入力ファイル名
- 開始位置の行
- 開始位置の列
- directive名
- 問題の説明

fixtureでは4件の不正な既知directiveと、一つの未知のdirectiveを同じ原稿へ置いた。4件の既知directiveだけが入力順に診断され、未知のdirectiveに対する診断は生成されなかった。

意味検証はASTの変更前に文書全体へ実行した。診断が一つでもある場合、変換処理は`output`を`nil`として失敗し、部分的に変換したMarkdownを返さなかった。

これは文字列変換関数の原子性を確認するものである。既存ファイルを残さないための一時ファイルや安全な置換は、ファイルI/Oを実装する段階の責務となる。

### AST変換

Container directiveは、開始raw HTMLノード、元の子ノード、終了raw HTMLノードへ置き換えた。

```markdown
<div class="text-align-right">

右寄せする`インラインコード`付きの段落です。脚注も参照します[^note]。

2026年8月21日\
Thunder Claw

</div>
```

子ノードをMarkdownのまま残すことで、段落、インラインコード、強制改行、脚注のHTML変換をVFMへ委譲した。VFM変換後には、外側の`div.text-align-right`の中へ二つの`p`が生成された。

Leaf directiveは、内容を持たない一つのraw HTMLノードへ置き換えた。

```html
<div class="page-break" aria-hidden="true"></div>
```

Text directiveは、開始raw HTMLノード、元のインライン子ノード、終了raw HTMLノードへ置き換えた。

```markdown
これは<span class="index-marker" data-index-reading="さくいんこうもく">索引項目</span>です。
```

動的なHTML属性値は、`&`、`"`、`<`、`>`をHTMLエスケープしてから出力した。自動検証では`A&B`を`A&amp;B`としてMarkdownへ出力し、VFM変換後のHTML属性値を`A&B`として取得できることを確認した。

### VFMとの結合

正常な原稿を変換したVFM向けMarkdownを、VFM 2.7.0の`dpub`脚注モードでHTMLへ変換した。次の内容が同じHTMLに保持された。

- 通常の見出し、段落、強調、リンク
- `div.text-align-right`と、その中の二つの段落、インラインコード、脚注参照
- `div.page-break`のclassと`aria-hidden`
- `span.index-marker`のclass、読み、表示語
- コードフェンス内のdirectiveらしい文字列
- `doc-noteref`の脚注参照
- `doc-footnote`の脚注本文
- 脚注内のインラインコードとリンク

これにより、用途別のraw HTMLと、VFMへ委譲する標準Markdownおよび脚注を一つの出力Markdownで共存させられることを確認した。

## 検証結果

### VFM向けMarkdown

既知のdirectiveだけをraw HTMLノードへ変換し、文書全体をMarkdownへ直列化する方式で、VFMが処理できる出力を生成できた。Container directiveの子要素とText directiveの表示内容をMarkdownとして残せるため、clonoがMarkdownからHTMLへの変換全体を再実装する必要はない。

VFM向けMarkdownは、clonoの主要な出力形式として技術的に成立する有力候補である。完成したHTMLを主要出力とする必要性は、今回の範囲では確認されなかった。

### 通常のMarkdownとコードフェンス

変換後のMarkdownを再解析しても、見出し、強調、リンク、コード、脚注のmdast構造が保持された。コードフェンス内にある`align`、`page-break`、`index`に見える文字列はdirectiveノードにならず、clonoの変換対象にもならなかった。VFM変換後もコードとして出力された。

ASTのノード型を利用して変換対象を選ぶことで、前身の正規表現による文字列置換で問題となり得た、コード例の誤変換を避けられる。

### 脚注

Generic Directivesの拡張だけでは、VFMの脚注を専用mdastノードとして保持する契約にならない。脚注構文とmdastの拡張を解析および直列化の両方へ追加することで、`footnoteReference`と`footnoteDefinition`として往復できた。

VFMが扱うMarkdown機能を変換前後で保持するには、clono側のパーサーと直列化処理も、必要なMarkdown拡張を明示的に共有する必要がある。Generic Directivesだけを追加したCommonMark相当の構成で十分とは限らない。

### 未知のdirective

未知の`third-party` directiveは変換せず、`mdast-util-directive`でMarkdownへ再直列化した。再解析後も名前、属性、本文を取得でき、意味的保持の要件を満たした。空白や引用符など、元の文字列表現との完全一致は要求していない。

この保証はclonoが生成するVFM向けMarkdownまでを対象とする。VFM 2.7.0自身はGeneric Directivesを解釈せず、未知のdirectiveをそのままVFMへ渡すと記法が通常テキストとしてHTMLへ現れる。別のツールに未知のdirectiveを処理させる場合は、clonoの後、VFMの前に実行する必要がある。

未知のdirectiveの内部に既知のdirectiveが入れ子になった場合の変換方針は、今回決定していない。

### 診断と入力位置

パーサーが既知のdirectiveノードとして認識した入力では、mdastの`position`からファイル名、行、列を持つ診断を生成できた。属性不足と誤ったdirective種別を、変換処理とは分離して検出できた。

構文自体が壊れ、directiveノードとして認識されない入力の診断は今回の対象外である。また、変換後のMarkdownと元の入力を対応付けるsource mapは生成していない。

### 低レベルAPI

解析、意味検証、再帰的なAST変換、Markdown直列化を、ClojureScriptからmdastとmicromarkの低レベルAPIを直接利用して実装できた。今回の規模では、`unified`またはremarkの処理パイプラインを追加しなくても、一方向の変換処理を構成できた。

一方、将来のプラグインが変換処理を登録する方法、文書全体の状態を処理間で受け渡す方法、既存のremarkプラグインを利用する必要性は検証していない。今回の結果だけでは、低レベルAPIを直接採用するか、`unified`およびremarkを採用するかを決定しない。

## 評価

次の構成は、clonoの最小変換パイプラインとして技術的に成立する。

1. Generic Directivesと必要なMarkdown拡張を含む入力をmdastへ解析する
2. AST全体を走査し、既知のdirectiveを入力位置付きで意味検証する
3. エラーがなければ、既知のdirectiveを用途別のraw HTMLノードへ変換する
4. 未知のdirectiveと変換対象外のMarkdownを保持する
5. ASTをVFM向けMarkdownへ直列化する
6. MarkdownからHTMLへの変換と組版をVivliostyleへ委譲する

このパイプラインは、構文認識、意味検証、AST変換、直列化を分離できる。既知の不正な入力で出力を生成せず、標準MarkdownとVFMの機能を後段へ委譲できるため、プロジェクト憲章の構造化、診断可能性、Vivliostyleとの責務分担に適合する。

今回の検証は、VFM向けMarkdownを主要出力とする判断を支持する。ただし、記法、採用ライブラリ、内部表現、変換規則の登録方法、出力契約、診断形式の正式な採用はADRで行う。

## セキュリティと依存関係

2026年8月21日にfixtureで`npm audit`を実行した結果、開発依存関係のVFM 2.7.0を経由して、3件のmoderateと3件のhighが報告された。報告対象には、VFMの推移的依存関係である`refractor`、`prismjs`、`remark-parse`、`trim`、`valibot`が含まれる。

`npm audit --omit=dev`では0件であり、clono側の変換を模したproduction依存関係には報告されなかった。これは今回固定したlockfileに対する調査時点の結果であり、将来の本体依存関係の安全性を保証するものではない。

VFM 2.7.0は、Vivliostyle CLI 11.1.0が使用するバージョンとの結合を再現するため、検証専用の開発依存関係として固定している。fixtureは外部から受け取った信頼できない入力の処理には使用しない。監査結果だけを解消する目的で、検証対象と異なるVFMへ更新または変更しない。

## 未確認・未決定事項

- 著者向けのdirective名、属性、内容モデル
- 変換後のclass名、HTML要素、属性
- 低レベルAPIと`unified`およびremarkの最終選択
- ASTの走査、変換規則の登録、状態共有に使用する内部API
- 未知のdirective内部にある既知のdirectiveの扱い
- 文字列表現まで含む未知のdirectiveの完全保持
- Generic Directives自体の構文エラーの検出
- 診断コード、重要度、最終的な表示形式
- 変換後のsource map
- ファイル出力の原子性
- 複数原稿と文書全体の情報収集
- 生成Markdownの整形方針
- 完成したHTMLを別の出力形式として提供する必要性

これらは、ADR、最小変換パイプライン、最初の独自記法、CLI、複数原稿対応の各段階で、必要な範囲だけを決定または検証する。

## 再現方法

[検証用fixtureのREADME](fixtures/markdown-ast-transformation/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm test`で自動検証できる。`npm run inspect`を実行すると、正常な入力から生成されたVFM向けMarkdownを確認できる。

不正な入力を指定してinspectorを実行すると、4件の位置付き診断を標準エラーへ出力し、終了コード1で終了する。

## 再調査する条件

- mdast、micromark、VFM関連ライブラリのメジャーバージョンを更新する場合
- 正式な著者向け記法または出力構造で、今回の変換方式を利用できない場合
- Thunder Clawの実原稿で使用するVFM機能が、解析または直列化によって失われる場合
- 必要な構文誤りを入力位置付きで診断できない場合
- 未知のdirectiveを必要な範囲で保持できない場合
- プラグインまたは文書全体の処理に、今回の低レベルAPI構成が適さない場合
- VFM向けMarkdownでは後段ツールとの連携要件を満たせない場合
- VFM 2.7.0の監査結果を含む依存関係が、fixtureの利用方法に対して許容できなくなった場合

## 参照資料

- [プロジェクト憲章](../project-charter.md)
- [0002: Node.js向けClojureScript開発基盤を採用する](../decisions/0002-adopt-nodejs-clojurescript-development-platform.md)
- [Generic DirectivesとMarkdown ASTに関する調査](markdown-ast.md)
- [検証用fixture](fixtures/markdown-ast-transformation/)
- [Generic directives/plugins syntax](https://talk.commonmark.org/t/generic-directives-plugins-syntax/444)
- [`mdast-util-from-markdown` 2.0.3](https://github.com/syntax-tree/mdast-util-from-markdown/tree/2.0.3)
- [`mdast-util-to-markdown` 2.1.2](https://github.com/syntax-tree/mdast-util-to-markdown/tree/2.1.2)
- [`micromark-extension-directive` 4.0.0](https://github.com/micromark/micromark-extension-directive/tree/4.0.0)
- [`mdast-util-directive` 3.1.0](https://github.com/syntax-tree/mdast-util-directive/tree/3.1.0)
- [`micromark-extension-gfm-footnote` 2.1.0](https://github.com/micromark/micromark-extension-gfm-footnote/tree/2.1.0)
- [`mdast-util-gfm-footnote` 2.1.0](https://github.com/syntax-tree/mdast-util-gfm-footnote/tree/2.1.0)
- [VFM 2.7.0](https://github.com/vivliostyle/vfm/tree/v2.7.0)
