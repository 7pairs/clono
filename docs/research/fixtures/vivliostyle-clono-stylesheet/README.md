# clono基盤CSSとユーザーテーマの統合検証fixture

## 目的

npmパッケージからclonoの基盤CSSを読み込む方式と、生成済み原稿ツリーへ基盤CSSを配置する方式を比較し、利用者のテーマCSSを後から適用して既定スタイルを上書きできるか検証する。

調査の背景、方式の比較、責務候補および未決定事項は、[Vivliostyleにおけるclono基盤CSSと利用者テーマの統合に関する調査](../../vivliostyle-clono-stylesheet.md)を参照する。

このfixtureは、書籍プロジェクト用CLI、基盤CSSの予約パスおよび設定ヘルパーの最終的なインターフェースを決定するものではない。

## 構成

### npmパッケージ方式

`scripts/prepare-package-theme.mjs`は、リポジトリルートで`npm pack --ignore-scripts`を実行し、`files`で制限された公開パッケージ相当のtarballを作成する。このtarballをVivliostyleのテーマ用作業ディレクトリへ導入し、`vivliostyle.config.mjs`から次の順序でテーマを指定する。

1. `clono/styles/clono.css`
2. `user-theme.css`

リポジトリのディレクトリをローカルパッケージとして直接指定すると、公開パッケージの`files`契約を再現せず、検証用fixtureや生成物を含むリポジトリ全体がWebPubへコピーされた。このため、比較には実際の配布物と同じ範囲を持つtarballを使用する。

### 生成先CSS方式

`scripts/prepare-generated-tree.mjs`は、原稿を生成済み原稿ツリーへコピーし、リポジトリの`styles/clono.css`と同じ内容を次の候補パスへ配置する。

```text
generated/manuscripts/_clono/styles/clono.css
```

`vivliostyle.generated-theme.config.mjs`は、この基盤CSSと`user-theme.css`を順に指定する。この方式では、clonoをVivliostyleのテーマ用作業ディレクトリへ導入しない。

予約領域を隠しディレクトリにした場合も比較するため、`.clono/styles/clono.css`を指定する`vivliostyle.hidden-generated-theme.config.mjs`を用意する。この構成はWebPub生成自体には成功するが、Vivliostyleが隠しディレクトリのCSSをWebPubへコピーしないことを負のケースとして検証する。

clonoの基盤CSSは`.clono-page-break`による改ページを提供する。ユーザーテーマは`.clono-align-right`を左揃えへ変更し、独自の疑似要素を追加する。

## 固定した依存関係

- `@vivliostyle/cli` 11.1.0
- `@vivliostyle/vfm` 2.7.0
- `mupdf` 1.28.0

`package-lock.json`で、これらの推移的依存関係も固定する。検証対象のclonoはリポジトリの作業ツリーからtarballを作成するため、このfixtureの`package-lock.json`には含まれない。

## 実行方法

```shell
set -eu
npm ci
npm run verify
```

個別に確認する場合は、次のコマンドを使用する。

```shell
set -eu
npm run verify:webpub
npm run verify:pdf
```

WebPubとPDFは`output/`へ生成するが、調査資材には含めない。

## 自動検証

`npm run verify:webpub`は、次を検証する。

- npmパッケージ方式と`_clono`予約領域方式のHTMLがclonoのクラスを保持する
- 両方式のWebPubへclonoの基盤CSSとユーザーテーマの両方が含まれる
- 両方式でユーザーテーマがclonoの基盤CSSより後に読み込まれる
- npmパッケージ方式だけが、clonoをVivliostyleのテーマ用作業ディレクトリへ導入する
- 生成先CSSがリポジトリの`styles/clono.css`と一致する
- `.clono`隠し予約領域へのリンクはHTMLへ残るが、基盤CSSはWebPubへコピーされない

`npm run verify:pdf`は、次を検証する。

- npmパッケージ方式と`_clono`予約領域方式の両方で、基盤CSSによる強制改ページが適用される
- 両方式で、ユーザーテーマによって右揃えの既定値を左揃えへ上書きできる
- 両方式で、ユーザーテーマ固有の疑似要素が出力される

## 検証結果

2026-08-25にmacOSとNode.js 24.19.0で`npm ci`と`npm run verify`を実行し、WebPubとPDFの自動検証がすべて成功した。

- npmパッケージ方式と`_clono`予約領域方式の両方で、WebPubへ基盤CSSを収録できた
- 両方式でclonoの基盤CSSを先、ユーザーテーマを後に指定し、利用者が既定の文字揃えを上書きできた
- 両方式で、ユーザーテーマが上書きしない強制改ページはclonoの基盤CSSどおりに動作した
- `_clono`予約領域方式は、clonoをテーマパッケージとして再導入せずにWebPubとPDFへ基盤CSSを適用できた
- `.clono`隠し予約領域はWebPubへコピーされないため、生成先CSSの配置先として使用できない

`import.meta.resolve('clono/styles/clono.css')`で取得した絶対パスをCSSファイルとして直接指定する方法も試したが、WebPubへ基盤CSSが収録されず、出力外のファイルを指すリンクになった。このため、配布可能なWebPubも考慮する場合は、npmパッケージ方式または可視の生成先予約領域を使用する必要がある。

この検証では未公開のclonoをtarballからテーマ用作業ディレクトリへ導入した。npm公開後のパッケージ名やバージョン指定、書籍プロジェクトからVivliostyle設定へテーマ一覧を渡すヘルパー、および複数OSでの動作は検証していない。tarballを導入する際の推移的依存関係はfixtureの`package-lock.json`で固定されないため、検証時のclonoが持つ依存関係にも影響される。
