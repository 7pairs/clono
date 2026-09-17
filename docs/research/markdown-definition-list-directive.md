# Generic Directivesと定義リストのMarkdown ASTに関する調査

- 状態: 調査済み
- 調査日: 2026-09-16
- 最終更新日: 2026-09-17
- 検証環境:
  - OS: macOS 26.6.2
  - Node.js: 24.19.0
  - npm: 11.17.0
  - JDK: Temurin 21.0.12
  - ClojureScriptビルド: shadow-cljs 3.4.12
  - Markdown解析: `mdast-util-from-markdown` 2.0.3
  - Markdown直列化: `mdast-util-to-markdown` 2.1.2
  - directive構文拡張: `micromark-extension-directive` 4.0.0
  - directive用mdast拡張: `mdast-util-directive` 3.1.0
  - HTML変換: `@vivliostyle/vfm` 2.7.0
  - PDF生成: `@vivliostyle/cli` 11.1.0
    - CLIが使用するVivliostyle.js 2.44.1
  - HTML検証: `node-html-parser` 9.0.1
  - PDF検証: `mupdf` 1.28.0

## 背景

[Vivliostyleの基本表現機能に関する調査](vivliostyle-basic-presentation.md)では、VFM 2.7.0に定義リスト用の標準記法がないことと、原稿へ直接記述した`dl`、`dt`および`dd`をVFMが保持できることを確認した。`dd`内のインラインコード、強調およびリンクもHTMLへ変換され、VivliostyleとCSSによって用語と説明を定義リストとして表示できた。

一方、既存の検証対象は一つの用語と一つの説明だけであり、著者向け記法は決定していなかった。Thunder Clawの次回作では、enumの各値へ二文以上の説明を付ける用途などで定義リストを使用する可能性が高い。この用途では、複数の用語と説明を一つのリストとして記述でき、用語をインラインコードで表現できる必要がある。初期仕様では各項目を一つの用語と一つの説明へ限定するが、将来は複数の用語が同じ説明を共有する可能性もある。

著者向け記法を決定する前に、Generic Directivesの入れ子によってリスト全体と各項目を表現できること、ASTから用語と説明を個別に取得できること、およびVFM向けMarkdownからPDFまで必要な構造を保持できることを追加検証した。

## 調査目的

- 入れ子にしたContainer directiveが、リスト全体と複数の項目を区別できるmdastになるか確認する
- 各項目の用語と説明を明示的なAST境界から個別に取得できるか確認する
- 用語に記述したインラインコードを専用のmdastノードとして取得できるか確認する
- 将来、一つの説明へ複数の用語を対応させる場合も記法を変更せず表現できるか確認する
- 説明の段落にある強調、インラインコードおよびリンクを保持できるか確認する
- 複数項目を一つの`dl`と項目ごとの`div`、`dt`および`dd`へ変換できるか確認する
- 変換後MarkdownをVFMへ渡しても、定義リストの意味構造と説明内のMarkdownが保持されるか確認する
- 自然なページ境界で項目ごとのラッパーへ指定した`break-inside: avoid`が用語と説明の分断を防げるか確認する

この調査は、定義リストの正式な著者向け記法、内容モデル、診断文、class名または基盤CSSを決定するものではない。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/markdown-definition-list-directive/)をリポジトリ内へ保存している。このfixtureはclono本体から独立したClojureScriptおよびnpmプロジェクトであり、`package.json`と`package-lock.json`によって依存関係を固定する。

解析と直列化には、clono本体が使用するGeneric Directivesの低レベルAPIを組み合わせた。

- `micromark-extension-directive`と`mdast-util-directive`
- `mdast-util-from-markdown`と`mdast-util-to-markdown`

候補となる著者向け記法には、リスト全体を表す`definition-list` Container directive、各項目を表す`definition` Container directive、および用語を表す`term` Leaf directiveを使用した。内側のContainer directiveを3個のコロンで囲むため、外側には4個のコロンを使用した。

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

検証したパイプラインは次のとおりである。

```text
候補記法を含むMarkdown
  → Generic Directivesを有効にしたmdast解析
  → 候補となる定義リスト構造へのAST変換
  → VFM向けMarkdownへの直列化
  → 直列化したMarkdownの再解析
  → VFM 2.7.0によるHTML変換
  → Vivliostyle CLI 11.1.0によるPDF生成
```

## 検証結果

### 入れ子のdirectiveとAST構造

外側の候補記法は、`name`が`definition-list`である一つの`containerDirective`として解析された。その`children`には、`name`が`definition`である二つの`containerDirective`が保持された。

各`definition`の`children`は、用語を表す`term` Leaf directiveと、説明を表す通常段落を含んだ。ノード種別とdirective名の両方が明示されるため、用語を説明の段落と区別できる。

一つ目の項目は、概ね次のmdastとなった。

```json
{
  "type": "containerDirective",
  "name": "definition",
  "attributes": {},
  "children": [
    {
      "type": "leafDirective",
      "name": "term",
      "attributes": {},
      "children": [
        {
          "type": "inlineCode",
          "value": "READY"
        }
      ]
    },
    {
      "type": "paragraph",
      "children": [
        {
          "type": "text",
          "value": "処理を開始できる"
        },
        {
          "type": "strong"
        },
        {
          "type": "link",
          "url": "https://example.com/state"
        }
      ]
    }
  ]
}
```

この構造により、外側のdirective直下に許可する項目、各項目の用語および説明の個数と種類を、入力位置付きのASTから検査できる見通しが立った。`term`は独立した子ノードであるため、初期仕様では一つだけに制限し、将来は個数制約を緩めて複数の用語を許可できる。

### 用語と説明内のMarkdown

各`term`の用語にある`` `READY` ``は`inlineCode`ノード、装飾のない`DONE`は`text`ノードとして保持された。このため、enumの値などをインラインコードで表示する用語と、通常のテキストで表示する用語を区別できる。

説明内の`**待機状態**`、リンクおよび`` `0` ``は、それぞれ`strong`、`link`および`inlineCode`ノードとして解析された。候補構造へ変換してVFM 2.7.0へ渡した後も、それぞれ`strong`、`a`および`code`要素になり、リンク先URLも保持された。

追加入力では、一つの`definition`直下へ`::term[一塁手]`と`::term[二塁手]`を並べた。両方が個別の`leafDirective`として解析され、二つの`dt`と一つの共有する`dd`へ変換できた。これにより、将来の複数用語対応は記法の変更ではなく個数制約の緩和として実現できる見通しが立った。

### VFM向けMarkdownへの変換

候補となる変換では、外側のdirectiveを一つの`dl.clono-definition-list`へ変換し、各`definition`を`div.clono-definition-item`で囲んだ`dt`と`dd`へ変換した。各`term`の用語は`dt`へ、説明の元の`paragraph`ノードは一つの`dd`内へ配置した。

直列化後のMarkdownは次の構造となった。

```markdown
<dl class="clono-definition-list">

<div class="clono-definition-item">
<dt><code>READY</code></dt>
<dd>

処理を開始できる**待機状態**。詳細は[状態遷移の仕様](https://example.com/state)を参照する。

</dd>
</div>

<div class="clono-definition-item">
<dt>DONE</dt>
<dd>

処理が正常に完了した状態を表す。終了コードは`0`となる。

</dd>
</div>

</dl>
```

変換後MarkdownをGeneric Directives付きで再解析しても、候補記法のdirectiveは残らなかった。VFM 2.7.0は、一つの`dl`、その直下にある二つの項目用`div`、各項目の一つの`dt`と一つの`dd`を保持した。`dd`内のMarkdown段落も`p`要素へ変換された。

項目ごとの`div`を置くことで、用語と説明を一組としてCSSの対象にできる。定義リスト全体を一つの`dl`として保ちながら、項目間の余白や改ページ制御を項目単位で指定できる。

### Vivliostyleによる項目単位の改ページ

PDF検証では、同じ変換後Markdownから次の二つのPDFを生成した。

- 項目ラッパーへ分断防止を指定しない基準版
- 項目ラッパーへ`break-inside: avoid`を指定する保護版

ページ末尾の残り領域は、固定したページ寸法、行高およびfixture専用の空要素によって、用語一行だけが収まり説明は収まらない状態にした。基準版では、`READY`の用語が1ページ目、説明が2ページ目へ分断された。保護版では、`READY`の用語と説明がともに2ページ目へ移動し、後続の`DONE`も用語と説明が同じページに配置された。

生成した両方のPDFを画像化して目視した結果、欠落、重なりまたは意図しない文字切れは見つからなかった。この比較から、Vivliostyle.js 2.44.1では`break-inside: avoid`だけで、ページ末尾に収まらない定義項目全体を次ページへ送れることを確認した。

## 評価

`definition-list`と`definition`のContainer directiveに`term` Leaf directiveを組み合わせる候補記法は、clonoの現在のMarkdown変換パイプラインで技術的に成立する。新しい解析ライブラリや、Markdown文字列を再解釈する独自パーサーを追加せず、リスト全体、複数の項目、用語および説明をASTの境界として取得できる。

用語を`term` Leaf directiveへ置くことで、用語と説明の境界および個数を直接検査でき、enumの値をインラインコードとして記述できる。説明は元の段落ノードを`dd`内へ残せるため、clonoがインラインMarkdownをHTMLへ変換し直す必要はない。clonoは定義リストの外側の構造を生成し、説明内のMarkdownからHTMLへの変換をVFMへ委譲できる。

項目ごとの`div`を生成する構造は、複数項目を一つの`dl`へまとめながら、用語と説明へ一組としてCSSを適用できる。印刷物で問題になり得る項目途中の改ページには、項目ラッパーへ`break-inside: avoid`を指定することで対応できる。

## 仕様策定への示唆

今回の結果から、初期仕様では次の契約を採用できる見通しが立った。

- リスト全体にはContainer directiveの`definition-list`を使用する
- `definition-list`直下の各項目にはContainer directiveの`definition`を使用する
- `definition`直下のLeaf directive `term`を用語、その後の本文を説明として扱う
- 一つの定義リストへ複数の項目を記述できる
- 初期仕様では各`definition`へ一つの`term`だけを許可する
- 将来は`term`の個数制約を緩めることで、複数の用語が一つの説明を共有できる
- 用語では少なくとも通常テキストとインラインコードを許可する
- 初期仕様の説明は、一つの通常段落へ限定できる
- 説明内の強い強調、インラインコードおよびリンクを構造を壊さずVFMへ渡す
- 一つの`dl`の内側で、各項目を`div`、`dt`および`dd`へ変換する
- 項目途中の改ページを避けるため、項目ラッパーへ`break-inside: avoid`を適用する
- 定義リスト固有の外観は利用者テーマへ委譲する

正式な記法を決定する際は、外側の`definition-list`と内側の`definition`だけを必要な入れ子として許可し、それ以外のdirective、複数段落およびブロック要素を初期仕様で禁止するのが単純である。空のリスト、用語または説明と、許可しない属性も、AST変換前の意味検証で診断できる。

`break-inside: avoid`は、定義項目を一組として保持するための機能的な基盤CSS候補にできる。字下げ、余白、罫線または背景などの外観は利用者テーマへ委譲できる。

## 成立条件と未確認事項

今回の結果は、Generic Directivesの解析および直列化拡張を同じパイプラインへ登録し、内側より長いコロン列で外側のContainer directiveを囲むことを前提とする。

次の事項は検証していない。

- 候補記法の配置、属性または内容モデルに対する意味検証と診断
- 空の定義リスト、空の用語または空の説明
- 用語内の強調、リンク、raw HTMLまたはdirective
- 説明内の複数段落、リスト、コードブロック、画像、脚注、raw HTMLまたはdirective
- 定義リストまたは項目の入れ子
- 一ページより長い定義項目へ`break-inside: avoid`を指定した場合の挙動
- 製品用テーマで使用する字下げ、余白、罫線または背景などの外観
- スクリーンリーダーなどによるアクセシビリティ上の読み上げ

今回の結果をもとに決定した正式な著者向け記法、診断契約および基盤CSSは、[clono著者向け記法](../specifications/authoring-syntax.md#定義リスト記法)を正本とする。

## 再現方法

[検証用fixtureのREADME](fixtures/markdown-definition-list-directive/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm run verify`でAST、VFM変換後HTMLおよびPDFを自動検証できる。

## 再調査する条件

- `mdast-util-from-markdown`、`mdast-util-to-markdown`またはGeneric Directivesの拡張をメジャーバージョンアップする場合
- 正式な著者向け記法または出力構造で、今回の変換方式を利用できない場合
- 用語または説明へ、初期仕様に含まれないMarkdown要素を許可する場合
- 複数段落、リスト、コードブロック、画像または脚注を説明へ含める場合
- 製品用テーマとの統合によって`break-inside: avoid`の効果が変わる場合
- VFMがraw HTML内のMarkdownを処理する方法を変更した場合
- Thunder Clawの実原稿または製品用テーマで、用語と説明が意図しない位置へ分断される場合

## 参照資料

- [プロジェクト憲章](../project-charter.md)
- [Vivliostyleの基本表現機能に関する調査](vivliostyle-basic-presentation.md)
- [Markdown AST変換と出力方式に関する調査](markdown-ast-transformation.md)
- [Generic DirectivesとmdastによるMarkdown変換パイプラインのADR](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [検証用fixture](fixtures/markdown-definition-list-directive/)
- [clono著者向け記法](../specifications/authoring-syntax.md#定義リスト記法)
- [Generic directives/plugins syntax](https://talk.commonmark.org/t/generic-directives-plugins-syntax/444)
- [`mdast-util-from-markdown` 2.0.3](https://github.com/syntax-tree/mdast-util-from-markdown/tree/2.0.3)
- [`mdast-util-to-markdown` 2.1.2](https://github.com/syntax-tree/mdast-util-to-markdown/tree/2.1.2)
- [`micromark-extension-directive` 4.0.0](https://github.com/micromark/micromark-extension-directive/tree/4.0.0)
- [VFM 2.7.0](https://github.com/vivliostyle/vfm/tree/v2.7.0)
