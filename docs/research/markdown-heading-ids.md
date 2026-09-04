# VFM見出しIDのMarkdown ASTに関する調査

- 状態: 調査済み
- 調査日: 2026-09-05
- 最終更新日: 2026-09-05
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
  - 脚注構文拡張: `micromark-extension-gfm-footnote` 2.1.0
  - 脚注用mdast拡張: `mdast-util-gfm-footnote` 2.1.0
  - 表構文拡張: `micromark-extension-gfm-table` 2.1.1
  - 表用mdast拡張: `mdast-util-gfm-table` 2.0.0

## 背景

[Vivliostyleの見出しID・連番・相互参照に関する調査](vivliostyle-heading-references.md)では、VFMの明示的な見出しID記法、CSSカウンターによる章・節・小節番号、およびVivliostyleによる同一原稿・原稿間の参照を確認した。

```markdown
# はじめに {#chapter-introduction}
```

この記法を使用すれば、clonoが見出しIDを独自に生成または出力し直す必要はない。一方、見出し参照を実装するには、clono自身も変換前のMarkdownから明示ID、見出しレベルおよび入力位置を取得し、書籍全体の参照対象として収集する必要がある。

clonoが採用する`mdast-util-from-markdown`はVFM専用のパーサーではないため、`{#id}`がどのようなmdastになるか、インラインMarkdownを含む見出しからも安定してID候補を取得できるかは、既存のVivliostyle調査だけでは確認できていなかった。

## 調査目的

- VFMの明示的な見出しID記法が、clonoと同じMarkdown解析構成でどのようなmdastになるか確認する
- `h1`から`h4`までの見出しレベル、明示IDおよび入力位置を同時に取得できるか確認する
- インラインMarkdownを含む見出しから明示IDの候補を取得できるか確認する
- IDのない見出し、通常の波括弧、不正なID候補および重複したID候補を区別できるか確認する
- Markdownへの直列化と再解析によって、見出しIDとインライン構造がどのように変化するか確認する

この調査は、見出し参照の最終的な入力契約、対応する見出しレベル、HTMLのclassまたはCSSを決定するものではない。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/markdown-heading-ids/)をリポジトリ内へ保存している。このfixtureはclono本体から独立したClojureScriptおよびnpmプロジェクトであり、`package.json`と`package-lock.json`によって依存関係を固定する。

解析と直列化には、clono本体と同じ低レベルAPIおよび拡張を使用した。

- `mdast-util-from-markdown`と`mdast-util-to-markdown`
- Generic Directives用のmicromarkおよびmdast拡張
- GFM脚注用のmicromarkおよびmdast拡張
- GFM表用のmicromarkおよびmdast拡張

fixtureでは、次の入力を組み合わせた。

- プレーンテキストと明示IDを持つ`h1`
- インラインコード、強調および明示IDを持つ`h2`
- 明示IDを持たない`h3`
- 初期仕様の対象外となる可能性がある、明示IDを持つ`h4`
- clonoの論理ID規則に一致しない`{#Invalid_ID}`
- 同じ`{#duplicate}`を持つ二つの見出し
- ID記法ではない`{注記}`で終わる見出し

## 検証結果

### 明示IDのmdast表現

`mdast-util-from-markdown`は、VFMの`{#id}`を見出し専用のプロパティや属性ノードとして解析しなかった。プレーンテキストの見出しでは、タイトルとID記法を一つの`text`ノードへ保持した。

```json
{
  "type": "heading",
  "depth": 1,
  "children": [
    {
      "type": "text",
      "value": "はじめに {#chapter-introduction}"
    }
  ]
}
```

見出しレベルは`heading.depth`へ保持される。`h1`から`h4`まで、それぞれ`1`から`4`として取得できた。

見出しノードと子ノードの`position`には、開始位置と終了位置の行、列およびオフセットが保持された。このため、収集または意味検証で問題が見つかった場合に、見出しの入力位置を診断へ使用できる。

### インラインMarkdownを含む見出し

次の見出しでは、インラインコードと強調が通常のmdastノードになり、ID記法は最後の`text`ノードへ保持された。

```markdown
## `clono`の**構造** {#section-structure}
```

子ノードの種類は次の順序となった。

```text
inlineCode → text → strong → text
```

最後の`text`ノードの値は` {#section-structure}`であった。したがって、見出し全体をプレーンテキストへ変換しなくても、最後の子ノードからID候補を取得できる。タイトルに含まれるインライン構造を変更または制限する必要も、この調査では確認されなかった。

### ID候補の識別

fixtureでは、見出しの最後の子が`text`ノードであり、その値が空白に続く`{#...}`で終わる場合だけ、内部の値を明示IDの候補として取得した。

この方法により、次の入力を区別できた。

| 入力 | 結果 |
| --- | --- |
| `{#chapter-introduction}` | 有効なID候補 |
| `{#Invalid_ID}` | clonoの論理ID規則に一致しないID候補 |
| ID記法のない見出し | ID候補なし |
| `{注記}`で終わる見出し | ID候補なし |

今回の有効性判定には、番号付き画像の論理IDと同じ`^[a-z][a-z0-9-]*$`を仮定した。最終的なID規則は見出し参照仕様で決定する。

同じ`{#duplicate}`を持つ二つの見出しは、別々の`heading`ノードとして保持された。各ノードから同じID候補と異なる入力位置を取得できるため、同一原稿または書籍全体での重複検査に利用できる。

### Markdownへの往復変換

有効な明示IDを持つ見出しをMarkdownへ直列化して再解析した結果、ID候補、見出しレベル、インラインコードおよび強調を再び取得できた。

clonoの論理ID規則に一致しない`{#Invalid_ID}`は、直列化したMarkdownでは`{#Invalid\_ID}`となった。再解析すると、textノードの値は再び`{#Invalid_ID}`となり、同じ不正なID候補として取得できた。

この結果は、不正なID候補を直列化後も常に同じ文字列表現で保持できることを意味しない。clonoの変換パイプラインでは、既知の不正な入力がある場合にAST変換と直列化を行わないため、ID候補の検証と診断も直列化より前に行う。

## 評価

VFMの明示的な見出しID記法は、clonoの現在のMarkdown解析構成でも参照対象の収集に利用できる。

専用のmdastプロパティは生成されないが、次の情報を組み合わせることで、追加の構文解析ライブラリを導入せずに見出し参照の実装へ進める見通しが立った。

- `heading.depth`による見出しレベル
- 最後の`text`ノードにある明示ID候補
- `position`による診断位置
- 掲載原稿のコンテキストにある文書種別と原稿パス

clonoは、収集のために見出しの`{#id}`を削除または別の記法へ変換する必要がない。元の見出しをMarkdownへ保持してVFMへ渡せば、後段で同じIDをHTMLの見出し要素へ出力できる。

見出しタイトルにインラインMarkdownが含まれる場合も、ID候補の取得とインライン構造を両立できた。参照時のタイトル表示は、既存のVivliostyle調査で確認した`target-text()`へ委譲できるため、clonoがタイトルを複製して参照要素へ埋め込む必要はない。

## 成立条件と未確認事項

今回確認したID候補は、空白に続く`{#id}`だけを持つATX見出しを前提とする。次の事項は検証していない。

- Setext形式の見出し
- IDとclassなど、複数のVFM属性を同じ波括弧へ指定する構文
- エスケープした`{#id}`または複数のID記法で終わる見出し
- 複数行にまたがる見出し
- VFMが許可する見出し属性構文全体と、fixtureで使用したID候補の抽出規則が完全に一致すること

初期仕様で`# 見出し {#id}`の形式だけを参照対象として採用する場合、これらを先行して対応する必要はない。別の構文を許可する場合は、VFMの解釈とclonoの収集結果が一致することを追加検証する必要がある。

このfixtureは`h4`の深さとID候補も取得できることを確認したが、`h4`以降を初期の参照対象とするかは決定していない。見出し番号、文書種別ごとの表示、参照用HTMLおよびCSSも、後続の見出し参照仕様で決定する。

## 再現方法

[検証用fixtureのREADME](fixtures/markdown-heading-ids/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm test`で自動検証できる。`npm run inspect`を実行すると、入力から生成された完全なmdastをJSONで確認できる。

## 再調査する条件

- `mdast-util-from-markdown`、`mdast-util-to-markdown`またはMarkdown拡張のメジャーバージョンを更新する場合
- VFMの見出しID記法または生成する見出し構造が変更された場合
- Setext見出し、複数属性または現在の抽出規則で扱えない見出し構文へ対応する場合
- 見出しタイトルの末尾構造からID候補を安定して取得できない実例が見つかった場合
- 見出しIDの構文または有効性を、現在と異なるライブラリへ委譲する場合

## 参照資料

- [Vivliostyleの見出しID・連番・相互参照に関する調査](vivliostyle-heading-references.md)
- [Generic DirectivesとMarkdown ASTに関する調査](markdown-ast.md)
- [番号付き画像と画像参照仕様](../specifications/figure-references.md)
- [Generic DirectivesとmdastによるMarkdown変換パイプラインのADR](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [セクション分け（Sectionization） | Vivliostyle Flavored Markdown 2.7.0](https://github.com/vivliostyle/vfm/blob/v2.7.0/docs/ja/vfm.md#セクション分け-sectionization)
- [`mdast-util-from-markdown` 2.0.3](https://github.com/syntax-tree/mdast-util-from-markdown/tree/2.0.3)
- [`mdast-util-to-markdown` 2.1.2](https://github.com/syntax-tree/mdast-util-to-markdown/tree/2.1.2)
