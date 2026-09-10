# Generic DirectivesとコードフェンスのMarkdown ASTに関する調査

- 状態: 調査済み
- 調査日: 2026-09-10
- 最終更新日: 2026-09-10
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
  - HTML検証: `node-html-parser` 9.0.1

## 背景

[VivliostyleのコードリストID・キャプション・連番・相互参照に関する調査](vivliostyle-code-listing-references.md)では、通常のコードフェンスをIDとclassを持つ`figure`で囲み、その前方へ`figcaption`を置くことで、番号付きコードリストに必要なHTML構造を生成できることを確認した。VFMによる構文強調、CSSカウンターによる章ごとのリスト番号、複数ページへの分割、同一原稿および原稿間の参照、PDF内部リンクもVivliostyleへ委譲できる。

````markdown
<figure class="numbered-listing" id="listing-greeting">
<figcaption id="listing-greeting-caption">挨拶を表示する関数</figcaption>

```kotlin
fun greet(name: String): String {
    return "Hello, $name!"
}
```
</figure>
````

一方、この調査ではclonoによる変換後を模した構造を原稿へ直接記述しており、Generic DirectivesのContainer directive内にコードフェンスを記述した場合のmdastは確認していなかった。

番号付きコードリストの著者向け記法を決定するには、directiveのラベル、ID、言語指定、メタ情報およびコード本文を個別に取得できること、コード内のdirective風文字列を誤認識しないこと、元の`code`ノードを保持したまま参照可能な構造へ変換できることを確認する必要があった。

## 調査目的

- Container directiveの内側に記述したコードフェンスが、どのようなmdastになるか確認する
- directiveのラベル、属性および単一のコード本体を個別に取得できるか確認する
- コードフェンスの言語指定を省略でき、指定した場合はASTから取得できるか確認する
- コードフェンスのメタ情報を言語指定とは別に検出できるか確認する
- コード内のdirective風文字列が、追加のdirectiveとして誤認識されないか確認する
- 元の`code`ノードをraw HTMLで囲み、VFM向けMarkdownへ直列化できるか確認する
- 直列化後のMarkdownを再解析しても、言語指定とコード本文が保持されるか確認する
- VFM変換後に、上側のキャプション、コードブロックおよび構文強調が保持されるか確認する

この調査は、番号付きコードリストの最終的な入力契約、診断文、class名、ID、参照またはCSSを決定するものではない。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/markdown-code-listing-directive/)をリポジトリ内へ保存している。このfixtureはclono本体から独立したClojureScriptおよびnpmプロジェクトであり、`package.json`と`package-lock.json`によって依存関係を固定する。

解析と直列化には、clono本体が使用するGeneric Directivesの低レベルAPIを組み合わせた。

- `micromark-extension-directive`と`mdast-util-directive`
- `mdast-util-from-markdown`と`mdast-util-to-markdown`

候補となる著者向け記法には、Container directiveの`listing`を使用した。fixtureには、言語指定のあるコードリストと言語指定のないコードリストを一つずつ記述した。

````markdown
:::listing[A &amp; &quot;B&quot; &lt;C&gt;]{#greeting}

```kotlin
fun greet(name: String): String {
    return "Hello, $name!"
}

// :xref[ignored]{type="listing" format="number"}
```

:::
````

言語指定とメタ情報の区別には、テスト内で次の最小入力も解析した。

````markdown
:::listing[Main function]{#main}

```kotlin title=Main.kt
fun main() = Unit
```

:::
````

検証したパイプラインは次のとおりである。

```text
候補記法を含むMarkdown
  → Generic Directivesを有効にしたmdast解析
  → 候補となるfigure構造へのAST変換
  → VFM向けMarkdownへの直列化
  → 直列化したMarkdownの再解析
  → VFM 2.7.0によるHTML変換
```

## 検証結果

### directiveとコードフェンスのAST構造

候補記法は`containerDirective`として解析され、`name`に`listing`、`attributes.id`に`greeting`を保持した。

directiveの`children`には、ラベルを表す`paragraph`とコード本体を表す`code`が兄弟として格納された。ラベルの`paragraph.data.directiveLabel`は`true`であるため、コード本体と区別できる。

```json
{
  "type": "containerDirective",
  "name": "listing",
  "attributes": {
    "id": "greeting"
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
          "value": "A & \"B\" <C>"
        }
      ]
    },
    {
      "type": "code",
      "lang": "kotlin",
      "meta": null,
      "value": "fun greet(name: String): String {\n    return \"Hello, $name!\"\n}"
    }
  ]
}
```

この構造により、ラベルをキャプション、`attributes.id`を論理IDとして取得し、`code`以外の本文がないことや、コード本体が一個だけであることをAST変換前に検査できる。各ノードの`position`には入力位置も保持されるため、内容モデルに違反した位置を診断へ使用できる。

キャプション内の`&amp;`、`&quot;`、`&lt;`および`&gt;`は解析時に通常の文字へ復号され、一つの`text`ノードに保持された。変換時にこの値をHTMLのテキスト内容として再エスケープすることで、文字の意味を保ったままraw HTMLへ安全に埋め込めた。

### 言語指定とメタ情報

言語を指定したコードフェンスでは、指定値が`code.lang`へ保持された。`kotlin`を指定したfixtureでは、`code.lang`は`kotlin`、`code.meta`は`null`となった。

言語を省略したコードフェンスでは、`code.lang`と`code.meta`はいずれも`null`となった。このため、言語指定を任意とする入力契約を、コード本文の検査と分離して実装できる。

フェンス開始行へ`kotlin title=Main.kt`と記述した場合は、`code.lang`に`kotlin`、`code.meta`に`title=Main.kt`が保持された。メタ情報は言語指定とは独立して検出できるため、初期仕様でメタ情報を許可しない場合も、意味検証によって明示的に診断できる。

### コード内のdirective風文字列

コード本文へ次の文字列を記述しても、追加のdirectiveノードは生成されなかった。

```text
// :xref[ignored]{type="listing" format="number"}
:::listing[not-a-directive]{#ignored}
```

これらは`code.value`の一部として保持され、Markdownへの直列化と再解析後もコード本文のまま残った。したがって、番号付きコードリスト内のサンプルコードがclonoの独自記法と同じ文字列を含んでも、Generic Directivesの構文として誤認識されない。

### VFM向けMarkdownへの変換

候補となる変換では、Container directiveを次の三要素へ置き換えた。

1. `figure`と`figcaption`を持つ開始raw HTMLノード
2. 入力から取得した元の`code`ノード
3. `figure`を閉じるraw HTMLノード

直列化後のMarkdownは、概ね次の構造となった。

````markdown
<figure class="clono-numbered-listing" id="listing-greeting">
<figcaption class="clono-listing-caption" id="listing-greeting-caption">A &amp; &quot;B&quot; &lt;C&gt;</figcaption>

```kotlin
fun greet(name: String): String {
    return "Hello, $name!"
}
```

</figure>
````

直列化したMarkdownを再解析すると、元のContainer directiveはなくなり、`code`ノードの`lang`、`meta`および`value`は変換前と同じ意味を保持した。入力Markdownとの文字列一致ではなく、AST上の意味を往復変換の契約にできる。

### VFMとの結合

変換後MarkdownをVFM 2.7.0へ渡した結果、IDを持つ`figure`の直下に`figcaption`と`pre`が生成された。`figcaption`はHTMLの子要素順でも`pre`より前にあり、コードの上へキャプションを配置できることを確認した。

言語に`kotlin`を指定したコードは、`language-kotlin`クラスを持つ`pre`と`code`へ変換された。コード内の`fun`は`token keyword`クラスを持つ`span`となり、Prismによる構文強調も保持された。

言語を省略したコードは、VFMによって`language-text`クラスを持つ`pre`と`code`へ変換された。mdast上では言語指定が`null`でも、VFM変換後のHTMLではプレーンテキスト用の言語classが補われる。

## 評価

Container directiveの`listing`で単一のコードフェンスを囲む候補記法は、clonoの現在のMarkdown変換パイプラインで技術的に成立する。新しい解析ライブラリを導入せず、既存のGeneric Directives拡張だけで、ラベル、ID、言語指定、メタ情報およびコード本文を個別に扱える。

元の`code`ノードをそのまま変換結果へ残せるため、clonoがコード本文をHTMLへ変換し直す必要はない。clonoは番号付きコードリストに必要な外側の構造とキャプションを生成し、コードフェンスからHTMLへの変換と構文強調をVFMへ委譲できる。

キャプションをコードより前へ置く構造も、候補記法からVFM変換後のHTMLまで一つのパイプラインで成立した。以前のVivliostyle調査で確認したCSSカウンター、長いコードリストのページ分割、参照文字列およびPDF内部リンクの構造と組み合わせられる見通しが立った。

## 仕様策定への示唆

今回の結果から、初期仕様では次の契約を採用できる見通しが立った。

- 番号付きコードリストにはContainer directiveの`listing`を使用する
- directiveのラベルをキャプション、`id`属性を論理IDとして扱う
- directive直下には単一のフェンス付き`code`ノードだけを許可する
- キャプションを必須のプレーンテキストとし、raw HTMLへ埋め込む際はHTMLのテキスト内容としてエスケープする
- 言語指定は任意とし、指定された場合は元の値を保持する
- コードフェンスのメタ情報は検出できるが、初期仕様では許可せず診断する
- 番号なしコードブロックには通常のMarkdownコードフェンスを使用する
- 元の`code`ノードを、raw HTMLの`figure`と上側の`figcaption`で囲むVFM向けMarkdownを出力する
- コード内の独自記法と同じ文字列には触れず、コード本文としてVFMへ渡す

## 成立条件と未確認事項

今回の結果は、Generic Directivesの構文拡張とmdast拡張を同じ解析および直列化処理へ登録することを前提とする。また、開始raw HTML、コードフェンス、終了raw HTMLの間に空行を置き、VFMがHTML内のMarkdownを処理できる形で直列化する必要がある。

次の事項は検証していない。

- 候補記法の属性、ラベル、内容モデルまたは配置に対する意味検証と診断
- 空のキャプション、キャプションの省略、キャプション内のMarkdownまたはraw HTML
- 同じ文書または書籍全体にある論理IDとHTML IDの重複検査
- コードリスト参照の解決、プレースホルダーおよび原稿間URLの生成
- リスト番号と参照文字列を生成するclono基盤CSS
- 候補記法から生成した構造を含むPDF
- 行番号、特定行の強調、差分表示またはコードフェンスのメタ情報を使用する機能
- 製品用テーマでの長い行の折り返しと禁則処理
- キャプションがページ末尾の近くに配置される原稿での改ページ
- コードリスト内の別のブロック要素または入れ子のdirective

Vivliostyleへ委譲するリスト番号、構文強調、複数ページへの分割、同一原稿および原稿間の参照、PDF内部リンクは、既存の[コードリストID・キャプション・連番・相互参照に関する調査](vivliostyle-code-listing-references.md)と[参照機能の結合検証](vivliostyle-reference-integration.md)で確認している。正式な著者向け記法、診断、ID、参照およびCSSは、後続の仕様策定で定める。

## 再現方法

[検証用fixtureのREADME](fixtures/markdown-code-listing-directive/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm test`で自動検証できる。`npm run inspect`を実行すると、入力から生成された完全なmdastと変換後Markdownを確認できる。

## 再調査する条件

- `mdast-util-from-markdown`、`mdast-util-to-markdown`またはGeneric Directivesの拡張をメジャーバージョンアップする場合
- 正式な著者向け記法または出力構造で、今回の変換方式を利用できない場合
- コードフェンスのメタ情報を利用する機能へ対応する場合
- キャプション内のMarkdown、raw HTMLまたは現在の内容モデルに含まれない表現へ対応する場合
- VFMがHTML内のMarkdown、コードフェンス、言語省略時のclassまたは構文強調を処理する方法が変更された場合
- Thunder Clawの実原稿で、直列化によってコードの意味が変化する事例が見つかった場合

## 参照資料

- [プロジェクト憲章](../project-charter.md)
- [VivliostyleのコードリストID・キャプション・連番・相互参照に関する調査](vivliostyle-code-listing-references.md)
- [参照機能の結合検証](vivliostyle-reference-integration.md)
- [Markdown AST変換と出力方式に関する調査](markdown-ast-transformation.md)
- [Generic DirectivesとmdastによるMarkdown変換パイプラインのADR](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [検証用fixture](fixtures/markdown-code-listing-directive/)
- [Generic directives/plugins syntax](https://talk.commonmark.org/t/generic-directives-plugins-syntax/444)
- [`mdast-util-from-markdown` 2.0.3](https://github.com/syntax-tree/mdast-util-from-markdown/tree/2.0.3)
- [`mdast-util-to-markdown` 2.1.2](https://github.com/syntax-tree/mdast-util-to-markdown/tree/2.1.2)
- [`micromark-extension-directive` 4.0.0](https://github.com/micromark/micromark-extension-directive/tree/4.0.0)
- [`mdast-util-directive` 3.1.0](https://github.com/syntax-tree/mdast-util-directive/tree/3.1.0)
- [VFM 2.7.0](https://github.com/vivliostyle/vfm/tree/v2.7.0)
