# Vivliostyle基本表現機能検証用fixture

## 目的

空行、強制改行、強制改ページ、段落の右寄せ、定義リストについて、VFMとVivliostyleへ委譲できる範囲と、clonoが変換後に出力すべき構造を検証する。

このfixtureは著者向けの独自記法を確定するものではない。標準Markdown記法と、clonoの変換後を模したHTML構造を入力する。

## 検証対象

- 標準Markdownの強制改行が`br`へ変換される
- 空行を表す空の要素をCSSで一行相当の空白として表示できる
- 複数の段落を一つのコンテナへ入れ、まとめて右寄せできる
- 用語一つと説明一つを持つ定義リストを表示できる
- 定義の説明内でインラインコード、強調、リンクを使用できる
- 目に見える区切りを表示せず、後続の見出しまたは通常段落を次ページから開始できる

連続する空行、ページ境界にある空行、複数の用語または説明、複数段落を含む定義は対象に含めない。

## 入力と出力の境界

強制改行には標準Markdownのバックスラッシュによる記法を使用する。clonoによる変換は想定しない。

空行、右寄せ、定義リスト、強制改ページには、clonoの変換後を模した次のclassを使用する。

- `blank-line`: CSSで一行相当の空白を確保する空の要素
- `text-align-right`: 内部の複数段落を右寄せするコンテナ
- `definition-list`: `dl`、`dt`、`dd`からなる定義リスト
- `page-break`: 後続のブロックを次ページへ送る空の要素

空の要素には`aria-hidden="true"`を付ける。見た目はテーマCSSへ委譲し、インラインstyleは使用しない。

## 依存関係

- Node.js 22.13.0以降の22系、または24系
- `@vivliostyle/cli` 11.1.0
- `@vivliostyle/vfm` 2.7.0
- `mupdf` 1.28.0

依存関係はこのディレクトリの`package-lock.json`に固定する。

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

生成物は`output/`に出力され、Gitの管理対象には含めない。

## 自動検証

`scripts/verify-webpub.mjs`は、次を確認する。

- 強制改行が`br`へ変換される
- 空行、右寄せ、定義リスト、強制改ページのID、class、要素構造が保持される
- 右寄せコンテナ内の二つの段落が保持される
- 定義内のインラインコード、強調、リンクがHTMLへ変換される

`scripts/verify-pdf.mjs`は、次を確認する。

- 二つの強制改ページによってPDFが3ページになる
- 強制改行の前後が同じ段落内の別の行として表示される
- 空行を挟んだ段落間の垂直方向の空きが、通常の段落間より大きい
- 日付と名前の右端が揃い、左寄せの基準より右側へ配置される
- 定義の説明が用語より字下げされる
- 改ページ前の段落が1ページ目、改ページ後の見出しが2ページ目、別の改ページ後の通常段落が3ページ目に表示される

## 目視確認

`output/basic-presentation.pdf`を開き、次を確認する。

- 強制改行、空行、右寄せ、定義リストが意図した見た目である
- 改ページ用の線や記号が表示されない
- 改ページ後の見出しと通常段落が、それぞれ2ページ目と3ページ目の先頭付近にある

## 未検証・未決定

- 著者向け記法
- 連続する空行とページ境界にある空行
- 右寄せブロック内の複雑なブロック要素
- 複数の用語、説明、段落または入れ子を持つ定義リスト
- 製品用テーマでの余白、改ページ、文字揃え

## 参照資料

- [強制改行（オプション） | Vivliostyle Flavored Markdown 2.7.0](https://github.com/vivliostyle/vfm/blob/v2.7.0/docs/ja/vfm.md#強制改行オプション-hard-new-line-optional)
- [そのままのHTML | Vivliostyle Flavored Markdown 2.7.0](https://github.com/vivliostyle/vfm/blob/v2.7.0/docs/ja/vfm.md#そのままのhtml-raw-html)
- [Supported CSS Features | Vivliostyle.js 2.44.1](https://github.com/vivliostyle/vivliostyle.js/blob/v2.44.1/docs/ja/supported-css-features.md)
