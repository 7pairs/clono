# Generic DirectivesとMarkdown ASTに関する調査

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

## 背景

clonoは、独自記法を含むMarkdownを正規表現による文字列置換ではなく、ASTなどの構造化表現を介して変換することを目指している。独自記法の第一候補には、プロジェクト憲章で[Generic Directives Proposal](https://talk.commonmark.org/t/generic-directives-plugins-syntax/444)に基づく記法を挙げている。

[開発基盤のADR](../decisions/0002-adopt-nodejs-clojurescript-development-platform.md)では、リポジトリ外の一時プロジェクトにおいて、`unified`、`remark-parse`、`remark-directive`をClojureScriptから読み込み、Generic Directivesを含むMarkdownをmdastへ変換できることを確認した。ただし、この検証はNode.js向けClojureScript開発基盤の成立性を判断するためのものであり、実際のmdast構造、Markdownへの直列化、異常な入力、低レベルAPIを直接利用する構成までは記録していなかった。

今回の調査では、Generic Directivesとmdastをclonoの構文解析および変換に利用できるか判断するため、実用途を模した候補記法を解析し、生成される構造とClojureScriptからの操作性を確認した。

## 調査目的

- Generic Directivesのcontainer、leaf、textの各構文が、用途を識別できるmdastノードになるか確認する
- 名前、属性、子要素、入力位置をClojureScriptから参照できるか確認する
- directive内にある通常のMarkdown構造を保持できるか確認する
- mdastを変更し、Markdownへ直列化して再解析できるか確認する
- clonoが認識しないdirectiveを意味解釈せずに保持できるか確認する
- 意味上の必須属性がない記法と、構文自体が不完全な記法の扱いを確認する
- コードフェンスなど、独自記法として解釈してはいけない場所を保護できるか確認する
- `remark`を経由せず、mdastとmicromarkの低レベルAPIをClojureScriptから直接利用できるか確認する

この調査は、著者向け記法、属性名、変換後の出力、採用ライブラリ、内部AST表現を確定するものではない。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/markdown-ast/)をリポジトリ内へ保存している。このfixtureはclono本体から独立したClojureScriptおよびnpmプロジェクトであり、`package.json`と`package-lock.json`によって依存関係を固定する。

解析には次の拡張を指定した`mdast-util-from-markdown`を使用した。

- `micromark-extension-directive`の`directive()`: Generic Directivesのトークン化
- `mdast-util-directive`の`directiveFromMarkdown()`: トークンからdirective用mdastノードへの変換

直列化には、`mdast-util-directive`の`directiveToMarkdown()`を指定した`mdast-util-to-markdown`を使用した。fixtureの依存ツリーには、`remark-parse`、`remark-directive`、`unified`を含めていない。

### 検証用の仮記法

実際の属性と入れ子構造を確認するため、次の仮記法を使用した。

| 想定用途 | 仮記法 | Generic Directivesの種類 |
| --- | --- | --- |
| コラム | `column` | Container directive |
| 右寄せ | `align` | Container directive |
| 強制改ページ | `page-break` | Leaf directive |
| 索引指定 | `index` | Text directive |
| 相互参照 | `xref` | Text directive |

たとえば、索引指定には次の入力を使用した。

```markdown
これは:index[索引項目]{reading="さくいんこうもく"}です。
```

これらの名前、属性、構文と用途の対応は、現実的なASTを生成するための仮定である。clonoの仕様として採用したものではない。

## 検証結果

### mdastの構造

各構文は、次の専用ノードへ変換された。

| 種類 | `type` | 主なフィールド |
| --- | --- | --- |
| Container directive | `containerDirective` | `name`、`attributes`、`children`、`position` |
| Leaf directive | `leafDirective` | `name`、`attributes`、空の`children`、`position` |
| Text directive | `textDirective` | `name`、`attributes`、インライン要素を持つ`children`、`position` |

索引指定は、概略として次の構造になった。

```json
{
  "type": "textDirective",
  "name": "index",
  "attributes": {
    "reading": "さくいんこうもく"
  },
  "children": [
    {
      "type": "text",
      "value": "索引項目"
    }
  ],
  "position": {
    "start": {
      "line": 18,
      "column": 4,
      "offset": 178
    },
    "end": {
      "line": 18,
      "column": 36,
      "offset": 210
    }
  }
}
```

`name`でclonoが扱う用途を識別し、`attributes`と`children`から著者が指定した値を取得できる。`position`には開始位置と終了位置の行、列、オフセットが含まれ、意味検証で問題を報告する位置の候補として利用できる。

Container directive内の段落、強調、インラインコード、リンク、箇条書きは、それぞれ通常のmdastノードとして保持された。右寄せの仮記法では、空行で区切った日付と名前が二つの`paragraph`としてcontainerの`children`へ格納された。このため、複数のブロックを一つの独自記法の範囲として扱える。

### ClojureScriptからの操作

解析結果はJavaScriptオブジェクトであり、ClojureScriptからプロパティと配列を直接参照できた。fixtureでは索引指定の`attributes.reading`と子`text`ノードの`value`を書き換え、そのASTをMarkdownへ直列化して再解析した。変更後の読みと表示語は、再解析後のmdastにも保持された。

AST全体を`js->clj`でClojureScriptの永続データ構造へ変換しなくても、今回必要とした走査、参照、変更は実現できた。JavaScriptオブジェクトのまま扱う構成は、初期実装の候補として成立する。

これは内部表現の採用決定ではない。実際の変換処理が複雑になり、ClojureScriptからの操作性が保守性を損なうと確認された場合は、変換範囲とデータ表現を改めて検討する。

### Markdownへの往復変換

候補原稿をmdastへ解析し、Markdownへ直列化してから再解析した結果、次の内容が保持された。

- 5種類のdirectiveのノード型と名前
- 日本語を含む属性値
- Text directiveの表示内容
- Container directive内の段落とリスト
- 強調、インラインコード、リンク、箇条書き

元のMarkdown文字列との完全一致は検証条件としていない。`mdast-util-to-markdown`は空白、改行、属性値の表現などを正規化する可能性があるため、今回確認したのは構造と意味の往復である。

この結果から、未知のdirectiveを意味解釈せずにASTとして保持することはできる。一方、文書全体を再直列化する場合、未知のdirectiveを含む元の文字列表現まで無変更で保持できるとは限らない。「触らない」という将来の契約を意味の保持とするか、原文のバイト列または表記まで保持するかは、出力方式の検証で決定する必要がある。

### 未知のdirective

`third-party`というclonoが認識しない想定のContainer directiveも、既知の候補と同じ`containerDirective`として解析された。名前、属性、本文は、直列化と再解析後にも保持された。

パーサーはclonoの既知・未知を区別しない。clono側でノードの`type`と`name`を確認し、既知のdirectiveだけを意味検証および変換の対象にできる。未知のdirectiveは意味解釈せず、AST上に保持したまま後段へ渡す方針が技術的に成立する。

### 不完全な記法

意味上の必須属性であると仮定した`reading`を省略しても、`:index[索引項目]`は`textDirective`として解析された。空の`attributes`と入力位置を取得できるため、パーサーとは別の意味検証段階で、既知のdirectiveに対する不足を位置付きで診断できる。

一方、次のように属性を閉じなかった入力は、属性を持たない`textDirective`と、後続の通常`text`ノードに分かれた。

```markdown
:index[壊れた索引]{reading="こわれたさくいん"です。
```

また、終了マーカーを持たないContainer directiveは構文エラーにならず、文書末尾までを`children`に含む`containerDirective`となった。

```markdown
:::column{title="閉じていないコラム"}
このコンテナには閉じ記号がありません。
```

mdastだけでは、文書末尾までを意図して記述したcontainerと、終了マーカーを書き忘れたcontainerを区別できない可能性がある。終了マーカーを必須として診断する場合は、元の入力範囲、micromarkのイベント、または追加の構文検証を利用できるか調査する必要がある。

Generic Directivesとして認識されない壊れ方もあり得るため、すべての構文誤りをmdast上の既知ノードだけから検出できるとは結論づけない。

### コードフェンス

コードフェンス内にContainer directiveとText directiveに見える文字列を置いても、文書全体は一つの`code`ノードとなり、directiveノードは生成されなかった。正規表現による文書全体の置換とは異なり、コード例を変換対象から除外できる。

## 評価

Generic Directivesの3種類は、clonoが必要とするブロック範囲、単独ブロック、インライン指定を表現し、それぞれ識別可能なmdastノードへ変換できた。通常のMarkdown構造と入力位置も保持できるため、Generic Directivesとmdastは、clonoの著者向け記法および構造化された変換処理の有力候補として技術的に成立する。

また、`mdast-util-from-markdown`、`mdast-util-to-markdown`と各directive拡張を、ClojureScriptから直接利用できた。この結果は、`remark`を利用できない、または利用すべきでないことを意味しない。低レベルAPIを直接利用する構成と、`unified`および`remark`の処理パイプラインを利用する構成の両方が候補となる。

採用判断では、直接依存関係の数だけでなく、次の点を比較する必要がある。

- clono自身が解析、変換、直列化の順序を管理する場合の実装量
- 将来のプラグインを処理パイプラインへ組み込む方法
- 変換ごとの設定、状態、診断情報の受け渡し
- JavaScriptエコシステムの既存プラグインを再利用する必要性
- production依存関係全体の大きさと保守状況

今回の調査だけでは、低レベルAPIと`remark`のどちらを採用するか決定しない。

## 後続調査の結果

[Markdown AST変換と出力方式に関する調査](markdown-ast-transformation.md)では、既知のdirectiveをraw HTMLノードへ変換し、VFMが処理できるMarkdownとして直列化するパイプラインを検証した。

通常のMarkdown、コードフェンス、VFMの脚注、未知のdirectiveを必要な範囲で保持しながら、Container、leaf、textの各directiveを用途別のHTML構造へ変換できた。直列化したMarkdownはVFM 2.7.0でHTMLへ変換でき、変換後の構造と脚注も保持された。

既知のdirectiveは変換前に意味検証し、入力位置を含む診断を生成できた。不正な既知のdirectiveがある場合はASTを変換せず、出力Markdownを返さない構成も成立した。

この結果により、VFM向けMarkdownは主要な出力形式の有力候補となった。著者向け記法、採用ライブラリ、内部表現、変換パイプライン、出力契約、診断形式は、二つの調査結果を踏まえてADRで決定する。

## 再現方法

[検証用fixtureのREADME](fixtures/markdown-ast/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm test`で自動検証できる。`npm run inspect`を実行すると、候補原稿から生成された実際のmdastをJSONで確認できる。

## 再調査する条件

- Generic Directives以外の独自記法を有力候補とする場合
- mdastまたはmicromark関連ライブラリのメジャーバージョンを更新する場合
- ClojureScriptからJavaScriptオブジェクトを直接扱うことが、実際の変換処理で保守上の問題となった場合
- 必要な構文誤りを入力位置付きで診断できない場合
- 未知のdirectiveまたは通常のMarkdownを、必要な範囲で保持できない場合
- 将来のプラグインAPIに、今回のAST構造または処理方式が適さないと判明した場合

## 参照資料

- [プロジェクト憲章](../project-charter.md)
- [0002: Node.js向けClojureScript開発基盤を採用する](../decisions/0002-adopt-nodejs-clojurescript-development-platform.md)
- [Generic directives/plugins syntax](https://talk.commonmark.org/t/generic-directives-plugins-syntax/444)
- [`mdast-util-from-markdown` 2.0.3](https://github.com/syntax-tree/mdast-util-from-markdown/tree/2.0.3)
- [`mdast-util-to-markdown` 2.1.2](https://github.com/syntax-tree/mdast-util-to-markdown/tree/2.1.2)
- [`micromark-extension-directive` 4.0.0](https://github.com/micromark/micromark-extension-directive/tree/4.0.0)
- [`mdast-util-directive` 3.1.0](https://github.com/syntax-tree/mdast-util-directive/tree/3.1.0)
