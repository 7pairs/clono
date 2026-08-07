# 複数の短い脚注

## 目的

同じページから短い脚注を複数参照した場合について、VFMの`dpub`および`gcpm`モードが生成するHTMLと、Vivliostyleによる脚注の番号、順序および紙面上の配置を比較する。

このケースでは三件の脚注を参照順と定義順を揃えて記述する。定義順が異なる場合、同じ脚注の複数参照、長い脚注および脚注内のMarkdownは扱わない。

## 入力とCSS

最初の段落から二件、次の段落から一件の脚注を参照する。三件の脚注定義は参照と同じ順序で原稿末尾に置く。

`footnotes.css`は共通CSSを読み込み、`footnote` classを持つ要素へ次の指定だけを加える。

```css
.footnote {
  float: footnote;
}
```

## 実行方法

`research/vivliostyle-capabilities/`で、HTMLとPDFを生成する。

```console
npm run build:footnotes:multiple-short
```

個別に生成する場合は、次のコマンドを使用する。

```console
npm run build:footnotes:multiple-short:html
npm run build:footnotes:multiple-short:pdf
```

生成物は`output/footnotes/multiple-short/`へ出力され、Git管理対象には含まれない。

HTMLは直接固定した`@vivliostyle/vfm` 2.7.2で生成する。
PDFはVivliostyle CLI 11.1.0を通じて生成するため、CLIが内蔵する`@vivliostyle/vfm` 2.7.0がMarkdownの変換に使用される。

## HTMLの結果

2026-08-08に標準条件で実行し、次の結果を確認した。

### `dpub`

- 本文へ番号1、2および3を含む三つの`a[role="doc-noteref"]`を参照順に出力した
- 原稿末尾へ三つの`aside.footnote[role="doc-footnote"]`を定義順に出力した
- 参照と脚注本体は、それぞれ一意なIDと`a[role="doc-backlink"]`で相互参照した
- 参照順と定義順を揃えた入力では、参照番号と脚注本体の順序が一致した

### `gcpm`

- 各参照位置へ三つの`span.footnote[role="doc-footnote"]`を出力した
- 各要素のIDには、Markdownの脚注識別子に対応する`fn-first`、`fn-second`および`fn-third`を使用した
- 参照番号、脚注番号および相互リンクはHTMLへ出力しなかった
- 脚注本体のHTML上の順序は参照順と一致した

Vivliostyle CLI内蔵VFM 2.7.0の中間HTMLについても、脚注に関係する構造はVFM 2.7.2の結果と一致した。

## PDFの結果

| 項目 | `dpub` | `gcpm` |
|---|---|---|
| ページ数 | 1 | 1 |
| 本文の参照番号 | HTMLへ出力されたリンク付きの1、2、3 | Vivliostyleが生成した1、2、3 |
| 脚注番号 | HTMLへ出力されたリンク付きの1、2、3 | Vivliostyleが生成した1.、2.、3. |
| 脚注の順序 | 参照順と一致 | 参照順と一致 |
| 脚注の配置 | 同じページの脚注領域 | 同じページの脚注領域 |
| 区切り線 | 脚注領域に一本 | 脚注領域に一本 |

両モードとも、三件の脚注が重なったり本文およびページ下端からはみ出したりせず、適切な間隔で縦に並んだ。日本語、本文および脚注に欠落、文字化け、重なりまたは切れはなかった。PDFから抽出したテキストでも、三件の脚注は1、2、3の順序を保った。

Popplerで`dpub`のPDFを検査した際、最小ケースと同じname tokenの長さに関する警告が出た。参照と脚注が三件に増えると警告も増えたため、リンクまたは名前付き遷移先に関係する可能性がある。`gcpm`では警告は出なかった。

## 暫定的な判断

一ページ内に複数の短いページ脚注を配置するために、clonoによる独自記法、番号付けまたは並べ替えは必要ない。VFMの`dpub`または`gcpm`モードとCSSの`float: footnote`で実現できる。このケースの分類は「clono不要」、確信度は高とする。

この結果からは`dpub`と`gcpm`のどちらかを採用方式として選ばない。次に、複数ページの本文で脚注が参照と同じページへ配置されるかを確認する。
