# 番号付き表と表参照仕様

- 状態: 実装済み
- 作成日: 2026-09-08
- 最終更新日: 2026-09-11

## 目的

この文書は、GFM形式のMarkdown表へ参照用IDとキャプションを付け、同じ書籍に含まれる原稿から番号、番号とキャプション、またはキャプションを参照するための初期仕様を定める。

clonoは、番号付き表を参照可能な構造へ変換し、書籍に含まれるMarkdown原稿から参照対象を収集して参照先を解決する。Markdown表のHTML変換、表番号と参照文字列の生成、章および付録ごとのカウンター、PDF内部リンク、および表の紙面レイアウトは、VFM、VivliostyleとCSSへ委譲する。

## 対象範囲

初期仕様では、次の機能を扱う。

- 一つのGFM表、参照用IDおよびキャプションを持つ番号付き表
- 番号付き表と通常の番号なしMarkdown表の書き分け
- 表内のプレーンテキスト、強い強調、強調、インラインコード、リンク、参照形式リンク、取り消し線および文字寄せ
- 同一Markdown原稿内の表参照
- `publication`に掲載されたMarkdown原稿間の表参照
- 表番号、表番号とキャプション、またはキャプションだけを表示する参照
- 単一ファイル変換における未解決参照のプレビュー用プレースホルダー
- 重複ID、未定義参照および不正な記法の診断

次の機能は初期仕様に含めない。

- 表内の画像、脚注、raw HTML、directiveまたは強制改行
- HTMLで記述する表
- セル結合
- セル内の複数段落または改行
- 複数ページにまたがる表の分割方法またはヘッダー行の繰り返し保証
- キャプション内のMarkdown
- 前付または後付における番号付き表
- 通常の番号なしMarkdown表への参照
- 通過HTML内にある表またはIDの収集と参照
- 著者が直接記述したraw HTMLまたはVFMが生成するすべてのIDとの衝突検査
- 表ごとの任意のclass、styleまたはその他の表示属性

表内脚注はThunder Clawの書籍で将来必要になる可能性がある。必要性が具体化した場合は、VFMとVivliostyleを含む結合検証を行い、内容モデルを拡張する。

## 実装状況

2026年9月9日現在、この仕様は実装済みである。番号付き表の解析、検証、変換、参照対象情報の生成、共通名前空間への統合、同一原稿および原稿間の表参照の解決、単一ファイル変換用プレースホルダー、失敗契約、基盤CSSならびにCLI統合テストを実装している。

[Generic DirectivesとGFM表のMarkdown ASTに関する調査](../research/markdown-table-directive.md)により、候補となる著者向け記法の解析、元の表ノードを保持したVFM向けMarkdownへの変換、およびVFMによるHTML変換が成立することを確認している。表番号、同一原稿および原稿間の参照、番号とキャプションの取得、およびPDF内部リンクは、既存のVivliostyle調査と結合検証で確認している。

## 番号付き表

### 構文

番号付き表には、Container directiveの`table`を使用する。

```markdown
:::table[実行環境]{#runtime}

| 項目 | 値 |
| --- | --- |
| 言語 | `ClojureScript` |
| 実行環境 | **Node.js** |

:::
```

`table`のラベルをキャプション、`id`属性を著者が管理する論理IDとする。`table`はラベルと`id`属性を必須とし、空のラベルまたは`id`以外の属性を許可しない。Text directiveまたはLeaf directiveとして記述した`table`はエラーとする。

キャプションは空でないプレーンテキストとし、強調、インラインコード、リンク、画像、改行またはその他のMarkdownを許可しない。

番号を付けない表には、`table`で囲まず通常のGFM形式のMarkdown表を使用する。clonoは通常のMarkdown表を番号付き表へ変換せず、その構造と意味を壊さず後段へ渡す。

### 内容モデル

`table`の直下には、GFM形式のMarkdown表を一つだけ記述する。表以外の段落、複数の表、HTML表、raw HTML、他のブロックまたはdirectiveを許可しない。`table`自身を含むdirectiveの入れ子も許可しない。

GFM表として解析できるヘッダー、行、列または空のセルについて、clonoは追加の意味上の制約を設けない。Markdown解析器が一つの`table`ノードとして認識したことを、表本体の成立条件とする。

列の文字寄せはGFM表の区切り行へ記述する。clonoは`table.align`に保持された左寄せ、中央寄せ、右寄せまたは指定なしを変更せず、VFMへ渡す。

### 表内のMarkdown

表のセルでは、次のインラインMarkdownを許可する。子要素を持つノードは再帰的に検証し、内部にも同じ内容モデルを適用する。

- プレーンテキスト
- 強い強調
- 強調
- インラインコード
- インラインリンク
- 参照形式リンク
- GFM形式の取り消し線

参照形式リンクの定義は、`table`の外側へ通常のMarkdownとして記述する。

現在のclono解析器は、GFMの取り消し線専用の解析拡張を使用していない。このため、`~~取り消し線~~`は専用の`delete`ノードではなく、`~~`を含む`text`ノードとして保持される。clonoは取り消し線記法を変更せずVFMへ渡し、VFMが最終HTMLの`del`へ変換する。初期仕様では、clonoが取り消し線をAST上で通常テキストと区別して個別に検証することを保証しない。

次の内容は初期仕様では許可せず、対応するmdastノードが表のセル内に出現した場合はエラーとする。この一覧にないmdastノードも、許可する仕様を追加するまではエラーとする。

- 画像と参照形式画像
- 脚注参照
- raw HTML
- Text directive、Leaf directiveおよびContainer directive
- 強制改行

### 配置と文書種別

`table`はMarkdown文書のルート直下にだけ記述できる。コラム、文字揃え、引用、箇条書き、番号付きリスト、脚注定義またはその他のブロックの内部に記述した場合はエラーとする。

`clono build`では、`publication`に掲載され、`kind`が`chapter`または`appendix`のMarkdown原稿に`table`を記述できる。`frontmatter`または`backmatter`では通常の番号なしMarkdown表を使用する。前付または後付でも参照可能な番号なし表が必要になった場合は、IDとキャプションを持つ別の入力契約を改めて検討する。

`publication`に掲載されていないMarkdownには`table`を記述できる。clonoは番号付き表の構造へ変換するが、その論理IDを書籍全体の参照名前空間へ登録しない。この原稿では`xref`を使用できない。

`clono transform`は書籍構造と文書種別を読み込まないため、`table`が記述された文書の`kind`を検証しない。単一ファイル変換の結果を組版する利用者は、本文または付録に相当するカウンターを自身のテーマで用意する。

### 論理ID

論理IDは、次の正規表現に一致するASCII小文字、数字およびハイフンだけで構成する。

```text
^[a-z][a-z0-9-]*$
```

論理IDは英小文字で始める。大文字、アンダースコア、日本語、空白またはその他の記号を許可しない。

`clono build`では、clonoが管理する見出し、番号付き画像、番号付き表および番号付きコードリストの論理IDを書籍全体で一意とする。

clonoは論理IDから、次のHTML IDを導出する。

| 対象 | HTML ID |
| --- | --- |
| 表全体 | `table-<論理ID>` |
| キャプション | `table-<論理ID>-caption` |

論理IDが異なる場合も、導出したHTML ID同士、または他の参照対象が使用するHTML IDと衝突する場合はエラーとする。著者が直接記述したraw HTML、通常のMarkdownまたはVFMが生成する任意のIDとの衝突を、clonoが網羅的に検出することは保証しない。

### 変換後Markdownに含める構造

`clono transform`および`clono build`の直接出力は、完全なHTML文書ではなく、VFMへ渡すUTF-8のMarkdownである。番号付き表は、開始raw HTMLノード、元のGFM `table`ノード、キャプションと終了要素を持つraw HTMLノードへ変換する。

変換後Markdownは、概ね次の構造となる。

```markdown
<figure class="clono-numbered-table" id="table-runtime">

| 項目 | 値 |
| --- | --- |
| 言語 | `ClojureScript` |
| 実行環境 | **Node.js** |

<figcaption class="clono-table-caption" id="table-runtime-caption">実行環境</figcaption>
</figure>
```

Markdown直列化により、列幅をそろえる空白、区切り行のハイフン数、その他の書式が入力から変化する場合がある。入力と変換後Markdownの文字列一致は保証せず、`table`ノード、文字寄せおよび許可したインラインMarkdownの意味を保持する。

VFMは、raw HTMLを保持し、内側のMarkdown表をHTMLの`table`へ変換する。最終HTMLでは、IDとclassを持つ`figure`の直下に`table`と`figcaption`がこの順序で置かれる。これにより、通常の文書フローではキャプションを表の下へ表示する。利用者テーマが要素の表示順序または配置を変更した場合の見た目は、そのテーマの責務とする。

HTMLやMarkdownの空白と改行は規範ではない。要素の親子関係と順序、要素名、属性、class、および元の表が持つ意味を出力契約とする。

## 表参照

### 構文

参照には、画像および見出し参照と共通のText directiveである`xref`を使用する。

```markdown
詳しくは:xref[runtime]{type="table" format="number"}を参照してください。

詳しくは:xref[runtime]{type="table" format="number-title"}を参照してください。

詳しくは:xref[runtime]{type="table" format="title"}を参照してください。
```

`xref`のラベルを参照先の論理IDとする。ラベルは番号付き表と同じ論理IDの規則に従う。`type`と`format`は両方必須とし、表参照では次の値を許可する。

| 属性 | 値 | 意味 |
| --- | --- | --- |
| `type` | `table` | 番号付き表を参照する |
| `format` | `number` | `表1.1`の形式で番号だけを表示する |
| `format` | `number-title` | `表1.1 実行環境`の形式で番号とキャプションを表示する |
| `format` | `title` | `実行環境`の形式でキャプションだけを表示する |

`type`または`format`の省略、未知の値、これら以外の属性、空または不正な論理IDはエラーとする。Container directiveまたはLeaf directiveとして記述した`xref`もエラーとする。

初期仕様の`table`は本文または付録にある番号付き表だけを対象とするため、すべての表参照先は番号を持つ。通常の番号なしMarkdown表は参照対象へ登録しない。

### 解決済み参照のHTML構造

解決済み参照は、変換後Markdownへraw HTMLの空の`a`要素として埋め込む。VFMはこの要素を保持し、最終HTMLでも同じ要素と属性を出力する。

同一Markdown原稿内の表を番号とキャプションで参照する場合、次の構造へ変換する。

```html
<a
  class="clono-xref clono-xref-table clono-xref-number-title"
  href="#table-runtime"
  data-title-href="#table-runtime-caption"
></a>
```

別のMarkdown原稿にある表を参照する場合は、参照元の変換後HTMLから参照先の変換後HTMLへの相対パスを`href`と`data-title-href`へ付ける。

```html
<a
  class="clono-xref clono-xref-table clono-xref-number-title"
  href="chapter-two.html#table-runtime"
  data-title-href="chapter-two.html#table-runtime-caption"
></a>
```

すべての解決済み表参照は、次のclassを持つ。

- 参照に共通する`clono-xref`
- 表参照を示す`clono-xref-table`
- 表示形式を示す`clono-xref-number`、`clono-xref-number-title`または`clono-xref-title`

`href`は、すべての形式で表全体のHTML IDを指し、PDF内部リンクの移動先と表カウンターの取得先に使用する。`data-title-href`は、`number-title`と`title`だけに出力し、キャプションのHTML IDを指す。

Markdown原稿からHTMLへのパス変換、原稿間の相対パス、URLのパーセントエンコード、およびHTML属性値のエンコードには、[番号付き画像と画像参照仕様](figure-references.md)と同じ規則を適用する。

### 単一ファイル変換の未解決参照

`clono transform`は一つのMarkdown原稿内にあるすべての番号付き表を収集し、同一原稿内の参照を前方参照と後方参照の両方について解決する。

同一原稿内に論理IDが存在しない参照は、別原稿にある表を参照している可能性がある。単一章を執筆中に紙面を確認できるよう、この場合だけはエラーにせず、リンクを持たない`span`のプレースホルダーへ変換する。

| `format` | 表示する固定文言 |
| --- | --- |
| `number` | `表X.X` |
| `number-title` | `表X.X 参照先未解決` |
| `title` | `参照先未解決` |

例えば、`format="number-title"`の未解決参照は次の構造へ変換する。

```html
<span
  class="clono-xref clono-xref-table clono-xref-number-title clono-xref-placeholder"
>表X.X 参照先未解決</span>
```

プレースホルダーの文言は固定し、論理IDなど著者が入力した動的な値を埋め込まない。HTMLのclassにより未解決参照を機械的に識別できるようにする。論理IDの形式、属性、同一原稿内の重複ID、参照先の型またはその他の意味上の誤りは、単一ファイル変換でも通常どおりエラーとする。

この許容は`clono transform`による執筆中のプレビューだけを目的とする。`clono build`では未解決参照をプレースホルダーへ変換しない。

## 書籍プロジェクトでの収集と解決

`clono build`は、`publication`で`type`が`document`であり、拡張子を小文字化した結果が`.md`となるすべての原稿を、書籍全体の参照スコープとする。`blank-page`、通過HTMLおよび`publication`に掲載されていないMarkdownは、このスコープに含めない。

書籍プロジェクト変換では、対象原稿を個別に出力する前に、少なくとも次の段階を分離して実行する。

```text
掲載Markdownの解析
  → 番号付き画像、見出しおよび番号付き表の収集
  → 論理IDとHTML IDの検証
  → 画像、見出しおよび表の参照の収集と解決
  → 各原稿のAST変換
  → Markdownへの直列化
```

前方参照と後方参照の両方を許可する。参照先の論理IDから対象の種類と原稿を特定し、参照元と参照先の変換後HTMLパスを使って`href`と`data-title-href`を生成する。

`publication`に掲載されていないMarkdownは、番号付き表を単一文書の構造として変換できるが、そのIDを書籍全体の名前空間へ登録しない。掲載されていないMarkdownに`xref`がある場合は、書籍参照の対象外であることを診断し、単一ファイル変換用のプレースホルダーへ変換しない。

掲載Markdownに未定義参照、重複ID、文書種別に適合しない番号付き表、内容モデルの違反、解決できない出力パスまたはその他の診断が一件でもある場合は、部分的な変換計画または生成済み原稿ツリーを公開しない。診断の順序とCLI表示は[書籍プロジェクト仕様](book-project.md)に従う。

## CSSとVivliostyle

clono基盤CSSは、固定classに対して次の機能上必要な規則を提供する。

- Markdown原稿ごとに表カウンターをリセットする
- `clono-numbered-table`で表カウンターをインクリメントする
- キャプションの前へ`表<章または付録番号>.<表番号>`を表示する
- `clono-xref-number`と`clono-xref-number-title`で参照先の表番号を表示する
- `clono-xref-number-title`と`clono-xref-title`で参照先のキャプションを表示する

CSSカウンター名には、調査fixtureと同じ`table`を使用する。既存の`figure`カウンターと同じ要素で初期化するため、`body`の`counter-reset`へ`table`を追加し、既存のカウンターを上書きしない。番号付きコードリストを実装する場合も、[番号付きコードリストとコードリスト参照仕様](code-listing-references.md)に従って同じ宣言へカウンターを統合する。

clono基盤CSSは本文用の10進数による表示を既定とする。付録の英字による章番号、章および付録ごとの`chapter`カウンターの設定、表の幅、罫線、セルの余白、背景、フォント、配置、および複数ページにまたがる表の見た目は利用者テーマの責務とする。利用者テーマは、書籍の`kind`に応じてclono基盤CSSのカウンター表示を上書きできる。

clonoは表番号または参照文字列を変換時に計算して本文へ埋め込まない。Vivliostyleは、`counter()`、`target-counter()`および`target-text()`を使用して組版時に番号とキャプションを生成し、`a`要素をPDF内部リンクとして保持する。

`clono transform`のプレースホルダーだけは、書籍全体の対象を解決できないため固定文字列を本文へ出力する。プレースホルダーの`X.X`は実際の表番号を表さず、`clono build`の出力には現れない。

clono基盤CSSは、表の分割を抑制する`break-inside: avoid`を番号付き表へ指定しない。表がページ内に収まらない場合の分割と表示は、Vivliostyleおよび利用者テーマへ委譲する。ただし、初期仕様では複数ページにまたがる表の結果を保証しない。

## 安全条件

clonoが生成する要素名、属性名およびclass名は、この仕様に記載した許可リストへ固定する。著者が指定した値を要素名、属性名またはclass名として使用しない。

キャプションはHTMLのテキストとしてエンコードする。表本体は検証済みのGFM `table`ノードとしてMarkdownへ直列化し、著者が記述した値をclonoがraw HTMLとして直接連結しない。表内の通常のMarkdownリンクはVFMへ委譲し、clono独自のURL安全性を追加しない。

表参照の`href`と`data-title-href`は、検証済みの論理IDと書籍プロジェクト内の原稿パスからclonoが生成する。著者が指定した任意のURLを表参照の属性へ直接出力しない。

## 診断と出力

少なくとも次の問題を診断する。

- `table`または`xref`のdirective種別が異なる
- 必須のラベルまたは属性がない
- 未知の属性がある
- キャプションまたは論理IDが不正である
- `table`の内容モデルまたは配置が不正である
- 表のセルに許可していないmdastノードがある
- `table`が前付または後付の掲載Markdownにある
- 論理IDまたは導出したHTML IDが重複している
- `xref`の`type`または`format`が不正である
- 参照先が存在しない、または指定した参照種別と一致しない
- 掲載されていないMarkdownで`xref`を使用している
- 別原稿への相対HTMLパスを安全に生成できない

診断には、[ADR 0003](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)に従ってファイル名、行、列、directive名および問題の説明を含める。同じ文書の独立した問題は可能な範囲で収集し、入力位置の順に返す。

単一ファイル変換では診断が一件でもある場合、AST変換を実行せず、部分的な出力Markdownを返さない。書籍プロジェクト変換では、すべての掲載Markdownの解析、収集、検証および参照解決が成功した場合だけ各原稿を変換し、診断が一件でもある場合は生成済み原稿ツリーを公開しない。

## 更新方針

- 番号付き表または表参照の構文、内容モデル、ID、HTML、CSS、収集範囲、解決規則または診断契約を変更する場合は、実装と同じPull Requestでこの文書を更新する
- 実装へ着手した場合は状態を「実装中」、実装と自動テストが完了した場合は「実装済み」へ更新する
- `xref`の共通契約を変更する場合は、[番号付き画像と画像参照仕様](figure-references.md)、[見出し参照仕様](heading-references.md)、[番号付きコードリストとコードリスト参照仕様](code-listing-references.md)および[clono著者向け記法](authoring-syntax.md)を同時に見直す
- VFMのMarkdown表、Markdown AST、Vivliostyleのカウンター、対象参照またはPDF内部リンクの挙動が変わった場合は、調査fixtureを再実行し、調査記録と責務判断を同時に更新する

## 参照資料

- [プロジェクト憲章](../project-charter.md)
- [clono著者向け記法](authoring-syntax.md)
- [書籍プロジェクト仕様](book-project.md)
- [番号付き画像と画像参照仕様](figure-references.md)
- [見出し参照仕様](heading-references.md)
- [番号付きコードリストとコードリスト参照仕様](code-listing-references.md)
- [Generic DirectivesとmdastによるMarkdown変換パイプラインのADR](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [Vivliostyleとclonoの責務整理](../vivliostyle-responsibilities.md)
- [Vivliostyleの表ID・キャプション・連番・相互参照に関する調査](../research/vivliostyle-table-references.md)
- [Generic DirectivesとGFM表のMarkdown ASTに関する調査](../research/markdown-table-directive.md)
- [Vivliostyleの相互参照に関する結合検証](../research/vivliostyle-reference-integration.md)
