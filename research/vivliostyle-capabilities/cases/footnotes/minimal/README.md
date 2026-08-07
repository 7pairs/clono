# 最小の脚注

## 目的

同一のGFM風脚注をVFMの各脚注モードでHTMLへ変換し、生成される構造とVivliostyleによる紙面上の配置を比較する。

このケースでは脚注を一件だけ使用し、脚注内の複数段落、Markdown、複数参照およびページをまたぐ脚注は扱わない。

## 入力とCSS

`manuscript.md`には、プレーンテキストだけを含む脚注を一件記述している。

```markdown
本文から脚注を参照します[^note]。

[^note]: これは脚注です。
```

`footnotes.css`は共通CSSを読み込み、ページ脚注として扱う要素に必要な指定だけを加える。

```css
.footnote {
  float: footnote;
}
```

この指定は、`footnote` classを出力する`dpub`および`gcpm`モードに作用する。

## 実行方法

`research/vivliostyle-capabilities/`で、HTMLとPDFを生成する。

```console
npm run build:footnotes:minimal
```

個別に生成する場合は、次のコマンドを使用する。

```console
npm run build:footnotes:minimal:html
npm run build:footnotes:minimal:pdf
```

生成物は`output/footnotes/minimal/`へ出力され、Git管理対象には含まれない。

HTMLは直接固定した`@vivliostyle/vfm` 2.7.2で生成する。
PDFはVivliostyle CLI 11.1.0を通じて生成するため、CLIが内蔵する`@vivliostyle/vfm` 2.7.0がMarkdownの変換に使用される。

## HTMLの結果

2026-08-07に標準条件で実行し、次の結果を確認した。

| モード | 生成される主な構造 | 参照関係と番号 |
|---|---|---|
| 未指定 | `section.footnotes[role="doc-endnotes"]`内の`li[role="doc-endnote"]` | 本文の`a[role="doc-noteref"]`と脚注の`a[role="doc-backlink"]`がIDで相互参照し、番号をHTMLへ出力する |
| `pandoc` | 未指定と同じ | 未指定と同じ |
| `dpub` | `aside.footnote[role="doc-footnote"]` | 本文の`a[role="doc-noteref"]`と脚注の`a[role="doc-backlink"]`がIDで相互参照し、番号をHTMLへ出力する |
| `gcpm` | 参照位置の`span.footnote[role="doc-footnote"]` | 参照リンクと番号をHTMLへ出力せず、CSSの`::footnote-call`および`::footnote-marker`に相当する処理をVivliostyleへ委ねる |

未指定と`pandoc`のHTMLはバイト単位で一致した。VFM 2.7.2では、脚注モードの未指定時に`pandoc`が使用される。

Vivliostyle CLI内蔵VFM 2.7.0の中間HTMLについても、脚注に関係する構造はVFM 2.7.2の結果と一致した。この最小ケースでは、両バージョン間に結果へ影響する差は確認できなかった。

## PDFの結果

| モード | 紙面上の配置 | 番号とリンク |
|---|---|---|
| `pandoc` | 二つ目の本文段落の直後に文末脚注として配置された | HTMLへ出力された番号と相互リンクが残った |
| `dpub` | ページ下部の脚注領域へ配置された | HTMLへ出力された参照番号と脚注からの戻りリンクが残った |
| `gcpm` | ページ下部の脚注領域へ配置された | Vivliostyleが参照番号と脚注番号を生成した。相互リンクはない |

三つのPDFはいずれもA5の一ページとして生成され、日本語、見出し、本文および脚注に欠落、文字化け、重なりまたは切れはなかった。

`pandoc`は文書末の脚注を生成するため、`.footnote { float: footnote; }`の対象にならない。`dpub`と`gcpm`はどちらも`footnote` classを持つ要素を生成するため、同じCSS指定でページ脚注になった。

Popplerで`pandoc`および`dpub`のPDFを検査した際、PDF内のname tokenが仕様上の長さを超えているという警告が出た。`gcpm`では出なかった。紙面とテキスト抽出への影響は確認できなかったが、リンクまたは名前付き遷移先との関係は未確認である。

## 暫定的な判断

単純なGFM風脚注一件をページ下部へ配置するために、clonoによる独自記法やHTML変換は必要ない。VFMの`dpub`または`gcpm`モードとCSSの`float: footnote`で実現できる。

ただし、次の検証が完了するまでページ脚注全体の方式は決定しない。

- 一ページに複数の脚注がある場合
- 長い脚注およびページをまたぐ脚注
- 同じ脚注を複数箇所から参照する場合
- 脚注内の複数段落およびMarkdown
- 表、リストおよび囲み枠内から参照する場合
- `dpub`と`gcpm`のリンク、アクセシビリティおよび番号制御の比較
- Popplerの警告の原因と実制作上の影響
