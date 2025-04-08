# clono

`clono`は、CSS組版システムの[Vivliostyle](https://vivliostyle.org/ja/)で利用されている[Vivliostyle Flavored Markdown](https://vivliostyle.github.io/vfm/#/vfm)（以下VFMと略します）に機能を追加するためのツールです。見出しの参照など、本来のVFMには存在しない機能をMarkdown内に記述できるようになります。

## 導入

以下の手順で`clono`を導入してください。

### インストール

[リリースページ](https://github.com/7pairs/clono/releases)からダウンロードした`clono.js`を任意のディレクトリに配置してください。

### 依存ライブラリの導入

`clono`は以下のnodeライブラリに依存しています。npmなどを利用してインストールしてください。

- fix-esm
- github-slugger
- mdast-util-directive
- mdast-util-from-markdown
- mdast-util-gfm-footnote
- mdast-util-to-markdown
- micromark-extension-directive
- micromark-extension-gfm-footnote

### 環境設定

`clono.js`を配置したディレクトリに、以下の要領で`config.edn`という名前のファイルを作成してください。

```clojure
{:catalog "<YOUR CATALOG FILE PATH>"
 :manuscripts "<YOUR MANUSCRIPTS DIRECTORY PATH>"
 :output "<YOUR OUTPUT DIRECTORY PATH>"}
```

| キー           | 値                                             |
| -------------- | ---------------------------------------------- |
| `:catalog`     | カタログファイル（後述）のパス                 |
| `:manuscripts` | 変換対象のMarkdownを配置したディレクトリのパス |
| `:output`      | 変換後のMarkdownを出力するディレクトリのパス   |

`:output`は、`vivliostyle.config.js`の`entry`で参照するMarkdownファイルを生成する場所として指定してください。

## 変換処理

### カタログファイル

`config.edn`のキー`:catalog`で指定したパスに、以下の要領でカタログファイルを作成してください。

```clojure
{:forewords ["foreword.md"]
 :chapters ["chapter01.md"
            "chapter02.md"
            "chapter03.md"]
:appendices ["appendix01.md"]
:afterwords ["afterword.md"]}
```

| キー          | 値                                                                 |
| ------------- | ------------------------------------------------------------------ |
| `:forewords`  | 「はじめに」などの原稿のファイル名。章番号は振られない。           |
| `:chapters`   | 本文の原稿のファイル名。「第1章」「第2章」の形で章番号が振られる。 |
| `:appendices` | 付録の原稿のファイル名。「付録A」「付録B」の形で章番号が振られる。 |
| `:afterwords` | 「おわりに」などの原稿のファイル名。章番号は振られない。           |

カタログファイルに列挙したファイルは、`config.edn`のキー`:manuscripts`で指定したディレクトリに格納してください。

### 実行

`node <INSTALL PATH>/clono.js`を実行してください。`config.edn`のキー`:output`で指定したディレクトリに変換後のMarkdownが出力されます。あわせて、目次ファイル`toc.md`と索引ファイル`index.md`も作成されますので、必要に応じて`vivliostyle.config.js`の`entry`に追加してください。

### スタイルシート

`vivliostyle.config.js`の`theme`に、`clono.js`に同梱されている`clono.css`を追加してください。`clono.css`には必要な機能を実現するための最低限の記述のみが含まれますので（逆にいうと、もともとのスタイルをできるだけ壊さないようにしています）、あなたの本のデザインに馴染ませるための記述は別途追加してください。

## 独自記法

VFMに機能を追加するため、`clono`では以下の独自記法を採用しています。

### 見出し

通常のVFMと同様、`#`のあとに見出しの文字列を記述してください。`#`の数を増やすと見出しのレベルが下がります。

```markdown
# レベル1の見出し
## レベル2の見出し
### レベル3の見出し
#### レベル4の見出し
##### レベル5の見出し
###### レベル6の見出し
```

後述の見出し参照を利用する場合、見出しIDを`:label{#見出しID}`で指定することができます。

```markdown
## レベル2の見出し:label{#heading-id}
```

なお、レベル1の見出しについては、ファイル名の拡張子を除いた部分が自動的に章IDとして付与され、`label`指定は無視されます（ファイル名が`manuscript.md`であれば、章IDは`manuscript`）。

> [!TIP]
> 上記の例では見出し文字列の後ろに`label`を記述しましたが、`#`の後ろであればどこに置いても機能します。
> 
> ```markdown
> ## :label{#heading-id}レベル2の見出し
> ## レベル2の:label{#heading-id}見出し
> ```
> 
> とはいえ、わざわざ見出し文字列を分断するメリットはないはずなので、基本的には**末尾か先頭に記述することをおすすめ**します。

>[!CAUTION]
> VFMにはブロック要素のidやclassを指定する機能がありますが、`clono`を利用する際にはこの機能が正しく動かないケースがあります。特に、見出し周りでは確実に動作しないことを確認しています。いずれは利用できるようにしたいと考えていますが、現時点では制限事項とさせてください。
> 
> ```markdown
> <!-- このような記述はできない -->
> # 見出し{#id .class}
> ```

### 見出し参照

章IDや見出しIDを章番号、節番号……に変換する`refHeading`と`refHeadingName`が提供されています。

| 要素名           | 展開時の挙動                                                             |
| ---------------- | ------------------------------------------------------------------------ |
| `refHeading`     | 「第1章」「1.2」のように番号のみが展開される。                           |
| `refHeadingName` | 「第1章 最初の章」「1.2 概要」のように見出し文字列も合わせて展開される。 |

章を参照する場合は`:refHeading{#章ID}`のように記述してください。レベル2以下の見出しを参照する場合、同一の章の見出しを参照する場合は`:refHeading{#見出しID}`、別の章にある見出しを参照する場合は`:refHeading{#章ID|見出しID}`と記述してください。`refHeadingName`の場合も同様です。

### 画像

`:figure[キャプション]{src=ファイルパス その他の属性}`で画像を埋め込むことができます。「その他の属性」は[VFMの画像記法](https://vivliostyle.github.io/vfm/#/vfm#with-caption-and-single-line)で`{}`内に書かれる属性（`width`など）を想定しています。

```markdown
:figure[サンプル画像]{src=../images/sample.png width=350}
```

後述の画像参照を利用する場合、ファイル名の拡張子を除いた部分（上記の例では`sample`）が画像IDとなります。

### 画像参照

画像IDを画像番号（図1.2など）に変換する機能です。

同一の章の画像を参照する場合は`:refFigure{#画像ID}`、別の章にある画像を参照する場合は`:refFigure{#章ID|画像ID}`と指定してください。

### コード

VFMと同様にバックスラッシュ3つで前後を囲ってください。最初のバックスラッシュの後ろには`言語 キャプション`を指定し、キャプション部分には見出しと同様に`:label{#コードID}`と記述してコードIDを付与することもできます。

<pre>
```kotlin サンプルコード:label{#hello}
fun hello() {
    return "Hello, World!"
}
```
</pre>

> [!TIP]
> 構文解析の都合上、言語指定を省略することはできません。シンタックスハイライトが不要な場合は、プレーンテキスト（`text`、`plaintext`）などを指定してください。
> 
> ```text シンタックスハイライト不要:label{#plain}
> これはハイライトされないコードです。
> ```

### コード参照

コードIDをコード番号（リスト1.2など）に変換する機能です。

同一の章のコードを参照する場合は`:refCode{#コードID}`、別の章にあるコードを参照する場合は`:refCode{#章ID|コードID}`と指定してください。

### 表

表そのものはVFMやHTMLで記述し、その前後を`:::table[キャプション]{#表ID}`と`:::`で囲んでください。

```markdown
:::table[和暦の開始年]{#wareki}

| 和暦 | 西暦 |
| ---- | ---- |
| 昭和 | 1926 |
| 平成 | 1989 |
| 令和 | 2019 |

:::
```

### 表参照

表IDを表番号（表1.2など）に変換する機能です。

同一の章の表を参照する場合は`:refTable{#表ID}`、別の章にある表を参照する場合は`:refTable{#章ID|表ID}`と指定してください。

### 脚注

VFMの後注（章の末尾に説明文が出力される）の機能を脚注（各ページの下部に説明文が出力される）の機能として再定義しました。

```markdown
これは本文です[^1]。これはもうひとつの本文です[^fn]。

[^1]: これは脚注です。
[^fn]: 脚注のIDは数字でなくても構いません。
```

>[!CAUTION]
> 逆に後注の機能は使えなくなります。将来的にはON/OFFができるようにしたいと考えていますが、現時点では制限事項とさせてください。

### 索引

`:index[単語]{ruby=読み}`で索引項目を作成します。「読み」は日本語の場合ひらがなで、英数字の場合は大文字で記述してください。

```markdown
:index[clono]{ruby=CLONO}は:index[原稿]{ruby=げんこう}内のVFM記法を拡張します。
```

### コラム

コラムの前後を`:::column[キャプション]`と`:::`で囲んでください。

```markdown
:::column[コラムのタイトル]

コラムの内容です。

:::
```

## 謝辞

はじめに、Vivliostyleの開発関係者のみなさまに感謝いたします。[公式サイト](https://vivliostyle.org/ja/)のタイトルにあるような「楽しくCSS組版！」が実現できているのは、みなさまの尽力のおかげです。ありがとうございます。

また、`clono`の開発にあたっては、以下の書籍や同人誌を大いに参考にさせていただきました。ありがとうございました。

- リブロワークス（著），Vivliostyle（監修）（2023）『[Web技術で「本」が作れるCSS組版Vivliostyle入門](https://www.c-r.com/book/detail/1493)』シーアンドアール研究所
- 古賀広隆（著）（2024）『 [VivlioStyleとRehype/RemarkではじめるCSS組版による同人誌制作](https://techbookfest.org/product/irrGnuyPPYz2g1jHizV6cZ?productVariantID=gGZyLqhPz1LhFsPC0RemrM)』げぐはつ書房

`clono`というツール名は、開発言語であるClojureScriptに由来していますが、VTuberの[千羽黒乃さん](https://www.youtube.com/@senba_crow)に（勝手に）あやかったものでもあります。千羽師匠の今後のますますのご活躍を祈念しておりますのじゃ。

## ライセンス

`clono`は[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)のもとで公開します。
