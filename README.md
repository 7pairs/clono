# clono

[![CI](https://github.com/7pairs/clono/actions/workflows/ci.yml/badge.svg)](https://github.com/7pairs/clono/actions/workflows/ci.yml)

`clono`（クロノ）は、Markdownに独自の意味を安全に付加し、構造化された変換パイプラインを通して目的の形式へ変換するためのMarkdown変換基盤です。

書籍制作に必要な独自記法を、正規表現による文字列置換ではなくASTなどの構造化表現を介して処理します。局所的な構文変換に加え、索引や相互参照のように文書全体の情報を収集・解決する処理を扱い、将来は利用者が変換処理を追加または差し替えられる仕組みの提供を目指します。

Vivliostyleを主要な出力先として想定し、Vivliostyleが持つ機能を生かしながら、その前段で文書の構文と意味を扱うことに集中します。

## 開発状況

現在は開発初期段階です。Node.js向けCLIで単一のMarkdownファイルと、複数の原稿・静的ファイルからなる書籍プロジェクトを変換できます。独自記法は垂直余白記法、強制改ページ記法、文字揃え記法、定義リスト記法、コラム記法、番号付き画像と画像参照、見出し参照、番号付き表と表参照、番号付きコードリストとコードリスト参照、および索引指定を実装しています。書籍プロジェクトでは、掲載Markdownから索引項目を収集して索引文書を生成できます。

npmパッケージとしての公開はまだ行っていません。現時点のCLIは、開発環境でビルドして利用します。

## 必要な環境

開発には次の環境が必要です。

| ソフトウェア | バージョン | 用途 |
| --- | --- | --- |
| Node.js | 24.19.0 | 開発時の基準環境 |
| npm | Node.jsに同梱されるバージョン | 依存関係と開発用コマンドの管理 |
| Temurin JDK | 21.0.12 | ClojureScriptのビルド |

生成されたCLIの実行時にJDKは必要ありません。将来の利用者向けには、Node.js 22.13.0以降の22系および24系をサポートする方針です。

## 開発環境のセットアップ

リポジトリを取得します。

```shell
git clone https://github.com/7pairs/clono.git
cd clono
```

このリポジトリには、Node.jsのバージョンを指定する`.node-version`と、JDKのバージョンを指定する`.sdkmanrc`が含まれています。nodenvとSDKMANを使用する場合は、次のコマンドで開発環境を準備できます。

```shell
nodenv install
sdk env install
npm ci
```

これらのバージョン管理ツールは必須ではありません。使用しない場合は、必要なバージョンのNode.jsとJDKを別の方法で用意してから`npm ci`を実行してください。

## 開発用コマンド

| コマンド | 内容 |
| --- | --- |
| `npm run build` | CLIのdevelopmentビルドを一度実行する |
| `npm run build:release` | CLIのreleaseビルドを実行する |
| `npm run watch` | CLIを監視し、変更時に再ビルドする |
| `npm test` | Node.js上でテストを一度実行する |
| `npm run test:watch` | テストを監視し、変更時に再実行する |

CLIをビルドして起動するには、次のコマンドを実行します。

```shell
npm run build
node dist/clono.js --help
```

## 単一ファイルの変換

入力Markdownと出力先を指定して、単一のファイルを変換します。

```shell
node dist/clono.js transform manuscript.md --output build/manuscript.md
```

出力先の親ディレクトリは、コマンドを実行する前に作成してください。既存の出力ファイルは上書きします。変換に成功した場合は何も表示せず、診断またはファイル操作エラーが発生した場合は標準エラーへ問題を表示して終了コード`1`を返します。

利用可能な記法と詳しいCLIの契約は、[clono著者向け記法](docs/specifications/authoring-syntax.md)と[単一ファイル変換CLI仕様](docs/specifications/single-file-cli.md)を参照してください。

`transform`サブコマンドは、独自記法が生成する構造、図番号、表番号、リスト番号、画像参照、見出し参照、表参照およびコードリスト参照に必要な`styles/clono.css`を出力先へコピーしません。単一ファイルの出力を組版する場合は、利用者が基盤CSSを組み込んでください。

## 基本表現

Generic Directivesを使用して、書籍制作向けの基本表現を指定できます。

| 機能 | 記法 | 用途 |
| --- | --- | --- |
| 垂直余白 | `::space` | 二つの通常段落の間へ本文一行相当の余白を追加する |
| 強制改ページ | `::page-break` | 後続のトップレベルブロックを次ページから開始する |
| 文字揃え | `:::align{position="right"}`と`:::`で囲む | 複数の段落をまとめて右寄せする |
| 定義リスト | `::::definition-list`の内側へ`:::definition`と`::term[用語]`を記述する | 用語と説明の組を列挙する |
| コラム | `:::column[タイトル]`と`:::`で囲む | タイトル付きの補足や雑談を本文から区別する |

垂直余白は、文書の先頭・末尾や連続した位置ではなく、二つの通常段落の間へ記述します。

```markdown
通常の本文。

::space

ここぞという決めの一言。
```

定義リストでは、一つの項目を一つの用語と一つの説明段落で構成します。

````markdown
::::definition-list
:::definition
::term[`READY`]

処理を開始できる**待機状態**。
:::

:::definition
::term[DONE]

処理が正常に完了した状態を表す。
:::
::::
````

各記法で許可する配置、属性、Markdown要素および診断の契約は、[clono著者向け記法](docs/specifications/authoring-syntax.md)を参照してください。

## 番号付き画像と画像参照

Container directiveの`figure`で、参照用IDとキャプションを持つ番号付き画像を記述できます。Text directiveの`xref`では、図番号、図番号とキャプション、またはキャプションだけを参照できます。

```markdown
:::figure[全体構成]{#architecture}
![入力、変換、出力を箱と矢印で表した図](./images/architecture.svg)
:::

詳しくは:xref[architecture]{type="figure" format="number-title"}を参照してください。
```

`transform`サブコマンドは同じ入力Markdownにある画像参照を解決します。同じ原稿に参照先がない場合は、別原稿への参照を含む章を単独でプレビューできるよう、固定のプレースホルダーへ変換します。

`build`サブコマンドは、`publication`に掲載されたすべてのMarkdownから番号付き画像を収集し、同一原稿および原稿間の画像参照を解決します。重複したID、未定義参照または安全に生成できない原稿間リンクがある場合は診断し、生成済み原稿ツリーを変更しません。

入力契約、参照形式、変換後の構造および制限事項は、[番号付き画像と画像参照仕様](docs/specifications/figure-references.md)を参照してください。

## 番号付き表と表参照

Container directiveの`table`で、GFM形式のMarkdown表に参照用IDとキャプションを付け、番号付き表として記述できます。Text directiveの`xref`では、表番号、表番号とキャプション、またはキャプションだけを参照できます。

```markdown
:::table[実行環境]{#runtime}

| 項目 | 値 |
| --- | --- |
| 言語 | ClojureScript |
| 実行環境 | Node.js |

:::

詳しくは:xref[runtime]{type="table" format="number-title"}を参照してください。
```

`transform`サブコマンドは同じ入力Markdownにある表参照を解決します。同じ原稿に参照先がない場合は、別原稿への参照を含む章を単独でプレビューできるよう、表示形式に応じた固定のプレースホルダーへ変換します。

`build`サブコマンドは、`publication`に掲載されたすべてのMarkdownから番号付き表を収集し、同一原稿および原稿間の表参照を解決します。重複したID、未定義参照、参照種別の不一致または安全に生成できない原稿間リンクがある場合は診断し、生成済み原稿ツリーを変更しません。

表番号と参照文字列は、clono基盤CSSが利用者テーマのCSSカウンターを参照して生成します。番号付き表は本文と付録で使用できます。入力契約、表内で使用できるMarkdown、変換後の構造および制限事項は、[番号付き表と表参照仕様](docs/specifications/table-references.md)を参照してください。

## 番号付きコードリストとコードリスト参照

Container directiveの`listing`で、フェンス付きコードブロックに参照用IDとキャプションを付け、番号付きコードリストとして記述できます。Text directiveの`xref`では、リスト番号、リスト番号とキャプション、またはキャプションだけを参照できます。

````markdown
:::listing[挨拶を表示する関数]{#greeting}

```kotlin
fun greet() {
    println("Hello")
}
```

:::

詳しくは:xref[greeting]{type="listing" format="number-title"}を参照してください。
````

コードフェンスの言語指定は任意です。番号を付けないコードブロックには、`listing`で囲まない通常のコードフェンスを使用します。

`transform`サブコマンドは同じ入力Markdownにあるコードリスト参照を解決します。同じ原稿に参照先がない場合は、別原稿への参照を含む章を単独でプレビューできるよう、表示形式に応じた固定のプレースホルダーへ変換します。

`build`サブコマンドは、`publication`に掲載されたすべてのMarkdownから番号付きコードリストを収集し、同一原稿および原稿間のコードリスト参照を解決します。重複したID、未定義参照、参照種別の不一致または安全に生成できない原稿間リンクがある場合は診断し、生成済み原稿ツリーを変更しません。

リスト番号と参照文字列は、clono基盤CSSが利用者テーマのCSSカウンターを参照して生成します。番号付きコードリストは本文と付録で使用できます。入力契約、コードフェンスの制限、変換後の構造およびCSSの責務は、[番号付きコードリストとコードリスト参照仕様](docs/specifications/code-listing-references.md)を参照してください。

## 見出し参照

VFMの明示的なIDを持つ`h1`から`h3`までの見出しを、画像・表参照と共通のText directiveで参照できます。表示形式には、見出し番号、見出し番号とタイトル、またはタイトルだけを指定できます。

```markdown
# はじめに {#introduction}

詳しくは:xref[introduction]{type="heading" format="number-title"}を参照してください。
```

`transform`サブコマンドは同じ入力Markdownにある見出し参照を解決します。同じ原稿に参照先がない場合は、別原稿への参照を含む章を単独でプレビューできるよう、表示形式に応じた固定のプレースホルダーへ変換します。

`build`サブコマンドは、`publication`に掲載されたすべてのMarkdownから明示ID付き見出しを収集し、同一原稿および原稿間の見出し参照を解決します。本文と付録では、参照先の`kind`と見出しレベルに応じた番号を表示できます。番号を持たない前付と後付では、タイトルだけを参照できます。

見出し番号と参照文字列は、clono基盤CSSが利用者テーマのCSSカウンターを参照して生成します。書籍プロジェクトでは生成済み原稿ツリーの`_clono/styles/clono.css`を、利用者テーマとともにVivliostyleへ読み込んでください。入力契約、番号形式、変換後の構造およびCSSの責務は、[見出し参照仕様](docs/specifications/heading-references.md)を参照してください。

## 索引

Text directiveの`index`で、本文に表示する索引語と、分類・並べ替えに使用する読みを指定できます。

```markdown
clonoは:index[索引]{reading="さくいん"}を生成できます。
```

`transform`サブコマンドは、単一の入力Markdownにある索引指定を本文マーカーへ変換しますが、索引文書は生成しません。

`build`サブコマンドは、`publication`に掲載されたMarkdownから索引指定を収集します。読みを正規化し、英数字と五十音の行へ分類して決定的に並べ替え、同じ索引項目を統合したうえで、`publication`の`index`エントリで指定した`path`へ索引Markdownを生成します。掲載Markdownに索引指定がある場合は`index`の設定が必要です。索引指定がなくても、`index`が設定されていればタイトルだけを持つ空の索引を生成します。

索引に表示する紙面上のページ番号は、生成済み原稿ツリーの`_clono/styles/clono.css`とVivliostyleが生成します。各ページ番号から出現位置へのPDF内部リンクはVivliostyleが保持します。分類見出し、段組み、余白などの紙面デザインは、利用者テーマで指定してください。著者向け記法、読みの正規化、分類、生成構造、設定および制限事項は、[索引仕様](docs/specifications/index.md)を参照してください。

## 書籍プロジェクトの変換

書籍プロジェクトのルートに`clono.config.mjs`を作成し、入力原稿ルート、生成済み原稿ルートおよび原稿順序を指定します。

```javascript
export default {
  sourceRoot: "manuscripts",
  outputRoot: "build/manuscripts",
  publication: [
    {
      type: "document",
      path: "introduction.md",
      kind: "frontmatter",
      includeInToc: true,
    },
    { type: "blank-page" },
    {
      type: "document",
      path: "chapter-one.md",
      kind: "chapter",
      includeInToc: true,
    },
    {
      type: "index",
      path: "generated/index.md",
      title: "索引",
      includeInToc: true,
    },
  ],
};
```

`clono.config.mjs`は通常のJavaScriptとして実行されるため、信頼できる書籍プロジェクトの設定だけを読み込んでください。

カレントディレクトリ、または指定した書籍プロジェクトを変換します。

```shell
node dist/clono.js build
node dist/clono.js build path/to/book
```

`build`サブコマンドは、入力原稿ツリーのMarkdownを変換し、その他の通常ファイルを相対パスのままコピーします。`publication`には原稿を表す`document`に加えて、原稿間へ一ページ挿入する`blank-page`と、索引Markdownを生成する`index`を指定できます。掲載されたMarkdown全体から番号付き画像、明示ID付き見出し、番号付き表および番号付きコードリストを収集し、同一原稿および原稿間の参照を解決します。また、索引指定を収集・正規化・統合し、設定された位置へ索引Markdownを生成します。生成済み原稿ツリーには、必要な空白ページHTML、clono基盤CSSおよび所有マーカーも配置します。既存出力は、一致する所有マーカーを持つ場合だけ安全に置き換えます。成功時は何も表示せず、診断がある場合は部分的な出力を公開しません。

`build`サブコマンドはWebPubまたはPDFを生成せず、`vivliostyle.config.mjs`も生成・変更しません。生成済み原稿ツリーと、書籍プロジェクト側で管理するVivliostyle設定および利用者テーマを組み合わせて組版してください。

設定項目、出力先の保護、Vivliostyle設定との連携方法は、[書籍プロジェクト仕様](docs/specifications/book-project.md)を参照してください。

## ドキュメント

- [プロジェクト憲章](docs/project-charter.md): プロジェクトの目的、設計原則、開発方針
- [開発ワークフロー](docs/development-workflow.md): Sora Flowによる通常開発とリリースのブランチ戦略
- [設計判断の記録](docs/decisions/): 採用した技術や方針と、その判断理由
- [著者向け記法](docs/specifications/authoring-syntax.md): 利用可能な記法と開発状態
- [単一ファイル変換CLI仕様](docs/specifications/single-file-cli.md): CLIの入出力、診断、終了コード
- [書籍プロジェクト仕様](docs/specifications/book-project.md): 複数原稿の設定、変換、基盤CSSおよび出力保護
- [番号付き画像と画像参照仕様](docs/specifications/figure-references.md): 番号付き画像の入力、画像参照の解決、診断および出力構造
- [番号付き表と表参照仕様](docs/specifications/table-references.md): 番号付き表の入力、表参照の解決、診断および出力構造
- [番号付きコードリストとコードリスト参照仕様](docs/specifications/code-listing-references.md): 番号付きコードリストの入力、コードリスト参照の解決、診断および出力構造
- [見出し参照仕様](docs/specifications/heading-references.md): 見出しIDの収集、見出し参照の解決、診断および出力構造
- [索引仕様](docs/specifications/index.md): 索引指定、読みの正規化、分類、索引文書の生成およびVivliostyleとの責務分担

## ライセンス

`clono`は[Apache License 2.0](LICENSE)で提供します。
