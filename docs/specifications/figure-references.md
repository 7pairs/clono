# 番号付き画像と画像参照仕様

- 状態: 実装中
- 作成日: 2026-09-01
- 最終更新日: 2026-09-03

## 目的

この文書は、番号付き画像を原稿へ記述し、同じ書籍に含まれる原稿から番号、番号とキャプション、またはキャプションを参照するための初期仕様を定める。

clonoは、番号付き画像を参照可能なHTML構造へ変換し、書籍に含まれるMarkdown原稿から参照対象を収集して参照先を解決する。図番号と参照文字列の生成、章および付録ごとのカウンター、PDF内部リンク、画像の表示と紙面レイアウトは、VivliostyleとCSSへ委譲する。

## 対象範囲

初期仕様では、次の機能を扱う。

- 一つの画像、参照用IDおよびキャプションを持つ番号付き画像
- 番号付き画像と通常の番号なしMarkdown画像の書き分け
- 同一Markdown原稿内の画像参照
- `publication`に掲載されたMarkdown原稿間の画像参照
- 図番号、図番号とキャプション、またはキャプションだけを表示する参照
- 単一ファイル変換における未解決参照のプレビュー用プレースホルダー
- 重複ID、未定義参照および不正な記法の診断

次の機能は初期仕様に含めない。

- 一つの図に複数の画像を含める構成
- キャプション内のMarkdown
- 参照形式のMarkdown画像
- 前付または後付における番号付き画像
- 通常の番号なしMarkdown画像への参照
- 通過HTML内にあるIDの収集または参照
- 見出し、表またはコードリストへの参照
- 著者が直接記述したraw HTMLまたはVFMが生成するすべてのIDとの衝突検査
- 画像の幅、高さ、classまたはインラインスタイルを著者向け記法で指定する機能

## 実装状況

2026年9月3日現在、番号付き画像の変換、単一Markdown原稿内の画像参照の解決、`clono transform`における未解決参照のプレースホルダー、および図番号と参照文字列に必要なclono基盤CSSを実装済みである。

`clono build`における掲載Markdown全体からの参照対象の収集、書籍全体でのID重複検査、原稿間の画像参照の解決、および書籍全体の参照スコープに基づく未定義参照の診断は未実装である。現段階では、同一原稿内で解決できない参照を暫定的に診断し、未解決の`xref`を含む出力の生成を防止する。これらが残っているため、この仕様全体の状態は「実装中」とする。

## 番号付き画像

### 構文

番号付き画像には、Container directiveの`figure`を使用する。

```markdown
:::figure[全体構成]{#architecture}
![入力、変換、出力を箱と矢印で表した図](./images/architecture.svg)
:::
```

`figure`のラベルをキャプション、`id`属性を著者が管理する論理IDとする。`figure`はラベルと`id`属性を必須とし、空のラベルまたは`id`以外の属性を許可しない。Text directiveまたはLeaf directiveとして記述した`figure`はエラーとする。

キャプションは空でないプレーンテキストとし、強調、インラインコード、リンク、画像、改行またはその他のMarkdownを許可しない。

番号を付けない画像には、`figure`で囲まず通常のMarkdown画像を使用する。clonoは通常のMarkdown画像を番号付き画像へ変換せず、その構造と意味を壊さず後段へ渡す。

### 内容モデル

`figure`の直下には、一つのMarkdown画像だけを含む段落を一つだけ記述する。段落内の通常テキスト、複数の画像、参照形式の画像、画像以外のブロック、raw HTMLまたはdirectiveを許可しない。Markdown画像の任意のタイトルも初期仕様では許可しない。

画像の代替テキストには空文字列を許可する。clonoは著者が記述した代替テキストをそのまま`img`の`alt`属性へ出力し、空の場合も`alt=""`を生成する。キャプションと代替テキストは異なる役割を持つため、一方から他方を自動補完しない。

情報を伝える画像では、その情報または目的を簡潔に表す代替テキストを記述することを推奨する。ただし、画像を見られない場合の代替情報が本文またはキャプションだけで十分かは著者が判断し、clonoは空の代替テキストをエラーまたは警告にしない。

### 配置と文書種別

`figure`はMarkdown文書のルート直下にだけ記述できる。コラム、文字揃え、引用、箇条書き、番号付きリスト、脚注定義またはその他のブロックの内部に記述した場合はエラーとする。

`clono build`では、`publication`に掲載され、`kind`が`chapter`または`appendix`のMarkdown原稿に`figure`を記述できる。`frontmatter`または`backmatter`の画像には通常の番号なしMarkdown画像を使用する。前付または後付でも参照可能な番号なし画像が必要になった場合は、IDとキャプションを持つ別の入力契約を改めて検討する。

`publication`に掲載されていないMarkdownには`figure`を記述できる。clonoは番号付き画像の構造へ変換するが、その論理IDを書籍全体の参照名前空間へ登録しない。この原稿では`xref`を使用できない。

`clono transform`は書籍構造と文書種別を読み込まないため、`figure`が記述された文書の`kind`を検証しない。単一ファイル変換の結果を組版する利用者は、本文または付録に相当するカウンターを自身のテーマで用意する。

### 論理ID

論理IDは、次の正規表現に一致するASCII小文字、数字およびハイフンだけで構成する。

```text
^[a-z][a-z0-9-]*$
```

論理IDは英小文字で始める。大文字、アンダースコア、日本語、空白またはその他の記号を許可しない。

`clono build`では、clonoが管理する参照対象の種類をまたいで、論理IDを書籍全体で一意とする。初期仕様で収集する参照対象は番号付き画像だけだが、将来、見出し、表またはコードリストを追加した場合も同じ名前空間を使用する。

clonoは論理IDから、次のHTML IDを導出する。

| 対象 | HTML ID |
| --- | --- |
| 図全体 | `figure-<論理ID>` |
| キャプション | `figure-<論理ID>-caption` |

論理IDが異なる場合も、導出したHTML ID同士が衝突する場合はエラーとする。著者が直接記述したraw HTML、通常のMarkdownまたはVFMが生成する任意のIDとの衝突を、clonoが網羅的に検出することは保証しない。

### 画像パス

Markdown画像のURLには、画像を記述したMarkdown原稿のディレクトリを基準とする相対パスだけを許可する。次のURLはエラーとする。

- `/`から始まるルート相対パス
- `//`から始まるネットワークパス参照
- `https:`、`file:`、`data:`またはその他のスキームを持つURL
- `?`によるクエリ文字列または`#`によるフラグメントを持つURL
- `\`を区切り文字に使用するパス
- 空のURL

`clono build`では、URLのパス部分を検証用にパーセントデコードし、Markdown原稿のディレクトリを基準に正規化する。不正なパーセントエンコーディング、正規化後に`sourceRoot`の外側を指すパス、存在しない対象、ディレクトリまたは通常ファイル以外の対象はエラーとする。変換後のMarkdownへは、著者が記述した相対URLを保持する。

`clono transform`では書籍プロジェクトの`sourceRoot`を持たず、画像ファイルも出力先へコピーしない。このため、URLが構文上許可された相対パスであることだけを確認し、対象の存在、ファイル種別または入力原稿ツリー内に収まることは検証しない。

### 変換後Markdownに含めるHTML構造

番号付き画像は、要素名、属性名およびclass名が固定された次の構造へ変換する。

`clono transform`および`clono build`の直接出力は、完全なHTML文書ではなく、VFMへ渡すUTF-8のMarkdownである。以下の例は、その変換後Markdownへraw HTMLとして埋め込む要素構造を示す。VFMはこのraw HTMLを保持し、最終HTMLでも同じ要素、属性および親子関係を持つ構造として出力する。

```html
<figure class="clono-numbered-figure" id="figure-architecture">
  <img
    src="./images/architecture.svg"
    alt="入力、変換、出力を箱と矢印で表した図"
  >
  <figcaption
    class="clono-figure-caption"
    id="figure-architecture-caption"
  >全体構成</figcaption>
</figure>
```

HTMLの空白と改行は規範ではない。要素の親子関係、要素名、属性およびclassを出力契約とする。

## 画像参照

### 構文

参照には、Text directiveの`xref`を使用する。

```markdown
詳しくは:xref[architecture]{type="figure" format="number"}を参照してください。

詳しくは:xref[architecture]{type="figure" format="number-title"}を参照してください。

詳しくは:xref[architecture]{type="figure" format="title"}を参照してください。
```

`xref`のラベルを参照先の論理IDとする。ラベルは番号付き画像と同じ論理IDの規則に従う。`type`と`format`は両方必須とし、初期仕様では次の値だけを許可する。

| 属性 | 値 | 意味 |
| --- | --- | --- |
| `type` | `figure` | 番号付き画像を参照する |
| `format` | `number` | `図1.1`の形式で番号だけを表示する |
| `format` | `number-title` | `図1.1 全体構成`の形式で番号とキャプションを表示する |
| `format` | `title` | `全体構成`の形式でキャプションだけを表示する |

`type`または`format`の省略、未知の値、これら以外の属性、空または不正な論理IDはエラーとする。Container directiveまたはLeaf directiveとして記述した`xref`もエラーとする。

`xref`は将来の参照種別でも共有する。見出し、表およびコードリストの参照を実装するまでは、`figure`以外の`type`を許可しない。

### 番号を持たない対象

将来、前付または後付にある見出しなど番号を持たない参照対象へ対応する場合も、同じ`xref`を使用する。番号を持たない対象では`format="title"`だけを許可し、`number`または`number-title`は表示できる番号が存在しないためエラーとする。番号を持たない対象への参照を、空の出力または表示されないリンクへ変換しない。

初期仕様の`figure`は本文または付録にある番号付き画像だけを対象とするため、この規則によって前付または後付の番号なし画像が参照可能になるわけではない。

### 解決済み参照のHTML構造

解決済み参照の`a`要素も、変換後Markdownへraw HTMLとして埋め込み、VFMを通した最終HTMLで保持する。

同一Markdown原稿内の参照では、図全体とキャプションをフラグメントで指定する。

```html
<a
  class="clono-xref clono-xref-figure clono-xref-number-title"
  href="#figure-architecture"
  data-title-href="#figure-architecture-caption"
></a>
```

別のMarkdown原稿にある図を参照する場合は、参照元の変換後HTMLから参照先の変換後HTMLへの相対パスを`href`と`data-title-href`へ付ける。

```html
<a
  class="clono-xref clono-xref-figure clono-xref-number-title"
  href="chapter-two.html#figure-architecture"
  data-title-href="chapter-two.html#figure-architecture-caption"
></a>
```

すべての参照は`clono-xref`、参照種別に応じた`clono-xref-figure`、および表示形式に応じた次のclassを持つ。

| `format` | class | `data-title-href` |
| --- | --- | --- |
| `number` | `clono-xref-number` | 出力しない |
| `number-title` | `clono-xref-number-title` | キャプションを指す値を出力する |
| `title` | `clono-xref-title` | キャプションを指す値を出力する |

`href`は、すべての形式で図全体を指し、PDF内部リンクの移動先と図カウンターの取得先に使用する。`data-title-href`はキャプションの取得先に使用する。

Markdown原稿の拡張子は大文字小文字を区別せず`.html`へ置き換える。別原稿へのパスは、参照元HTMLが置かれるディレクトリから参照先HTMLへのファイルシステム上の相対パスを計算した後、URLとして直列化する。同一原稿への参照にはHTMLファイル名を付けず、フラグメントだけを出力する。

別原稿への相対パスは`/`を区切り文字としてパス要素に分割する。相対移動を表す`.`および`..`はそのまま保持し、それ以外の各要素はUTF-8でパーセントエンコードする。ASCII英数字と`-`、`.`、`_`、`~`だけはエンコードせずに保持する。エンコード済みパスの後へ`#<HTML ID>`を付加し、完成したURLをHTML属性値としてエンコードする。`href`と`data-title-href`には同じ規則を適用する。

たとえば、参照先原稿`chapter#2.md`にある`figure-architecture`へのリンクは、同じディレクトリから参照する場合、`chapter%232.html#figure-architecture`となる。原稿名に含まれる`?`や`%`も、それぞれ`%3F`、`%25`へエンコードする。

### 単一ファイル変換の未解決参照

`clono transform`は一つのMarkdown原稿内にあるすべての番号付き画像を収集し、同一原稿内の参照を前方参照と後方参照の両方について解決する。

同一原稿内に論理IDが存在しない参照は、別原稿にある対象を参照している可能性がある。単一章を執筆中に紙面を確認できるよう、この場合だけはエラーにせず、リンクを持たない`span`のプレースホルダーへ変換する。

| `format` | 表示する固定文言 |
| --- | --- |
| `number` | `図X.X` |
| `number-title` | `図X.X 参照先未解決` |
| `title` | `参照先未解決` |

例えば、`format="number-title"`の未解決参照は次の構造へ変換する。

```html
<span
  class="clono-xref clono-xref-figure clono-xref-number-title clono-xref-placeholder"
>図X.X 参照先未解決</span>
```

プレースホルダーの文言は固定し、論理IDなど著者が入力した動的な値を埋め込まない。HTMLのclassにより未解決参照を機械的に識別できるようにする。論理IDの形式、属性、同一原稿内の重複ID、参照先の型またはその他の意味上の誤りは、単一ファイル変換でも通常どおりエラーとする。

この許容は`clono transform`による執筆中のプレビューだけを目的とする。書籍プロジェクト変換では未解決参照をプレースホルダーへ変換しない。

## 書籍プロジェクトでの収集と解決

`clono build`は、`publication`で`type`が`document`であり、拡張子を小文字化した結果が`.md`となるすべての原稿を、書籍全体の参照スコープとする。`blank-page`、通過HTMLおよび`publication`に掲載されていないMarkdownは、このスコープに含めない。

書籍プロジェクト変換では、対象原稿を個別に出力する前に、少なくとも次の段階を分離して実行する。

```text
掲載Markdownの解析
  → 番号付き画像の収集
  → IDと画像パスの検証
  → 画像参照の収集と解決
  → 各原稿のAST変換
  → Markdownへの直列化
```

前方参照と後方参照の両方を許可する。参照先の論理IDから対象原稿を特定し、参照元と参照先の変換後HTMLパスを使って`href`と`data-title-href`を生成する。

`publication`に掲載されていないMarkdownは、番号付き画像を単一文書の構造として変換できるが、そのIDを書籍全体の名前空間へ登録しない。掲載されていないMarkdownに`xref`がある場合は、書籍参照の対象外であることを診断し、単一ファイル変換用のプレースホルダーへ変換しない。

掲載Markdownに未定義参照、重複ID、文書種別に適合しない番号付き画像、解決できない出力パスまたはその他の診断が一件でもある場合は、部分的な変換計画または生成済み原稿ツリーを公開しない。診断の順序とCLI表示は[書籍プロジェクト仕様](book-project.md)に従う。

## CSSとVivliostyle

clono基盤CSSは、固定classに対して次の機能上必要な規則を提供する。

- Markdown原稿ごとに図カウンターをリセットする
- `clono-numbered-figure`で図カウンターをインクリメントする
- キャプションの前へ`図<章または付録番号>.<図番号>`を表示する
- `clono-xref-number`と`clono-xref-number-title`で参照先の図番号を表示する
- `clono-xref-number-title`と`clono-xref-title`で参照先のキャプションを表示する

CSSカウンター名には、調査fixtureと同じ`chapter`と`figure`を使用する。clono基盤CSSは本文用の10進数による表示を既定とする。付録の英字による章番号、章および付録ごとの`chapter`カウンターの設定、画像サイズ、余白、配置、フォントおよび装飾は利用者テーマの責務とする。利用者テーマは、書籍の`kind`に応じてclono基盤CSSのカウンター表示を上書きできる。

clonoは図番号または参照文字列を変換時に計算して本文へ埋め込まない。Vivliostyleは、`counter()`、`target-counter()`および`target-text()`を使用して組版時に番号とキャプションを生成し、`a`要素をPDF内部リンクとして保持する。

`clono transform`のプレースホルダーだけは、書籍全体の対象を解決できないため固定文字列を本文へ出力する。プレースホルダーの`X.X`は実際の図番号を表さず、`clono build`の出力には現れない。

## 安全条件

clonoが生成する要素名、属性名およびclass名は、この仕様に記載した許可リストへ固定する。著者が指定した値を要素名、属性名またはclass名として使用しない。

キャプション、代替テキストおよび画像URLは、それぞれHTMLのテキスト、属性値またはURL属性のコンテキストに応じてエンコードする。画像URLはこの仕様の相対パス検証を通過した値だけを`src`へ出力する。`href`と`data-title-href`は、検証済みの論理IDと書籍プロジェクト内の原稿パスからclonoが生成し、著者が指定した任意のURLを直接出力しない。

## 診断と出力

少なくとも次の問題を診断する。

- `figure`または`xref`のdirective種別が異なる
- 必須のラベルまたは属性がない
- 未知の属性がある
- キャプションまたは論理IDが不正である
- `figure`の内容モデルまたは配置が不正である
- 画像URL、画像パスまたは画像ファイルが不正である
- `figure`が前付または後付の掲載Markdownにある
- 論理IDまたは導出したHTML IDが重複している
- `xref`の`type`または`format`が不正である
- 参照先が存在しない、または指定した参照種別と一致しない
- 番号を持たない対象に番号を必要とする`format`を指定している
- 掲載されていないMarkdownで書籍参照を使用している
- 別原稿への相対HTMLパスを安全に生成できない

診断には、[ADR 0003](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)に従ってファイル名、行、列、directive名および問題の説明を含める。同じ文書の独立した問題は可能な範囲で収集し、入力位置の順に返す。

単一ファイル変換では診断が一件でもある場合、AST変換を実行せず、部分的な出力Markdownを返さない。書籍プロジェクト変換では、すべての掲載Markdownの解析、収集、検証および参照解決が成功した場合だけ各原稿を変換し、診断が一件でもある場合は生成済み原稿ツリーを公開しない。

## 更新方針

- 番号付き画像または画像参照の構文、内容モデル、ID、URL、HTML、CSS、収集範囲、解決規則または診断契約を変更する場合は、実装と同じPull Requestでこの文書を更新する
- 実装へ着手した場合は状態を「実装中」、実装と自動テストが完了した場合は「実装済み」へ更新する
- 見出し、表またはコードリストへ`xref`を拡張する場合は、対応する個別仕様を追加し、この文書と[clono著者向け記法](authoring-syntax.md)の共通契約を同時に見直す
- Vivliostyleのカウンター、対象参照またはPDF内部リンクの挙動が変わった場合は、調査fixtureを再実行し、調査記録と責務判断を同時に更新する

## 参照資料

- [プロジェクト憲章](../project-charter.md)
- [clono著者向け記法](authoring-syntax.md)
- [書籍プロジェクト仕様](book-project.md)
- [Generic DirectivesとmdastによるMarkdown変換パイプラインのADR](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [Vivliostyleとclonoの責務整理](../vivliostyle-responsibilities.md)
- [Vivliostyleの画像ID・キャプション・連番・相互参照に関する調査](../research/vivliostyle-figure-references.md)
- [Vivliostyleの相互参照に関する結合検証](../research/vivliostyle-reference-integration.md)
