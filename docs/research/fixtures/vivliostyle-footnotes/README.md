# Vivliostyle脚注検証用fixture

このfixtureは、[Vivliostyleの脚注機能に関する調査](../../vivliostyle-footnotes.md)で確認した基本要件を再検証するための入力、設定、手順、期待結果を保存する。

このfixtureは、コラムに依存しない基本的な脚注を対象とする。コラム内脚注は、専用の[コラム検証用fixture](../vivliostyle-column/)で本文との連番、ページ下部への配置、章ごとの番号リセットを検証する。

## 検証対象

HTML変換は、次のVFMを個別に実行する。

- `@vivliostyle/vfm` 2.7.0: `@vivliostyle/cli` 11.1.0が依存するバージョン
- `@vivliostyle/vfm` 2.7.2: 初回調査時に直接インストールしたバージョン

PDF生成には`@vivliostyle/cli` 11.1.0を使用する。このCLIは`@vivliostyle/vfm` 2.7.0とVivliostyle.js 2.44.1を使用する。

このfixtureは独立したnpmプロジェクトである。検証用の依存関係は`package.json`と`package-lock.json`で固定し、`clono`本体の依存関係とは分離する。

## 準備

このディレクトリで次のコマンドを実行し、検証用の依存関係をインストールする。

```shell
npm ci
```

## HTMLの検証

このディレクトリで次のコマンドを実行する。

```shell
npm run verify:html
```

検証プログラムは既存のHTMLを削除してからVFM 2.7.0と2.7.2で`basic.md`を変換し、次を自動で確認する。

- 両方のHTMLが空ではない
- 両方のHTMLが一致する
- それぞれのHTMLが次の要素と内容を含む

  - 本文中の参照が`role="doc-noteref"`を持つ
  - 脚注本文が`role="doc-footnote"`を持つ
  - インラインコードが`<code>footnoteMode</code>`として残る
  - 脚注内のリンクが`https://docs.vivliostyle.org/ja/cookbook/footnotes/`を指す
  - 10行に分けて記述した脚注の内容が欠落しない

生成したHTMLは、必要に応じて`output/vfm-2.7.0.html`と`output/vfm-2.7.2.html`で確認できる。

## PDFの検証

このディレクトリで次のコマンドを実行する。

```shell
npm run build:pdf
```

検証プログラムは既存の`output/footnotes.pdf`を削除してからVivliostyle CLIを実行し、ビルドの終了コードが0であることと、生成されたPDFが空ではないことを自動で確認する。

生成された`output/footnotes.pdf`の全ページをPDFビューアーで開き、次を確認する。

- `basic.md`の脚注が本文と同じページの下部へ配置される
- インラインコードの表示が維持される
- 脚注内のリンクを開ける
- 10行に分けて記述した脚注の内容が欠落せず、一つのページに収まる
- `chapter-one.md`と`chapter-two.md`の最初の脚注番号が、どちらも1になる

生成した`.vivliostyle/`、`node_modules/`および`output/`以下のファイルはコミットしない。
