# Vivliostyleにおけるclono基盤CSSと利用者テーマの統合に関する調査

- 状態: 調査済み
- 調査日: 2026-08-25
- 検証環境:
  - OS: macOS 26.5.2
  - Node.js: 24.19.0
  - npm: 11.17.0
  - WebPub・PDF生成: `@vivliostyle/cli` 11.1.0
    - CLIが依存する`@vivliostyle/vfm` 2.7.0
    - CLIが使用するVivliostyle.js 2.44.1
  - PDF検証: `mupdf` 1.28.0

## 背景

clonoは、文字揃え、コラム、強制改ページなどの著者向け記法を、Vivliostyleが処理できるHTML構造を含むMarkdownへ変換する。変換後の構造を意図した機能として成立させるため、npmパッケージ内の`styles/clono.css`へ最小限の基盤CSSを同梱している。

単一ファイル変換CLIはMarkdownだけを出力し、基盤CSSを出力先へコピーしない。一方、書籍プロジェクト変換はclonoが所有する生成済み原稿ツリーを作るため、実行中のclonoと対応する基盤CSSをそのツリーへ配置する余地がある。

基盤CSSはclonoが生成する構造の機能上必要な既定値を提供するが、書籍固有の紙面設計を制限してはならない。特に、利用者のテーマCSSを後から適用し、clonoの既定値を上書きできることを必須要件とする。

今回の調査では、次の二方式を比較した。

- clonoのnpmパッケージをVivliostyleのテーマとして指定し、パッケージ内の基盤CSSを読み込む
- `clono build`が基盤CSSを生成済み原稿ツリーの予約領域へコピーし、通常のCSSファイルとして読み込む

また、次の点を検証した。

- clonoの基盤CSSと利用者テーマを指定した順序で適用できるか
- 利用者テーマからclonoの既定値を上書きできるか
- 配布可能なWebPubへ必要なCSSを収録できるか
- 生成済み原稿ツリー内の隠しディレクトリと可視ディレクトリで収録結果が異なるか
- npmパッケージ方式を用いない場合、Vivliostyleによる追加のテーマ導入を避けられるか

## 比較した統合方式

### npmパッケージをテーマとして指定する

Vivliostyle CLI 11.1.0の設定では、`theme`へ複数のテーマを配列として指定できる。テーマにはCSSファイルのパスだけでなく、npm形式のパッケージ指定と、パッケージ内から読み込むCSSを組み合わせたオブジェクトを使用できる。

将来npmへ公開したclonoを指定する場合、次の形になる。

```javascript
theme: [
  {
    specifier: "clono@<version>",
    import: "styles/clono.css",
  },
  "user-theme.css",
]
```

検証時点ではclonoをnpmへ公開していない。fixtureはリポジトリルートで`npm pack --ignore-scripts`を実行し、生成したtarballをVivliostyleのテーマ用作業領域へ導入したうえで、次の指定を使用する。

```javascript
theme: [
  {
    specifier: "clono",
    import: "styles/clono.css",
  },
  "user-theme.css",
]
```

tarballを介することで、リポジトリ内のディレクトリを直接指定する場合と異なり、`package.json`の`files`で定義した公開パッケージの収録範囲を検証できる。fixtureはtarballに`styles/clono.css`が含まれ、`docs/`が含まれないことも確認する。

### 生成済み原稿ツリーへコピーする

書籍プロジェクト変換が、実行中のclonoに同梱された`styles/clono.css`を生成済み原稿ツリーへコピーし、利用者テーマより前に指定する方式である。

fixtureでは予約領域の候補として、隠しディレクトリではない`_clono/`を使用した。

```text
generated/manuscripts/
├── _clono/
│   └── styles/
│       └── clono.css
└── chapter.md
```

Vivliostyle設定では、次の順序でCSSを指定する。

```javascript
theme: [
  "generated/manuscripts/_clono/styles/clono.css",
  "user-theme.css",
]
```

fixtureはコピー後のCSSがリポジトリルートの`styles/clono.css`とバイト単位で一致することを確認する。`_clono`という名称は検証用の候補であり、書籍プロジェクト仕様として確定したものではない。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/vivliostyle-clono-stylesheet/)をリポジトリ内へ保存している。このfixtureはclono本体から独立したnpmプロジェクトであり、`package.json`と`package-lock.json`によってVivliostyle CLI、VFMおよびMuPDFの依存関係を固定する。

原稿には、次の要素を配置した。

- 左端の位置を比較する通常段落
- clonoの`.clono-align-right`を持つ段落
- 利用者テーマ固有の疑似要素を持つ段落
- clonoの`.clono-page-break`を持つ空要素
- 改ページ要素の後に続く見出しと本文

clonoの基盤CSSは、文字揃えと強制改ページについて次の既定値を持つ。

```css
.clono-align-right {
  text-align: right;
}

.clono-page-break {
  break-before: page;
}
```

利用者テーマは、文字揃えだけを左揃えへ上書きし、適用を識別する疑似要素を追加した。強制改ページは上書きしない。

```css
.clono-align-right {
  text-align: left;
}

.user-theme-marker::before {
  content: "USER THEME APPLIED: ";
}
```

これにより、基盤CSSそのものが適用されることと、後から読み込む利用者テーマが特定の既定値を上書きできることを一つのPDFで区別して検証した。

### WebPubの検証

fixtureは、次の三構成でWebPubを毎回新しく生成し、変換後のHTMLとCSSを自動検証する。

| 構成 | 基盤CSSの配置 | 結果 |
| --- | --- | --- |
| npmパッケージ | Vivliostyleのテーマ用作業領域 | 成立 |
| 可視の予約領域 | `generated/manuscripts/_clono/styles/clono.css` | 成立 |
| 隠し予約領域 | `generated/manuscripts/.clono/styles/clono.css` | 不成立 |

npmパッケージ方式と可視の予約領域方式では、次の内容を確認した。

- HTMLが`.clono-align-right`と`.clono-page-break`を保持する
- clonoの基盤CSSと利用者テーマの両方がWebPubへ収録される
- clonoの基盤CSSが利用者テーマより前に参照される
- 収録されたCSSの内容から、それぞれのスタイルシートを識別できる

npmパッケージ方式では、生成したHTMLが次のように基盤CSSを参照した。

```html
<link rel="stylesheet" href="themes/node_modules/clono/styles/clono.css">
<link rel="stylesheet" href="user-theme.css">
```

可視の予約領域方式では、生成済み原稿ツリー内の基盤CSSがWebPubへコピーされ、利用者テーマより前に参照された。Vivliostyleのテーマ用作業領域にはclonoパッケージが導入されなかった。

一方、`.clono/`へ基盤CSSを置いた構成では、WebPub生成自体は成功し、HTMLにもCSSへの参照が残ったが、基盤CSSはWebPubへコピーされなかった。配布物内のリンク先が存在しないため、隠しディレクトリを予約領域として用いる方式は成立しない。

### PDFの検証

fixtureはnpmパッケージ方式と可視の予約領域方式について、古いPDFとVivliostyleの作業ディレクトリを削除してからPDFを生成し、次の内容をそれぞれ自動検証する。

- PDFが空でなく、正確に2ページある
- 通常段落と`.clono-align-right`を持つ段落の左端が一致する
- 利用者テーマが生成する疑似要素の文字列が1ページ目に存在する
- `.clono-page-break`より後の見出しが2ページ目に存在する

両方式とも、文字揃えが左揃えへ変化したことで、後から読み込んだ利用者テーマがclonoの既定値を上書きしたことを確認した。同時に、上書きしていない強制改ページが機能したことで、clonoの基盤CSSも適用されていることを確認した。

## 採用しない方式

### npmパッケージ内CSSの絶対パスを直接指定する

`import.meta.resolve("clono/styles/clono.css")`でnpmパッケージ内のCSSを絶対パスへ解決し、そのパスをVivliostyleのテーマとして直接指定する方式も試した。

Vivliostyle CLIはWebPubの生成を完了したが、基盤CSSをWebPub内へ収録せず、生成物の外側にある絶対パスを基準とした相対リンクをHTMLへ出力した。このWebPubを別の場所へ移動するとリンク先が存在しないため、配布可能な生成物として自立しない。

### 隠しディレクトリへ生成する

基盤CSSを`generated/manuscripts/.clono/styles/clono.css`へコピーする方式では、Vivliostyle CLIがWebPub内へCSSを収録しなかった。HTMLに残った参照先が存在しないWebPubになるため、初期仕様の候補にしない。

### リポジトリのディレクトリをローカルパッケージとして直接指定する

検証方法として`clono@file:../../../..`のようにリポジトリルートを直接指定すると、公開パッケージの`files`契約を再現せず、リポジトリ内の不要なファイルまでVivliostyleのテーマ用作業領域へコピーされた。

これはnpmパッケージ方式そのものの不成立を示す結果ではないが、公開パッケージを模擬する検証方法として不適切である。fixtureでは`npm pack --ignore-scripts`で作成したtarballを使用する。

## 検証結果

npmパッケージ方式と、可視の予約領域へ基盤CSSをコピーする方式は、どちらも技術的に成立する。

共通して、次の要件を満たした。

- Vivliostyle CLIへ複数のテーマを順序付きで渡せる
- clonoの基盤CSSを先、利用者テーマを後に指定できる
- 利用者テーマからclonoの既定値を上書きできる
- WebPubへ基盤CSSを収録し、生成物内のパスだけで参照できる
- 同じ設定からPDFにも基盤CSSと利用者テーマを適用できる

初期の書籍プロジェクト仕様では、`clono build`が実行中のclonoに同梱された基盤CSSを生成済み原稿ツリーの可視な予約領域へコピーする方式を採用候補とする。

この方式には次の利点がある。

- 変換済みMarkdownと、それを成立させる基盤CSSを同じ生成物として扱える
- 実行中のclonoと基盤CSSのバージョンが自然に一致する
- Vivliostyleによるclonoテーマパッケージの追加導入を必要としない
- テーマ導入に伴うネットワーク、キャッシュ、バージョン固定および依存関係の重複を避けられる

npmパッケージ方式は、生成済み原稿ツリーへCSSを含めたくない要件が生じた場合の代替候補として残す。ただし、初期仕様では実装と運用がより単純な生成方式を優先する。

clonoの基盤CSSは、利用者による上書きを妨げないよう、機能上必要な規則だけを低い詳細度で定義し、原則として`!important`を使用しない。フォント、色、枠線、余白などの紙面デザインは、引き続き利用者テーマの責務とする。

## 責務判断

### clonoが担う候補

- npmパッケージへ`styles/clono.css`を含める
- `clono build`では、実行中のclonoに同梱された基盤CSSを生成済み原稿ツリーの可視な予約領域へコピーする
- 予約領域のパスを定義し、入力原稿との衝突を検出する
- clonoの基盤CSSを利用者テーマより先に指定できる情報またはヘルパーを提供する
- 基盤CSSを機能上必要な既定値に限定し、利用者テーマから上書き可能に保つ
- 書籍プロジェクトの設定から、基盤CSSと利用者テーマをVivliostyle設定へ順序付きで渡せるようにする

### Vivliostyleへ委譲する責務

- 生成済み原稿ツリーの基盤CSSと、書籍プロジェクトの利用者テーマを読み込む
- 基盤CSSと利用者テーマを指定順に適用する
- WebPubへ必要なCSSファイルを収録する
- CSSのカスケードに従い、利用者テーマの上書きを反映する
- 基盤CSSと利用者テーマを使用してWebPubまたはPDFを生成する

### 利用者が担う責務

- 書籍固有のテーマCSSを用意する
- clonoの既定値を変更する場合、同じclassに対する後続のCSS規則を定義する
- フォント、色、余白、枠線などの紙面デザインを管理する

最終的な予約領域の名称、設定項目、Vivliostyle設定へ橋渡しするヘルパー、および利用者テーマを指定しない場合の扱いは、書籍プロジェクト仕様とADRで確定する。

## 成立条件と未確認事項

今回の生成方式は、clonoが所有する生成済み原稿ツリーへ同梱CSSをコピーでき、Vivliostyle CLIが可視ディレクトリにあるCSSをテーマとしてWebPubへ収録できることを前提とする。

次の事項は未確認または未決定である。

- 予約領域の最終的なディレクトリ名とCSSのパス
- 入力原稿が予約領域と同じパスを使用した場合の診断
- 基盤CSSをすべての書籍ビルドで出力するか、対象記法を使用した場合だけ出力するか
- 書籍プロジェクトの設定で利用者テーマを表現する形式
- clonoの基盤CSSを自動的に先頭へ加えるヘルパーのインターフェース
- 利用者テーマを指定しない場合のVivliostyle設定
- 一つの原稿だけ別のテーマを使用する場合の扱い
- WindowsとLinuxにおける生成済みCSSのコピー、WebPubおよびPDF生成
- npmパッケージ方式を再検討する場合の、公開済みclono、ネットワーク、キャッシュ、導入時間および依存関係の重複

これらは基盤CSSと利用者テーマを順序付きで統合できるという今回の結論を妨げない。初期仕様ではThunder Clawの書籍全体で一つの利用者テーマを使う構成を優先し、entryごとに異なるテーマを使う機能は具体的な需要が生じてから検討する。

## 依存関係の監査

2026年8月25日にfixtureで`npm ci`を実行した際、固定した開発依存関係に7件のmoderateと3件のhighが報告された。Vivliostyle CLI、VFMおよびMuPDFは検証専用の開発依存関係であり、clono本体のproduction依存関係として追加したものではない。

fixtureは外部から受け取った信頼できない入力の処理には使用しない。調査対象との対応を維持するため、監査結果だけを理由に固定バージョンを変更しない。

## 再現方法

[検証用fixtureのREADME](fixtures/vivliostyle-clono-stylesheet/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm run verify`でnpmパッケージ方式、可視の予約領域方式および隠し予約領域方式のWebPubを比較し、成立する二方式のPDFを再検証できる。

検証時には、公開パッケージの収録範囲を模擬するclonoのtarballと、二種類の生成済み原稿ツリーを毎回新しく作成する。古いWebPub、PDFおよびVivliostyleの作業ディレクトリは、検証結果として再利用しない。

Vivliostyle CLIはWebPubとPDFの生成時にローカルのHTTPサーバーを起動する。実行環境がローカルポートの待ち受けを禁止している場合は、その制限を解除した環境で検証する必要がある。

## 再調査する条件

- Vivliostyle CLIの`theme`、`specifier`、`import`またはWebPubへのファイル収録規則が変更された場合
- clonoのnpmパッケージ名、配布ファイルまたはCSSの配置を変更する場合
- 生成済み原稿ツリーの予約領域または出力方式を変更する場合
- 利用者テーマから基盤CSSの既定値を上書きできない事例が確認された場合
- WebPubへ生成済み原稿ツリーの基盤CSSが収録されない事例が確認された場合
- entryごとに異なる基盤CSSまたは利用者テーマが必要になった場合
- Thunder Clawの製品用テーマとの統合で、CSSの読み込み順または詳細度が問題になった場合
- 生成済み原稿ツリーへ基盤CSSを含めることが運用上の問題になる場合

## 参照資料

- [著者向け記法仕様](../specifications/authoring-syntax.md)
- [単一ファイル変換CLI仕様](../specifications/single-file-cli.md)
- [書籍プロジェクトの生成済み原稿ツリーに関する調査](book-project-output-tree.md)
- [検証用fixture](fixtures/vivliostyle-clono-stylesheet/)
- [Vivliostyle CLI 11.1.0 Config Reference](https://github.com/vivliostyle/vivliostyle-cli/blob/v11.1.0/docs/config.md)
