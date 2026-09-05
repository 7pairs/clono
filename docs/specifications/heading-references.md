# 見出し参照仕様

- 状態: 実装中
- 作成日: 2026-09-05
- 最終更新日: 2026-09-05

## 目的

この文書は、Markdown原稿の見出しへVFMの明示的なIDを指定し、同じ書籍に含まれる原稿から番号、番号とタイトル、またはタイトルを参照するための初期仕様を定める。

clonoは、参照対象となる見出しを収集して参照先を解決し、著者向けの参照記法をVFMが保持できるリンク構造へ変換する。見出しIDのHTMLへの出力、見出し番号と参照文字列の生成、PDF内部リンク、および紙面レイアウトはVFM、VivliostyleとCSSへ委譲する。

## 対象範囲

初期仕様では、次の機能を扱う。

- ATX形式の`h1`、`h2`および`h3`へ指定したVFMの明示的な見出しID
- 同一Markdown原稿内の見出し参照
- `publication`に掲載されたMarkdown原稿間の見出し参照
- 本文の章、節、小節と、付録の章、節、小節に対する番号参照
- 前付と後付にある番号なし見出しに対するタイトル参照
- 番号、番号とタイトル、またはタイトルだけを表示する参照
- 単一ファイル変換における未解決参照のプレビュー用プレースホルダー
- 重複ID、未定義参照および不正な記法の診断

次の機能は初期仕様に含めない。

- `h4`以降の見出し参照
- Setext形式の見出しに対する明示IDの収集と参照
- IDとclassなど、複数のVFM属性を持つ見出しの参照
- IDのない見出しに対するVFMの自動生成IDの収集と参照
- 一つのMarkdown原稿に複数の章を記述する構成の検証
- 見出し階層の飛躍、各原稿の`h1`の個数、または見出しの親子関係の検証
- 通過HTML内にある見出しまたはIDの収集と参照
- 著者が直接記述したraw HTMLまたはVFMが生成するすべてのIDとの衝突検査

## 実装状況

2026年9月5日現在、VFMの見出しID、見出し番号、同一原稿および原稿間の参照、ならびにclonoが使用するMarkdown ASTの形を調査済みである。

見出し参照の実装に着手し、最初の段階として見出しの収集とIDの検証を進める。`xref`の`heading`種別、参照の解決、プレビュー用プレースホルダー、および見出し参照用のclono基盤CSSを含む仕様全体の実装と自動テストが完了するまで、状態を「実装中」とする。

## 参照対象となる見出し

### 構文

参照対象とする見出しには、VFMの明示的な見出しID記法を使用する。

```markdown
# はじめに {#introduction}

## clonoの構造 {#clono-structure}

### 変換パイプライン {#transformation-pipeline}
```

clono独自の見出しID記法は追加しない。見出しの末尾に一個以上の空白を置き、その後へ単独の`{#<論理ID>}`を記述する。見出しIDの後ろに空白以外の文字を記述しない。

初期仕様で参照対象として収集するのは、ATX形式で記述した`h1`、`h2`および`h3`だけとする。IDのない見出しは通常の見出しとして許可し、参照対象へ登録しない。

`h4`以降、Setext形式、複数の属性、エスケープしたID記法、または初期仕様に含まれないその他のVFM属性構文は、clonoの見出し参照対象へ登録せず、入力の意味を変えずにVFMへ渡す。初期仕様の対象外となる見出しIDの有効性や重複は、この機能では診断しない。

### タイトル

見出しタイトルには、VFMが見出し内で受理するインラインMarkdownを記述できる。clonoはタイトルをプレーンテキストへ変換したり、参照要素へ複製したりしない。

番号とタイトルまたはタイトルだけを参照する場合、Vivliostyleの`target-text()`が最終HTMLの見出し要素から表示上のテキストを取得する。見出しID記法そのものはVFMがHTMLの`id`属性として扱うため、参照タイトルへ含まれない。

### 論理IDとHTML ID

見出しの論理IDは、番号付き画像と同じ次の正規表現に一致するASCII小文字、数字およびハイフンだけで構成する。

```text
^[a-z][a-z0-9-]*$
```

論理IDは英小文字で始める。大文字、アンダースコア、日本語、空白またはその他の記号を許可しない。初期仕様の対象となる見出しの末尾に`{#...}`の形を持つID候補があり、その値がこの規則に一致しない場合は診断する。

VFMが見出し要素へ出力するHTML IDには、論理IDを接頭辞なしでそのまま使用する。clonoは見出しIDを削除、変更または別のIDへ置換しない。

`clono build`では、clonoが管理する見出し、番号付き画像、および将来追加する表やコードリストを含め、論理IDを書籍全体で一意とする。また、論理IDが異なる場合も、次のように見出しのHTML IDと他の参照対象から導出したHTML IDが衝突する場合はエラーとする。

```text
# 見出し {#figure-architecture}
:::figure[全体構成]{#architecture}
```

この例では、見出しのHTML IDと図全体のHTML IDが、どちらも`figure-architecture`になる。図のキャプションなど、参照対象から導出するその他のHTML IDとの衝突も同じように検査する。

### 文書種別と番号

`clono build`では、`publication`に掲載されたMarkdown原稿の`kind`と見出しレベルから、見出しが持つ番号の形式を決定する。

| `kind` | 見出し | 番号の形式 | 例 |
| --- | --- | --- | --- |
| `chapter` | `h1` | 本文の章番号 | `第2章` |
| `chapter` | `h2` | 章番号と節番号 | `2.1` |
| `chapter` | `h3` | 章番号、節番号と小節番号 | `2.1.1` |
| `appendix` | `h1` | 付録番号 | `付録A` |
| `appendix` | `h2` | 付録番号と節番号 | `A.1` |
| `appendix` | `h3` | 付録番号、節番号と小節番号 | `A.1.1` |
| `frontmatter` | `h1`から`h3` | 番号なし | なし |
| `backmatter` | `h1`から`h3` | 番号なし | なし |

番号はclonoが計算または変換後Markdownへ埋め込まず、書籍の原稿順序、文書種別、見出しレベルおよびCSSカウンターを使用してVivliostyleが組版時に生成する。

一つのMarkdown原稿を一つの章、付録または番号なし文書として扱うことを前提とする。ただし、clonoは原稿内の`h1`の個数、`h2`または`h3`の親見出し、および見出し階層の連続性を検証しない。

`clono transform`では、対象構文に一致する見出しIDを単一文書内の参照対象として収集できる。`clono build`では、`publication`に掲載されていないMarkdownにある見出しIDを書籍全体の参照名前空間へ登録せず、その原稿で`xref`を使用した場合はエラーとする。

## 見出し参照

### 構文

参照には、画像参照と共通のText directiveである`xref`を使用する。

```markdown
詳しくは:xref[introduction]{type="heading" format="number"}を参照してください。

詳しくは:xref[clono-structure]{type="heading" format="number-title"}を参照してください。

詳しくは:xref[transformation-pipeline]{type="heading" format="title"}を参照してください。
```

`xref`のラベルを参照先の論理IDとする。ラベルは見出しと同じ論理IDの規則に従う。`type`と`format`は両方必須とし、見出し参照では次の値を許可する。

| 属性 | 値 | 意味 |
| --- | --- | --- |
| `type` | `heading` | 見出しを参照する |
| `format` | `number` | 見出しの番号だけを表示する |
| `format` | `number-title` | 見出しの番号、半角スペース、タイトルの順で表示する |
| `format` | `title` | 見出しのタイトルだけを表示する |

`type`または`format`の省略、未知の値、これら以外の属性、空または不正な論理IDはエラーとする。Container directiveまたはLeaf directiveとして記述した`xref`もエラーとする。

### 番号を持たない見出し

`frontmatter`または`backmatter`にある見出しは番号を持たないため、`format="title"`だけを許可する。`format="number"`または`format="number-title"`を指定した場合は、表示できる番号が存在しないためエラーとする。

番号を持たない見出しへの参照も、タイトルを表示するクリック可能なリンクへ変換する。空の出力または表示されないリンクへ変換しない。

### 解決済み参照のHTML構造

解決済み参照は、変換後Markdownへraw HTMLの空の`a`要素として埋め込む。VFMはこの要素を保持し、最終HTMLでも同じ要素と属性を出力する。

同一Markdown原稿の本文にある`h2`を番号とタイトルで参照する場合、次の構造へ変換する。

```html
<a
  class="clono-xref clono-xref-heading clono-xref-heading-h2 clono-xref-heading-chapter clono-xref-number-title"
  href="#clono-structure"
  data-title-href="#clono-structure"
></a>
```

すべての解決済み見出し参照は、次のclassを持つ。

- 参照に共通する`clono-xref`
- 見出し参照を示す`clono-xref-heading`
- 見出しレベルを示す`clono-xref-heading-h1`、`clono-xref-heading-h2`または`clono-xref-heading-h3`
- 文書種別を示す`clono-xref-heading-chapter`、`clono-xref-heading-appendix`または`clono-xref-heading-unnumbered`
- 表示形式を示す`clono-xref-number`、`clono-xref-number-title`または`clono-xref-title`

`clono build`は参照先の`kind`から文書種別のclassを決定する。`clono transform`は書籍構造を読み込まないため、同一原稿内で解決できた見出しを本文相当とみなし、`clono-xref-heading-chapter`を付ける。

`href`は、すべての形式で見出しのHTML IDを指し、PDF内部リンクの移動先と見出しカウンターの取得先に使用する。`data-title-href`は、`number-title`と`title`だけに出力し、`href`と同じ見出しのHTML IDを指す。

別のMarkdown原稿にある見出しを参照する場合は、参照元の変換後HTMLから参照先の変換後HTMLへの相対パスを`href`と`data-title-href`へ付ける。

```html
<a
  class="clono-xref clono-xref-heading clono-xref-heading-h1 clono-xref-heading-appendix clono-xref-number-title"
  href="appendix-a.html#additional-information"
  data-title-href="appendix-a.html#additional-information"
></a>
```

Markdown原稿からHTMLへのパス変換、原稿間の相対パス、URLのパーセントエンコード、およびHTML属性値のエンコードには、[番号付き画像と画像参照仕様](figure-references.md)と同じ規則を適用する。

### 単一ファイル変換の未解決参照

`clono transform`は、一つのMarkdown原稿内にある初期仕様の対象となる見出しIDを収集し、同一原稿内の参照を前方参照と後方参照の両方について解決する。

同一原稿内に論理IDが存在しない参照は、別原稿にある見出しを参照している可能性がある。単一章を執筆中に紙面を確認できるよう、この場合だけはエラーにせず、リンクを持たない`span`のプレースホルダーへ変換する。

| `format` | 表示する固定文言 |
| --- | --- |
| `number` | `見出し番号未解決` |
| `number-title` | `見出し参照先未解決` |
| `title` | `参照先未解決` |

例えば、`format="number-title"`の未解決参照は次の構造へ変換する。

```html
<span
  class="clono-xref clono-xref-heading clono-xref-number-title clono-xref-placeholder"
>見出し参照先未解決</span>
```

参照先を取得できないため、プレースホルダーへ見出しレベルまたは文書種別のclassを付けない。固定文言には、論理IDなど著者が入力した動的な値を埋め込まない。

論理IDの形式、属性、同一原稿内の重複ID、参照先の型またはその他の意味上の誤りは、単一ファイル変換でも通常どおりエラーとする。この許容は`clono transform`による執筆中のプレビューだけを目的とし、`clono build`では未解決参照をプレースホルダーへ変換しない。

## 書籍プロジェクトでの収集と解決

`clono build`は、`publication`で`type`が`document`であり、拡張子を小文字化した結果が`.md`となるすべての原稿を、書籍全体の参照スコープとする。`blank-page`、通過HTMLおよび`publication`に掲載されていないMarkdownは、このスコープに含めない。

書籍プロジェクト変換では、対象原稿を個別に出力する前に、少なくとも次の段階を分離して実行する。

```text
掲載Markdownの解析
  → 番号付き画像と見出しの収集
  → 論理IDとHTML IDの検証
  → 画像参照と見出し参照の収集および解決
  → 各原稿のAST変換
  → Markdownへの直列化
```

前方参照と後方参照の両方を許可する。参照先の論理IDから対象の種類、原稿、文書種別および見出しレベルを特定し、参照元と参照先の変換後HTMLパスを使って`href`と`data-title-href`を生成する。

`publication`に掲載されていないMarkdownは、対象構文に一致する見出しIDを単一文書の参照対象として収集できるが、そのIDを書籍全体の名前空間へ登録しない。掲載されていないMarkdownに`xref`がある場合は、書籍参照の対象外であることを診断し、単一ファイル変換用のプレースホルダーへ変換しない。

掲載Markdownに未定義参照、重複ID、参照種別の不一致、番号を持たない見出しへの番号参照、解決できない出力パスまたはその他の診断が一件でもある場合は、部分的な変換計画または生成済み原稿ツリーを公開しない。診断の順序とCLI表示は[書籍プロジェクト仕様](book-project.md)に従う。

## CSSとVivliostyle

clono基盤CSSは、固定classに対して次の機能上必要な規則を提供する。

- 本文の`h1`参照を`第<章番号>章`の形式で表示する
- 本文の`h2`と`h3`参照を`<章番号>.<節番号>`と`<章番号>.<節番号>.<小節番号>`の形式で表示する
- 付録の`h1`参照を`付録<英字>`の形式で表示する
- 付録の`h2`と`h3`参照を`<英字>.<節番号>`と`<英字>.<節番号>.<小節番号>`の形式で表示する
- `number-title`では番号の後ろに半角スペースと見出しタイトルを表示する
- `title`では見出しタイトルだけを表示する

CSSカウンター名には、調査fixtureと同じ`chapter`、`appendix`、`section`および`subsection`を使用する。タイトルは`target-text(attr(data-title-href url), content)`で取得する。

clonoは見出し番号または参照文字列を変換時に計算して本文へ埋め込まない。CSSカウンターの初期化、更新、および見出し自体への番号表示は利用者テーマの責務とする。clono基盤CSSは、利用者テーマが管理するカウンターを`target-counter()`で参照し、見出し参照の文字列を生成する。

`frontmatter`と`backmatter`の見出し参照では、`format="title"`だけを許可し、番号用のCSSカウンターを参照しない。

## 安全条件

clonoが生成する要素名、属性名およびclass名は、この仕様に記載した許可リストへ固定する。著者が指定した値を要素名、属性名またはclass名として使用しない。

`href`と`data-title-href`は、検証済みの論理IDと書籍プロジェクト内の原稿パスからclonoが生成し、著者が指定した任意のURLを直接出力しない。URLは相対パスとフラグメントだけで構成し、URLとして直列化した後にHTML属性値としてエンコードする。

プレースホルダーの本文には仕様で固定した文字列だけを使用し、著者が指定した論理IDを出力しない。

## 診断と出力

少なくとも次の問題を診断する。

- 初期仕様の対象となる見出しのID候補が論理IDの規則に一致しない
- clonoが管理する論理IDまたはHTML IDが重複している
- `xref`のdirective種別、ラベル、`type`または`format`が不正である
- 参照先が存在しない、または指定した参照種別と一致しない
- 番号を持たない見出しに`number`または`number-title`を指定している
- 掲載されていないMarkdownで`xref`を使用している
- 別原稿への相対HTMLパスを安全に生成できない

`xref`の診断には、[ADR 0003](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)に従ってファイル名、行、列、directive名および問題の説明を含める。見出しIDの診断にはファイル名、行、列および問題の説明を含め、通常のMarkdown構文であるためdirective名は含めない。同じ文書の独立した問題は可能な範囲で収集し、入力位置の順に返す。

単一ファイル変換では診断が一件でもある場合、AST変換を実行せず、部分的な出力Markdownを返さない。書籍プロジェクト変換では、すべての掲載Markdownの解析、収集、検証および参照解決が成功した場合だけ各原稿を変換し、診断が一件でもある場合は生成済み原稿ツリーを公開しない。

## 更新方針

- 見出しIDまたは見出し参照の構文、対象レベル、文書種別、番号形式、HTML、CSS、収集範囲、解決規則または診断契約を変更する場合は、実装と同じPull Requestでこの文書を更新する
- 実装へ着手した場合は状態を「実装中」、実装と自動テストが完了した場合は「実装済み」へ更新する
- `xref`の共通契約を変更する場合は、[番号付き画像と画像参照仕様](figure-references.md)と[clono著者向け記法](authoring-syntax.md)を同時に見直す
- VFMの見出しID、Markdown AST、Vivliostyleのカウンター、対象参照またはPDF内部リンクの挙動が変わった場合は、調査fixtureを再実行し、調査記録と責務判断を同時に更新する

## 参照資料

- [プロジェクト憲章](../project-charter.md)
- [clono著者向け記法](authoring-syntax.md)
- [書籍プロジェクト仕様](book-project.md)
- [番号付き画像と画像参照仕様](figure-references.md)
- [Generic DirectivesとmdastによるMarkdown変換パイプラインのADR](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [Vivliostyleとclonoの責務整理](../vivliostyle-responsibilities.md)
- [Vivliostyleの見出しID・連番・相互参照に関する調査](../research/vivliostyle-heading-references.md)
- [VFM見出しIDのMarkdown ASTに関する調査](../research/markdown-heading-ids.md)
- [Vivliostyleの相互参照に関する結合検証](../research/vivliostyle-reference-integration.md)
- [Vivliostyleの目次に関する調査](../research/vivliostyle-table-of-contents.md)
