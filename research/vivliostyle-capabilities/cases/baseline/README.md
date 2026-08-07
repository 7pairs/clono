# ベースライン

## 目的

このケースは、固定したVFMとVivliostyle CLIを使用して、共通CSSを適用した日本語原稿からHTMLおよびPDFを生成できることを確認する。
特定のclono機能やVivliostyleの高度な機能は検証しない。後続のケースで問題が発生したときに、調査環境そのものの異常と機能固有の問題を切り分けるための基準とする。

## 実行方法

`research/vivliostyle-capabilities/`で依存関係をインストールした後、HTMLとPDFを生成する。

```console
npm run build:baseline
```

個別に生成する場合は、次のコマンドを使用する。

```console
npm run build:baseline:html
npm run build:baseline:pdf
```

生成物は次の場所に出力される。

```text
output/baseline/baseline.html
output/baseline/baseline.pdf
```

HTMLは直接固定した`@vivliostyle/vfm` 2.7.2で生成する。
PDFはVivliostyle CLI 11.1.0を通じて生成するため、CLIが内蔵する`@vivliostyle/vfm` 2.7.0がMarkdownの変換に使用される。

## 期待する結果

### HTML

- 文書の言語が日本語として出力される
- `styles/base.css`が参照される
- 見出しと段落が対応するHTML要素へ変換される
- 原稿の日本語が欠落または文字化けしない

### PDF

- エラーなくPDFを生成できる
- ページサイズがA5（148 mm × 210 mm）である
- 横書き、一段組みで出力される
- 原稿全体が一ページに収まる
- 日本語、見出しおよび段落が欠落、文字化け、重なりまたは切れなく描画される
- 上下左右に共通CSSで指定した18 mmの余白がある

コマンドが正常終了しただけでは成功とせず、生成したHTMLの構造とPDFの紙面を確認する。

## 初回検証結果

2026-08-07に標準条件で実行し、次の結果を確認した。

- HTMLに`lang="ja"`、共通CSSへの参照、`h1`、`h2`および`p`が出力された
- HTML内の日本語に欠落および文字化けはなかった
- PDFは一ページで、ページサイズは419.528 pt × 595.276 pt（A5相当）だった
- PDF生成にはVivliostyle Core 2.44.1およびChrome 150.0.7871.115が使用された
- PDFから原稿の日本語を抽出できた
- PDFを画像として描画し、横書き・一段組み、見出し、段落および余白に欠落、文字化け、重なりまたは切れがないことを目視確認した
