# 番号付きコードリストとコードリスト参照仕様

- 状態: 実装予定
- 作成日: 2026-09-10
- 最終更新日: 2026-09-11

## 目的

この文書は、フェンス付きコードブロックへ参照用IDとキャプションを付け、同じ書籍に含まれる原稿から番号、番号とキャプション、またはキャプションを参照するための初期仕様を定める。

clonoは、番号付きコードリストを参照可能な構造へ変換し、書籍に含まれるMarkdown原稿から参照対象を収集して参照先を解決する。コードフェンスのHTML変換と構文強調、リスト番号と参照文字列の生成、章および付録ごとのカウンター、PDF内部リンク、およびコードの紙面レイアウトは、VFM、VivliostyleとCSSへ委譲する。

## 対象範囲

初期仕様では、次の機能を扱う。

- 一つのフェンス付きコードブロック、参照用IDおよびキャプションを持つ番号付きコードリスト
- 番号付きコードリストと通常の番号なしコードブロックの書き分け
- 任意のコードフェンス言語指定
- コード内にあるMarkdownまたはclonoの独自記法と同じ文字列の保持
- 同一Markdown原稿内のコードリスト参照
- `publication`に掲載されたMarkdown原稿間のコードリスト参照
- リスト番号、リスト番号とキャプション、またはキャプションだけを表示する参照
- 単一ファイル変換における未解決参照のプレビュー用プレースホルダー
- 複数ページにまたがる長いコードリスト
- 重複ID、未定義参照および不正な記法の診断

次の機能は初期仕様に含めない。

- コードフェンスのメタ情報
- 行番号
- 特定行の強調
- 差分表示
- コードの実行、評価または外部ファイルからの読み込み
- キャプション内のMarkdown
- 前付または後付における番号付きコードリスト
- 通常の番号なしコードブロックへの参照
- 通過HTML内にあるコードリストまたはIDの収集と参照
- 著者が直接記述したraw HTMLまたはVFMが生成するすべてのIDとの衝突検査
- コードリストごとの任意のclass、styleまたはその他の表示属性
- 継続ページにキャプションまたは見出しを繰り返す機能

## 実装状況

2026年9月10日現在、この仕様は実装予定である。番号付きコードリストの著者向け記法、コードリスト参照、共通名前空間への統合、基盤CSSおよび自動テストは未実装である。

[Generic DirectivesとコードフェンスのMarkdown ASTに関する調査](../research/markdown-code-listing-directive.md)により、候補となる著者向け記法の解析、元のコードノードを保持したVFM向けMarkdownへの変換、およびVFMによるHTML変換が成立することを確認している。リスト番号、構文強調、複数ページへの分割、同一原稿および原稿間の参照、番号とキャプションの取得、およびPDF内部リンクは、既存のVivliostyle調査と結合検証で確認している。

## 番号付きコードリスト

### 構文

番号付きコードリストには、Container directiveの`listing`を使用する。

````markdown
:::listing[挨拶を表示する関数]{#greeting}

```kotlin
fun greet(name: String): String {
    return "Hello, $name!"
}
```

:::
````

`listing`のラベルをキャプション、`id`属性を著者が管理する論理IDとする。`listing`はラベルと`id`属性を必須とし、空のラベルまたは`id`以外の属性を許可しない。Text directiveまたはLeaf directiveとして記述した`listing`はエラーとする。

キャプションはファイル名に限定せず、空でない任意のプレーンテキストとする。強調、インラインコード、リンク、画像、改行またはその他のMarkdownを許可しない。

番号を付けないコードブロックには、`listing`で囲まず通常のMarkdownコードフェンスを使用する。clonoは通常のコードブロックを番号付きコードリストへ変換せず、その構造と意味を壊さず後段へ渡す。

### 内容モデル

`listing`の直下には、フェンス付きコードブロックを一つだけ記述する。コードブロック以外の段落、複数のコードブロック、インデント形式のコードブロック、raw HTML、他のブロックまたはdirectiveを許可しない。`listing`自身を含むdirectiveの入れ子も許可しない。

Markdown解析器が一つの`code`ノードとして認識したことを、コード本体の成立条件とする。コード本文の内容は検証せず、`code.value`を変更しない。Markdown、Generic Directivesまたはclonoの独自記法と同じ文字列がコード内にある場合も、コード本文として保持する。

コードフェンスの言語指定は任意とする。言語を指定した場合は`code.lang`の値を変更せずVFMへ渡し、省略した場合は言語を補完しない。言語名の妥当性と対応する構文強調の有無はVFMの責務とし、clonoは特定の言語一覧による制限を設けない。

ただし、VFMがキャプション付きコードとして解釈する`kotlin:Main.kt`のようなコロンを含む言語指定は許可しない。番号付きコードリストのキャプションは`listing`のラベルだけを正本とし、コードフェンス側へ重複して指定しない。

コードフェンスのメタ情報は初期仕様では許可しない。`code.meta`が`null`でない場合は、VFMが解釈できる値であってもエラーとする。キャプションは`listing`のラベルへ記述し、コードフェンスのメタ情報へ重複して記述しない。

### 配置と文書種別

`listing`はMarkdown文書のルート直下にだけ記述できる。コラム、文字揃え、引用、箇条書き、番号付きリスト、脚注定義またはその他のブロックの内部に記述した場合はエラーとする。

`clono build`では、`publication`に掲載され、`kind`が`chapter`または`appendix`のMarkdown原稿に`listing`を記述できる。`frontmatter`または`backmatter`では通常の番号なしコードブロックを使用する。前付または後付でも参照可能な番号なしコードリストが必要になった場合は、IDとキャプションを持つ別の入力契約を改めて検討する。

`clono build`では、`publication`に掲載されていないMarkdownにも`listing`を記述できる。clonoは番号付きコードリストの構造へ変換するが、その論理IDを書籍全体の参照名前空間へ登録しない。この未掲載原稿で`xref`を使用した場合は、書籍参照の対象外としてエラーにする。

`clono transform`は書籍構造と文書種別を読み込まないため、`listing`が記述された文書の`kind`を検証しない。単一ファイル変換の結果を組版する利用者は、本文または付録に相当するカウンターを自身のテーマで用意する。

### 論理ID

論理IDは、次の正規表現に一致するASCII小文字、数字およびハイフンだけで構成する。

```text
^[a-z][a-z0-9-]*$
```

論理IDは英小文字で始める。大文字、アンダースコア、日本語、空白またはその他の記号を許可しない。

`clono build`では、clonoが管理する見出し、番号付き画像、番号付き表および番号付きコードリストを含め、論理IDを書籍全体で一意とする。

clonoは論理IDから、次のHTML IDを導出する。

| 対象 | HTML ID |
| --- | --- |
| コードリスト全体 | `listing-<論理ID>` |
| キャプション | `listing-<論理ID>-caption` |

論理IDが異なる場合も、導出したHTML ID同士、または他の参照対象が使用するHTML IDと衝突する場合はエラーとする。著者が直接記述したraw HTML、通常のMarkdownまたはVFMが生成する任意のIDとの衝突を、clonoが網羅的に検出することは保証しない。

### 変換後Markdownに含める構造

`clono transform`および`clono build`の直接出力は、完全なHTML文書ではなく、VFMへ渡すUTF-8のMarkdownである。番号付きコードリストは、`figure`と`figcaption`を持つ開始raw HTMLノード、元の`code`ノード、終了raw HTMLノードへ変換する。

変換後Markdownは、概ね次の構造となる。

````markdown
<figure class="clono-numbered-listing" id="listing-greeting">
<figcaption class="clono-listing-caption" id="listing-greeting-caption">挨拶を表示する関数</figcaption>

```kotlin
fun greet(name: String): String {
    return "Hello, $name!"
}
```

</figure>
````

Markdown直列化により、コードフェンスの記号、長さ、前後の空白またはその他の書式が入力から変化する場合がある。入力と変換後Markdownの文字列一致は保証せず、`code`ノードの言語指定、メタ情報がないこと、およびコード本文の意味を保持する。

VFMはraw HTMLを保持し、内側のコードフェンスをHTMLの`pre`と`code`へ変換する。最終HTMLでは、IDとclassを持つ`figure`の直下に`figcaption`と`pre`がこの順序で置かれる。これにより、通常の文書フローではキャプションをコードの上へ表示する。言語を省略したコードへVFMが`language-text`を付けることを、clonoの入力や出力へ補完する契約にはしない。

HTMLやMarkdownの空白と改行、VFMが構文強調のために生成する`span`の詳細は規範ではない。要素の親子関係と順序、要素名、属性、class、および元のコードブロックが持つ意味をclonoの出力契約とする。

## コードリスト参照

### 構文

参照には、画像、見出しおよび表の参照と共通のText directiveである`xref`を使用する。

```markdown
詳しくは:xref[greeting]{type="listing" format="number"}を参照してください。

詳しくは:xref[greeting]{type="listing" format="number-title"}を参照してください。

詳しくは:xref[greeting]{type="listing" format="title"}を参照してください。
```

`xref`のラベルを参照先の論理IDとする。ラベルは番号付きコードリストと同じ論理IDの規則に従う。`type`と`format`は両方必須とし、コードリスト参照では次の値を許可する。

| 属性 | 値 | 意味 |
| --- | --- | --- |
| `type` | `listing` | 番号付きコードリストを参照する |
| `format` | `number` | `リスト1.1`の形式で番号だけを表示する |
| `format` | `number-title` | `リスト1.1 挨拶を表示する関数`の形式で番号とキャプションを表示する |
| `format` | `title` | `挨拶を表示する関数`の形式でキャプションだけを表示する |

`type`または`format`の省略、未知の値、これら以外の属性、空または不正な論理IDはエラーとする。Container directiveまたはLeaf directiveとして記述した`xref`もエラーとする。

初期仕様の`listing`は本文または付録にある番号付きコードリストだけを対象とするため、すべてのコードリスト参照先は番号を持つ。通常の番号なしコードブロックは参照対象へ登録しない。

### 解決済み参照のHTML構造

解決済み参照は、変換後Markdownへraw HTMLの空の`a`要素として埋め込む。VFMはこの要素を保持し、最終HTMLでも同じ要素と属性を出力する。

同一Markdown原稿内のコードリストを番号とキャプションで参照する場合、次の構造へ変換する。

```html
<a
  class="clono-xref clono-xref-listing clono-xref-number-title"
  href="#listing-greeting"
  data-title-href="#listing-greeting-caption"
></a>
```

別のMarkdown原稿にあるコードリストを参照する場合は、参照元の変換後HTMLから参照先の変換後HTMLへの相対パスを`href`と`data-title-href`へ付ける。

```html
<a
  class="clono-xref clono-xref-listing clono-xref-number-title"
  href="chapter-two.html#listing-greeting"
  data-title-href="chapter-two.html#listing-greeting-caption"
></a>
```

すべての解決済みコードリスト参照は、次のclassを持つ。

- 参照に共通する`clono-xref`
- コードリスト参照を示す`clono-xref-listing`
- 表示形式を示す`clono-xref-number`、`clono-xref-number-title`または`clono-xref-title`

`href`は、すべての形式でコードリスト全体のHTML IDを指し、PDF内部リンクの移動先とリストカウンターの取得先に使用する。`data-title-href`は、`number-title`と`title`だけに出力し、キャプションのHTML IDを指す。

Markdown原稿からHTMLへのパス変換、原稿間の相対パス、URLのパーセントエンコード、およびHTML属性値のエンコードには、[番号付き画像と画像参照仕様](figure-references.md)と同じ規則を適用する。

### 単一ファイル変換の未解決参照

`clono transform`は一つのMarkdown原稿内にあるすべての番号付きコードリストを収集し、同一原稿内の参照を前方参照と後方参照の両方について解決する。

同一原稿内に論理IDが存在しない参照は、別原稿にあるコードリストを参照している可能性がある。単一章を執筆中に紙面を確認できるよう、この場合だけはエラーにせず、リンクを持たない`span`のプレースホルダーへ変換する。

| `format` | 表示する固定文言 |
| --- | --- |
| `number` | `リストX.X` |
| `number-title` | `リストX.X 参照先未解決` |
| `title` | `参照先未解決` |

例えば、`format="number-title"`の未解決参照は次の構造へ変換する。

```html
<span
  class="clono-xref clono-xref-listing clono-xref-number-title clono-xref-placeholder"
>リストX.X 参照先未解決</span>
```

プレースホルダーの文言は固定し、論理IDなど著者が入力した動的な値を埋め込まない。HTMLのclassにより未解決参照を機械的に識別できるようにする。論理IDの形式、属性、同一原稿内の重複ID、参照先の型またはその他の意味上の誤りは、単一ファイル変換でも通常どおりエラーとする。

この許容は`clono transform`による執筆中のプレビューだけを目的とする。`clono build`では未解決参照をプレースホルダーへ変換しない。

## 書籍プロジェクトでの収集と解決

`clono build`は、`publication`で`type`が`document`であり、拡張子を小文字化した結果が`.md`となるすべての原稿を、書籍全体の参照スコープとする。`blank-page`、通過HTMLおよび`publication`に掲載されていないMarkdownは、このスコープに含めない。

書籍プロジェクト変換では、対象原稿を個別に出力する前に、少なくとも次の段階を分離して実行する。

```text
掲載Markdownの解析
  → 番号付き画像、見出し、番号付き表および番号付きコードリストの収集
  → 論理IDとHTML IDの検証
  → 画像、見出し、表およびコードリストの参照の収集と解決
  → 各原稿のAST変換
  → Markdownへの直列化
```

前方参照と後方参照の両方を許可する。参照先の論理IDから対象の種類と原稿を特定し、参照元と参照先の変換後HTMLパスを使って`href`と`data-title-href`を生成する。

`publication`に掲載されていないMarkdownは、番号付きコードリストを単一文書の構造として変換できるが、そのIDを書籍全体の名前空間へ登録しない。掲載されていないMarkdownに`xref`がある場合は、書籍参照の対象外であることを診断し、単一ファイル変換用のプレースホルダーへ変換しない。

掲載Markdownに未定義参照、重複ID、文書種別に適合しない番号付きコードリスト、内容モデルの違反、解決できない出力パスまたはその他の診断が一件でもある場合は、部分的な変換計画または生成済み原稿ツリーを公開しない。診断の順序とCLI表示は[書籍プロジェクト仕様](book-project.md)に従う。

## CSSとVivliostyle

clono基盤CSSは、固定classに対して次の機能上必要な規則を提供する。

- Markdown原稿ごとにリストカウンターをリセットする
- `clono-numbered-listing`でリストカウンターをインクリメントする
- キャプションの前へ`リスト<章または付録番号>.<リスト番号>`を表示する
- `clono-xref-number`と`clono-xref-number-title`で参照先のリスト番号を表示する
- `clono-xref-number-title`と`clono-xref-title`で参照先のキャプションを表示する
- 長いコードリストを複数ページへ分割できるようにする
- キャプションとコードの先頭が可能な限り分離しないようにする

CSSカウンター名には、調査fixtureと同じ`listing`を使用する。既存の`figure`および`table`カウンターと同じ要素で初期化するため、`body`の`counter-reset`へ`listing`を追加し、既存のカウンターを上書きしない。

`clono-numbered-listing`とその直下の`pre`では、複数ページへの分割を禁止しない。`clono-listing-caption`には、後続するコードの先頭との分離を避ける規則を指定する。キャプションはHTMLに一度だけ出力し、継続ページへ繰り返す処理を追加しない。

clono基盤CSSは本文用の10進数による表示を既定とする。付録の英字による章番号、章および付録ごとの`chapter`カウンターの設定、コードのフォント、色、背景、余白、折り返し、禁則処理、構文強調の配色、および長いコードリストの詳細な改ページ結果は利用者テーマの責務とする。利用者テーマは、書籍の`kind`に応じてclono基盤CSSのカウンター表示を上書きできる。

clonoはリスト番号または参照文字列を変換時に計算して本文へ埋め込まない。VFMとVivliostyleは、コードフェンスのHTML変換、構文強調、`counter()`、`target-counter()`および`target-text()`を使用した番号とキャプションの生成、ページ分割、ならびに`a`要素のPDF内部リンクとしての保持を担う。

`clono transform`のプレースホルダーだけは、書籍全体の対象を解決できないため固定文字列を本文へ出力する。プレースホルダーの`X.X`は実際のリスト番号を表さず、`clono build`の出力には現れない。

## 安全条件

clonoが生成する要素名、属性名およびclass名は、この仕様に記載した許可リストへ固定する。著者が指定した値を要素名、属性名またはclass名として使用しない。

キャプションはHTMLのテキストとしてエンコードする。コード本体は検証済みの`code`ノードとしてMarkdownへ直列化し、著者が記述したコードをclonoがraw HTMLとして直接連結しない。言語指定も元の`code`ノードの値として直列化し、clonoがHTML属性へ直接出力しない。

コードリスト参照の`href`と`data-title-href`は、検証済みの論理IDと書籍プロジェクト内の原稿パスからclonoが生成する。著者が指定した任意のURLをコードリスト参照の属性へ直接出力しない。

## 診断と出力

少なくとも次の問題を診断する。

- `listing`または`xref`のdirective種別が異なる
- 必須のラベルまたは属性がない
- 未知の属性がある
- キャプションまたは論理IDが不正である
- `listing`の内容モデルまたは配置が不正である
- コードフェンスの言語指定にコロンがある
- コードフェンスにメタ情報がある
- `listing`が前付または後付の掲載Markdownにある
- 論理IDまたは導出したHTML IDが重複している
- `xref`の`type`または`format`が不正である
- 参照先が存在しない、または指定した参照種別と一致しない
- 掲載されていないMarkdownで`xref`を使用している
- 別原稿への相対HTMLパスを安全に生成できない

診断には、[ADR 0003](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)に従ってファイル名、行、列、directive名および問題の説明を含める。同じ文書の独立した問題は可能な範囲で収集し、入力位置の順に返す。

単一ファイル変換では診断が一件でもある場合、AST変換を実行せず、部分的な出力Markdownを返さない。書籍プロジェクト変換では、すべての掲載Markdownの解析、収集、検証および参照解決が成功した場合だけ各原稿を変換し、診断が一件でもある場合は生成済み原稿ツリーを公開しない。

## 更新方針

- 番号付きコードリストまたはコードリスト参照の構文、内容モデル、ID、HTML、CSS、収集範囲、解決規則または診断契約を変更する場合は、実装と同じPull Requestでこの文書を更新する
- 実装へ着手した場合は状態を「実装中」、実装と自動テストが完了した場合は「実装済み」へ更新する
- `xref`の共通契約を変更する場合は、[番号付き画像と画像参照仕様](figure-references.md)、[見出し参照仕様](heading-references.md)、[番号付き表と表参照仕様](table-references.md)および[clono著者向け記法](authoring-syntax.md)を同時に見直す
- VFMのコードフェンス、Markdown AST、Vivliostyleのカウンター、ページ分割、対象参照またはPDF内部リンクの挙動が変わった場合は、調査fixtureを再実行し、調査記録と責務判断を同時に更新する

## 参照資料

- [プロジェクト憲章](../project-charter.md)
- [clono著者向け記法](authoring-syntax.md)
- [書籍プロジェクト仕様](book-project.md)
- [番号付き画像と画像参照仕様](figure-references.md)
- [見出し参照仕様](heading-references.md)
- [番号付き表と表参照仕様](table-references.md)
- [Generic DirectivesとmdastによるMarkdown変換パイプラインのADR](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [Vivliostyleとclonoの責務整理](../vivliostyle-responsibilities.md)
- [VivliostyleのコードリストID・キャプション・連番・相互参照に関する調査](../research/vivliostyle-code-listing-references.md)
- [Generic DirectivesとコードフェンスのMarkdown ASTに関する調査](../research/markdown-code-listing-directive.md)
- [Vivliostyleの相互参照に関する結合検証](../research/vivliostyle-reference-integration.md)
