# 表の検証

<figure class="numbered-table" id="table-runtime">

| 項目 | 説明 | 値 |
| :--- | :---: | ---: |
| 言語 | **実装言語** | `ClojureScript` |
| 組版 | [Vivliostyle](https://vivliostyle.org/) | 11 |

<figcaption id="table-runtime-caption">実行環境</figcaption>
</figure>

同一文書内の番号は<a class="xref-table" href="#table-runtime"></a>を期待する。

番号なしの比較表を次に示す。

| 候補 | 採否 |
| --- | --- |
| Markdown | 採用 |
| HTML | 保留 |

<figure class="numbered-table" id="table-features">

| 機能 | 状態 |
| --- | --- |
| **強調** | 対応 |
| `code` | 対応 |

<figcaption id="table-features-caption">対応機能</figcaption>
</figure>

番号なしの表を挟んだ後の番号とタイトルは<a class="xref-table xref-title" href="#table-features" data-caption-href="#table-features-caption"></a>を期待する。

別文書の番号とタイトルは<a class="xref-table xref-title" href="chapter-two.html#table-platforms" data-caption-href="chapter-two.html#table-platforms-caption"></a>を期待する。
