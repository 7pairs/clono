# Vivliostyle脚注検証用fixture

このfixtureは、[Vivliostyleの脚注機能に関する調査](../../vivliostyle-footnotes.md)で確認した基本要件を再検証するための入力、設定、手順、期待結果を保存する。

コラム内の脚注は、コラムの構文と出力HTMLが未決定のため、このfixtureには含めない。コラムとの結合テストを設計できる段階で追加する。

## 検証対象

HTML変換は、次のVFMを個別に実行する。

- `@vivliostyle/vfm` 2.7.0: `@vivliostyle/cli` 11.1.0が依存するバージョン
- `@vivliostyle/vfm` 2.7.2: 初回調査時に直接インストールしたバージョン

PDF生成には`@vivliostyle/cli` 11.1.0を使用する。このCLIは`@vivliostyle/vfm` 2.7.0とVivliostyle.js 2.44.1を使用する。

## HTMLの検証

このディレクトリで次のコマンドを実行する。

```shell
set -eu

mkdir -p output
npx --yes --package=@vivliostyle/vfm@2.7.0 vfm --partial --footnote dpub basic.md > output/vfm-2.7.0.html
test -s output/vfm-2.7.0.html

npx --yes --package=@vivliostyle/vfm@2.7.2 vfm --partial --footnote dpub basic.md > output/vfm-2.7.2.html
test -s output/vfm-2.7.2.html

diff -u output/vfm-2.7.0.html output/vfm-2.7.2.html
```

`diff`が差分を出力せず、終了コード0を返すことを確認する。それぞれのHTMLについて、少なくとも次の要素と内容を確認する。

- 本文中の参照が`role="doc-noteref"`を持つ
- 脚注本文が`aside[role="doc-footnote"]`として出力される
- インラインコードが`<code>footnoteMode</code>`として残る
- 脚注内のリンクが`https://docs.vivliostyle.org/ja/cookbook/footnotes/`を指す
- 10行に分けて記述した脚注の内容が欠落しない

## PDFの検証

このディレクトリで次のコマンドを実行する。

```shell
set -eu

mkdir -p output
rm -f output/footnotes.pdf
npx --yes --package=@vivliostyle/cli@11.1.0 vivliostyle build --config vivliostyle.config.mjs --output output/footnotes.pdf
test -s output/footnotes.pdf
```

生成された`output/footnotes.pdf`の全ページをPDFビューアーで開き、次を確認する。

- `basic.md`の脚注が本文と同じページの下部へ配置される
- インラインコードの表示が維持される
- 脚注内のリンクを開ける
- 10行に分けて記述した脚注の内容が欠落せず、一つのページに収まる
- `chapter-one.md`と`chapter-two.md`の最初の脚注番号が、どちらも1になる

生成した`.vivliostyle/`および`output/`以下のファイルと、npxが取得した依存関係はコミットしない。
