# 統合検証 {#chapter-integration}

基本脚注もほかの要素と共存する[^chapter-one-note]。

## 構成要素 {#section-components}

<figure class="numbered-figure" id="figure-architecture">
  <img src="./images/diagram.svg" alt="入力、変換、出力を並べた構成図">
  <figcaption id="figure-architecture-caption">全体構成</figcaption>
</figure>

![番号なし画像](./images/diagram.svg)

<figure class="numbered-figure" id="figure-workflow">
  <img src="./images/diagram.svg" alt="処理の流れを表した図">
  <figcaption id="figure-workflow-caption">処理フロー</figcaption>
</figure>

<figure class="numbered-table" id="table-environments">

| 環境 | 優先度 |
| --- | ---: |
| macOS | 1 |
| Linux | 2 |

<figcaption id="table-environments-caption">対応環境</figcaption>
</figure>

| 番号なし項目 | 状態 |
| --- | --- |
| Markdown | 有効 |

<figure class="numbered-table" id="table-features">

| 機能 | 状態 |
| --- | --- |
| **参照** | `verified` |

<figcaption id="table-features-caption">対応機能</figcaption>
</figure>

<figure class="numbered-listing" id="listing-greeting">
<figcaption id="listing-greeting-caption">挨拶を生成する関数</figcaption>

```kotlin
fun greet(name: String) = "Hello, $name!"
```
</figure>

```shell
npm test
```

<figure class="numbered-listing" id="listing-validation">
<figcaption id="listing-validation-caption">入力を検証する関数</figcaption>

```kotlin
fun requireName(name: String) = require(name.isNotBlank())
```
</figure>

見出しの番号参照は<a class="xref-section" href="#section-components"></a>を期待する。

図の番号参照は<a class="xref-figure" href="#figure-architecture"></a>を期待する。

表の番号参照は<a class="xref-table" href="#table-environments"></a>を期待する。

コードリストの番号参照は<a class="xref-listing" href="#listing-greeting"></a>を期待する。

見出しの番号とタイトル参照は<a class="xref-section xref-title" href="#section-components" data-title-href="#section-components"></a>を期待する。

図の番号とタイトル参照は<a class="xref-figure xref-title" href="#figure-workflow" data-title-href="#figure-workflow-caption"></a>を期待する。

表の番号とタイトル参照は<a class="xref-table xref-title" href="#table-features" data-title-href="#table-features-caption"></a>を期待する。

コードリストの番号とタイトル参照は<a class="xref-listing xref-title" href="#listing-validation" data-title-href="#listing-validation-caption"></a>を期待する。

別文書の見出し参照は<a class="xref-chapter xref-title" href="chapter-two.html#chapter-secondary" data-title-href="chapter-two.html#chapter-secondary"></a>を期待する。

別文書の図参照は<a class="xref-figure xref-title" href="chapter-two.html#figure-layout" data-title-href="chapter-two.html#figure-layout-caption"></a>を期待する。

別文書の表参照は<a class="xref-table xref-title" href="chapter-two.html#table-platforms" data-title-href="chapter-two.html#table-platforms-caption"></a>を期待する。

別文書のコードリスト参照は<a class="xref-listing xref-title" href="chapter-two.html#listing-status" data-title-href="chapter-two.html#listing-status-caption"></a>を期待する。

[^chapter-one-note]: 第1章の脚注本文。
