# Generic DirectivesとGFM表のMarkdown ASTに関する調査

- 状態: 調査済み
- 調査日: 2026-09-08
- 最終更新日: 2026-09-08
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
  - 表構文拡張: `micromark-extension-gfm-table` 2.1.1
  - 表用mdast拡張: `mdast-util-gfm-table` 2.0.0
  - HTML変換: `@vivliostyle/vfm` 2.7.0
  - HTML検証: `node-html-parser` 9.0.1

## 背景

[Vivliostyleの表ID・キャプション・連番・相互参照に関する調査](vivliostyle-table-references.md)では、Markdown表をIDとclassを持つ`figure`で囲み、その後ろへ`figcaption`を置くことで、表の下にキャプションを表示できることを確認した。CSSカウンターによる章ごとの表番号、同一原稿および原稿間の参照、番号とキャプションの取得、PDF内部リンクも、VFMとVivliostyleへ委譲できる。

```markdown
<figure class="numbered-table" id="table-runtime">

| 項目 | 値 |
| --- | --- |
| 言語 | `ClojureScript` |

<figcaption id="table-runtime-caption">実行環境</figcaption>
</figure>
```

一方、この調査ではclonoによる変換後を模した構造を原稿へ直接記述しており、Generic DirectivesのContainer directive内にGFM形式のMarkdown表を記述した場合のmdastは確認していなかった。番号付き表の著者向け記法を決定するには、directiveのラベルと表本体を個別に取得できること、元の表ノードを保持したまま参照可能な構造へ変換できること、およびVFM向けMarkdownへの直列化で表内の意味を壊さないことを確認する必要があった。

## 調査目的

- Container directiveの内側に記述したGFM表が、どのようなmdastになるか確認する
- directiveのラベル、属性および単一の表本体を個別に取得できるか確認する
- 表の文字寄せとインラインMarkdownをAST変換前に検査できるか確認する
- 元の`table`ノードをraw HTMLで囲み、VFM向けMarkdownへ直列化できるか確認する
- 直列化後のMarkdownを再解析しても、表の構造と意味が保持されるか確認する
- VFM変換後に、表の下のキャプション、文字寄せおよび表内のMarkdownが保持されるか確認する

この調査は、番号付き表の最終的な入力契約、診断文、class名、ID、参照またはCSSを決定するものではない。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/markdown-table-directive/)をリポジトリ内へ保存している。このfixtureはclono本体から独立したClojureScriptおよびnpmプロジェクトであり、`package.json`と`package-lock.json`によって依存関係を固定する。

解析と直列化には、clono本体が使用するGeneric DirectivesとGFM表の低レベルAPIを組み合わせた。

- `micromark-extension-directive`と`mdast-util-directive`
- `micromark-extension-gfm-table`と`mdast-util-gfm-table`
- `mdast-util-from-markdown`と`mdast-util-to-markdown`

候補となる著者向け記法には、Container directiveの`table`を使用した。

```markdown
:::table[実行環境]{#runtime}

| 項目 | 値 | 備考 |
| :--- | :---: | ---: |
| 言語 | `ClojureScript` | **必須** |
| 文書 | [VFM](https://vivliostyle.github.io/vfm/) | *推奨*、~~旧形式ではない~~ |

:::
```

検証したパイプラインは次のとおりである。

```text
候補記法を含むMarkdown
  → Generic DirectivesとGFM表を有効にしたmdast解析
  → 候補となるfigure構造へのAST変換
  → VFM向けMarkdownへの直列化
  → 直列化したMarkdownの再解析
  → VFM 2.7.0によるHTML変換
```

## 検証結果

### directiveと表のAST構造

候補記法は`containerDirective`として解析され、`name`に`table`、`attributes.id`に`runtime`を保持した。

directiveの`children`には、ラベルを表す`paragraph`と表本体を表す`table`が兄弟として格納された。ラベルの`paragraph.data.directiveLabel`は`true`であるため、表本体と区別できる。

```json
{
  "type": "containerDirective",
  "name": "table",
  "attributes": {
    "id": "runtime"
  },
  "children": [
    {
      "type": "paragraph",
      "data": {
        "directiveLabel": true
      },
      "children": [
        {
          "type": "text",
          "value": "実行環境"
        }
      ]
    },
    {
      "type": "table",
      "align": [
        "left",
        "center",
        "right"
      ]
    }
  ]
}
```

この構造により、ラベルをキャプションとして取得し、`table`以外の本文がないことや、表本体が一個だけであることをAST変換前に検査できる。入力位置も各ノードの`position`に保持されるため、内容モデルに違反した位置を診断へ使用できる。

### 表内のMarkdownと文字寄せ

左寄せ、中央寄せ、右寄せは、`table.align`へそれぞれ`left`、`center`、`right`として保持された。

表のセルに記述した次のインラインMarkdownは、専用のmdastノードとして取得できた。

| 入力 | mdastノード |
| --- | --- |
| `` `ClojureScript` `` | `inlineCode` |
| `**必須**` | `strong` |
| `*推奨*` | `emphasis` |
| `[VFM](https://vivliostyle.github.io/vfm/)` | `link` |

一方、clono本体の現在の解析構成はGFMの取り消し線専用拡張を使用していない。`~~旧形式ではない~~`は`delete`ノードにならず、`~~`を含む`text`ノードとして保持された。

この違いは、取り消し線記法が失われることを意味しない。Markdownへの直列化と再解析後も`~~旧形式ではない~~`は`text`ノードの値として保持され、VFM 2.7.0によるHTML変換では`del`要素になった。ただし、clonoは現在のASTだけでは、取り消し線と同じ文字列を持つ通常テキストを区別して個別に検証できない。

### VFM向けMarkdownへの変換

候補となる変換では、Container directiveを次の三要素へ置き換えた。

1. `figure`を開始するraw HTMLノード
2. 入力から取得した元の`table`ノード
3. `figcaption`と`figure`の終了要素を持つraw HTMLノード

直列化後のMarkdownは、概ね次の構造となった。

```markdown
<figure class="clono-numbered-table" id="table-runtime">

| 項目 | 値 | 備考 |
| :- | :-: | -: |
| 言語 | `ClojureScript` | **必須** |
| 文書 | [VFM](https://vivliostyle.github.io/vfm/) | *推奨*、~~旧形式ではない~~ |

<figcaption class="clono-table-caption" id="table-runtime-caption">実行環境</figcaption>
</figure>
```

実際の直列化結果では、各列の内容に応じて空白と区切り行のハイフン数が調整された。このため、入力Markdownとの文字列一致は保証できない。一方、直列化したMarkdownを再解析すると、ルート直下のノードは開始raw HTML、`table`、終了raw HTMLの順になり、文字寄せと表内のMarkdownも変換前と同じ意味を保持した。

### VFMとの結合

変換後MarkdownをVFM 2.7.0へ渡した結果、IDを持つ`figure`の直下に`table`と`figcaption`が生成された。`figcaption`はHTMLの子要素順でも`table`より後ろにあり、表の下へキャプションを配置できることを確認した。

VFM変換後には、次の内容も保持された。

- `th`と`td`の`align`属性による左寄せ、中央寄せ、右寄せ
- `code`によるインラインコード
- `strong`による強い強調
- `em`による強調
- `a`によるリンクとURL
- `del`による取り消し線

## 評価

Container directiveの`table`でGFM表を囲む候補記法は、clonoの現在のMarkdown変換パイプラインで技術的に成立する。新しい解析ライブラリを導入せず、既存のGeneric DirectivesとGFM表の拡張を組み合わせて、ラベル、ID、表本体、文字寄せおよび表内の主要なインラインMarkdownを扱える。

元の`table`ノードをそのまま変換結果へ残せるため、clonoが表の行、セルまたはインラインMarkdownをHTMLへ変換し直す必要はない。clonoは番号付き表に必要な外側の構造とキャプションを生成し、Markdown表からHTMLの`table`への変換をVFMへ委譲できる。

表の下へキャプションを置く構造も、候補記法からVFM変換後のHTMLまで一つのパイプラインで成立した。以前のVivliostyle調査で確認したCSSカウンター、参照文字列およびPDF内部リンクの構造と組み合わせられる見通しが立った。

取り消し線も最終HTMLまで意味を保持できる。ただし、専用のmdastノードとして検査する必要が生じた場合は、GFMの取り消し線用拡張を追加するか、許可する入力契約を再検討する必要がある。初期仕様で単にVFMへ保持して渡す場合は、新しい依存関係を追加する必要はない。

## 仕様策定への示唆

今回の結果から、初期仕様では次の契約を採用できる見通しが立った。

- 番号付き表にはContainer directiveの`table`を使用する
- directiveのラベルをキャプション、`id`属性を論理IDとして扱う
- directive直下には単一のGFM `table`ノードだけを許可する
- 番号なし表には通常のMarkdown表を使用する
- 元の`table`ノードをraw HTMLの`figure`と`figcaption`で囲むVFM向けMarkdownを出力する
- キャプションは`table`の後ろへ置き、表の下へ表示する
- 文字寄せ、インラインコード、強い強調、強調およびリンクは、構造を壊さずVFMへ渡す
- 取り消し線はVFMまで記法を保持するが、clonoによる専用ASTノードとしての検証は保証しない

表内脚注はThunder Clawの書籍で将来必要になる可能性があるが、今回のfixtureでは検証していない。初期仕様では許可せず、必要性が具体化した時点でVFMとVivliostyleを含む結合検証を行うのが安全である。

## 成立条件と未確認事項

今回の結果は、Generic DirectivesとGFM表の各拡張を同じ解析および直列化処理へ登録することを前提とする。

次の事項は検証していない。

- 候補記法の属性、ラベル、内容モデルまたは配置に対する意味検証と診断
- 同じ文書または書籍全体にある論理IDとHTML IDの重複検査
- 表参照の解決、プレースホルダーおよび原稿間URLの生成
- 表番号と参照文字列を生成するclono基盤CSS
- 候補記法から生成した構造を含むPDF
- 表内の画像、脚注、raw HTML、directiveまたは強制改行
- HTMLで記述する複雑な表、セル結合またはセル内の複数段落
- 複数ページにまたがる表、表の分割またはヘッダー行の繰り返し
- キャプション内のMarkdown

Vivliostyleへ委譲する表番号、同一原稿および原稿間の参照、PDF内部リンクは、既存の[表ID・キャプション・連番・相互参照に関する調査](vivliostyle-table-references.md)と[参照機能の結合検証](vivliostyle-reference-integration.md)で確認している。正式な著者向け記法、診断、ID、参照およびCSSは、後続の[番号付き表と表参照仕様](../specifications/table-references.md)で定める。

## 再現方法

[検証用fixtureのREADME](fixtures/markdown-table-directive/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm test`で自動検証できる。`npm run inspect`を実行すると、入力から生成された完全なmdastと変換後Markdownを確認できる。

## 再調査する条件

- `mdast-util-from-markdown`、`mdast-util-to-markdown`、Generic DirectivesまたはGFM表の拡張をメジャーバージョンアップする場合
- 正式な著者向け記法または出力構造で、今回の変換方式を利用できない場合
- 表内脚注、画像、raw HTML、directiveまたは現在の内容モデルに含まれない表現へ対応する場合
- 取り消し線をclonoのAST上で個別に識別または検証する必要が生じた場合
- VFMがHTML内のMarkdown、表、文字寄せまたはインラインMarkdownを処理する方法が変更された場合
- Thunder Clawの実原稿で、直列化によって表の意味が変化する事例が見つかった場合

## 参照資料

- [プロジェクト憲章](../project-charter.md)
- [番号付き表と表参照仕様](../specifications/table-references.md)
- [Vivliostyleの表ID・キャプション・連番・相互参照に関する調査](vivliostyle-table-references.md)
- [参照機能の結合検証](vivliostyle-reference-integration.md)
- [Markdown AST変換と出力方式に関する調査](markdown-ast-transformation.md)
- [Generic DirectivesとmdastによるMarkdown変換パイプラインのADR](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [検証用fixture](fixtures/markdown-table-directive/)
- [Generic directives/plugins syntax](https://talk.commonmark.org/t/generic-directives-plugins-syntax/444)
- [`mdast-util-from-markdown` 2.0.3](https://github.com/syntax-tree/mdast-util-from-markdown/tree/2.0.3)
- [`mdast-util-to-markdown` 2.1.2](https://github.com/syntax-tree/mdast-util-to-markdown/tree/2.1.2)
- [`micromark-extension-directive` 4.0.0](https://github.com/micromark/micromark-extension-directive/tree/4.0.0)
- [`mdast-util-directive` 3.1.0](https://github.com/syntax-tree/mdast-util-directive/tree/3.1.0)
- [`micromark-extension-gfm-table` 2.1.1](https://github.com/micromark/micromark-extension-gfm-table/tree/2.1.1)
- [`mdast-util-gfm-table` 2.0.0](https://github.com/syntax-tree/mdast-util-gfm-table/tree/2.0.0)
- [VFM 2.7.0](https://github.com/vivliostyle/vfm/tree/v2.7.0)
