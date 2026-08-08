# 表内から参照する脚注

## 目的

GFMの表の本文セルから短い脚注を参照した場合について、VFMの`dpub`および`gcpm`モードが生成するHTMLと、Vivliostyleによる脚注の配置および表の組版を比較する。

## 入力とCSS

二列二行の表を通常本文の段落間へ置き、一行目の本文セルから一件の短い脚注を参照する。脚注定義は原稿末尾に置く。

`footnotes.css`は共通CSSを読み込み、ページ脚注に必要な指定と、表のセル境界を目視できる最小限の罫線および余白を加える。

```css
.footnote {
  float: footnote;
}
```

表や脚注を強制的に配置する指定は加えず、Vivliostyleの既定動作を確認する。

## 実行方法

`research/vivliostyle-capabilities/`で、HTMLとPDFを生成する。

```console
npm run build:footnotes:table
```

個別に生成する場合は、次のコマンドを使用する。

```console
npm run build:footnotes:table:html
npm run build:footnotes:table:pdf
```

生成物は`output/footnotes/table/`へ出力され、Git管理対象には含まれない。

HTMLは直接固定した`@vivliostyle/vfm` 2.7.2で生成する。
PDFはVivliostyle CLI 11.1.0を通じて生成するため、CLIが内蔵する`@vivliostyle/vfm` 2.7.0がMarkdownの変換に使用される。

## HTMLの結果

2026-08-08に標準条件で実行し、次の結果を確認した。

### `dpub`

- 脚注参照を表の本文セル内の`a[role="doc-noteref"]`として出力した
- 脚注本体を表と後続の通常本文より後の`aside.footnote[role="doc-footnote"]`として出力した
- 参照と脚注本体をIDと`a[role="doc-backlink"]`で相互参照し、番号1をHTMLへ出力した

### `gcpm`

- 脚注本体を表の本文セル内の`span.footnote[role="doc-footnote"]`として、参照位置へ出力した
- 参照番号、脚注番号および相互リンクはHTMLへ出力しなかった

Vivliostyle CLI内蔵VFM 2.7.0の中間HTMLについても、脚注に関係する構造はVFM 2.7.2の結果と一致した。

## PDFの結果

| 項目 | `dpub` | `gcpm` |
|---|---|---|
| ページ数 | 1 | 1 |
| 本文の参照番号 | 表の本文セル内に1を表示 | 表の本文セル内に1を表示 |
| 脚注 | 同じページの脚注領域 | 同じページの脚注領域 |
| 脚注番号 | 1 | 1. |
| 区切り線 | 一本 | 一本 |

両モードとも、表のセル内に参照番号を残し、対応する脚注を同じページの脚注領域へ配置した。表の内容はセル内で折り返され、表、通常本文および脚注に欠落、重複、文字化け、重なり、切れまたはページからのはみ出しはなかった。

Popplerで`dpub`のPDFを検査した際、これまでのケースと同じname tokenの長さに関する警告が出た。`gcpm`では警告は出なかった。

## 暫定的な判断

単純なGFMの表の本文セルから短いページ脚注を参照するために、clonoによる独自記法、脚注の移動または表に固有の変換は必要ない。VFMの`dpub`または`gcpm`モードとCSSの`float: footnote`で実現できる。このケースの分類は「clono不要」、確信度は高とする。

このケースでは、一ページへ収まる単純な表から一件の短い脚注を参照した。複数ページへまたがる表、複数の脚注および長い脚注との組み合わせは、実制作上の必要が生じた場合に追加検証する。
