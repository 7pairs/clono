# コードリストの検証

<figure class="numbered-listing" id="listing-greeting">
<figcaption id="listing-greeting-caption">挨拶を表示する関数</figcaption>

```kotlin
fun greet(name: String): String {
    return "Hello, $name!"
}
```
</figure>

同一文書内の番号は<a class="xref-listing" href="#listing-greeting"></a>を期待する。

番号なしのシェルコマンドを次に示す。

```shell
npm test
```

<figure class="numbered-listing" id="listing-validation">
<figcaption id="listing-validation-caption">入力を検証する処理</figcaption>

```kotlin
fun requireName(name: String) {
    require(name.isNotBlank())
}
```
</figure>

番号なしのコードを挟んだ後の番号とタイトルは<a class="xref-listing xref-title" href="#listing-validation" data-caption-href="#listing-validation-caption"></a>を期待する。

別文書の番号とタイトルは<a class="xref-listing xref-title" href="chapter-two.html#listing-device-properties" data-caption-href="chapter-two.html#listing-device-properties-caption"></a>を期待する。
