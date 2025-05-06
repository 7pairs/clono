# clono

**clono**は、CSS組版システムの**[Vivliostyle](https://vivliostyle.org/ja/)**で利用されている[Vivliostyle Flavored Markdown](https://vivliostyle.github.io/vfm/#/vfm)（以下VFMと略します）に機能を追加するツールです。見出しの参照など、もともとのVFMには存在しない機能をMarkdown内で実現できるようになります。

## 導入

以下の手順でclonoを導入してください。

### インストール

[リリースページ](https://github.com/7pairs/clono/releases)からダウンロードした`clono.js`を任意のディレクトリに配置してください。

### 依存ライブラリの導入

clonoは以下のnodeライブラリに依存しています。npmなどを利用してインストールしてください。

- [fix-esm](https://www.npmjs.com/package/fix-esm)
- [github-slugger](https://www.npmjs.com/package/github-slugger)
- [mdast-util-directive](https://www.npmjs.com/package/mdast-util-directive)
- [mdast-util-from-markdown](https://www.npmjs.com/package/mdast-util-from-markdown)
- [mdast-util-gfm-footnote](https://www.npmjs.com/package/mdast-util-gfm-footnote)
- [mdast-util-to-markdown](https://www.npmjs.com/package/mdast-util-to-markdown)
- [micromark-extension-directive](https://www.npmjs.com/package/micromark-extension-directive)
- [micromark-extension-gfm-footnote](https://www.npmjs.com/package/micromark-extension-gfm-footnote)

### 環境設定

`clono.js`を配置したディレクトリに、以下の要領で`config.edn`という名前のファイルを作成してください。

```clojure
{:catalog "<YOUR CATALOG FILE PATH>"
 :input "<YOUR INPUT DIRECTORY PATH>"
 :output "<YOUR OUTPUT DIRECTORY PATH>"}
```

| キー       | 値                                             |
| ---------- | ---------------------------------------------- |
| `:catalog` | カタログファイル（後述）のパス                 |
| `:input`   | 変換対象のMarkdownを配置するディレクトリのパス |
| `:output`  | 変換後のMarkdownを出力するディレクトリのパス   |

`vivliostyle.config.js`の`entry`で参照するVFMファイルのディレクトリを`:output`に指定すれば、clonoとVivliostyleをシームレスに接続することができます。

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
| `:forewords`  | 「はじめに」などの原稿ファイル名。章番号は振られない。             |
| `:chapters`   | 本文の原稿ファイル名。「第1章」「第2章」の形式で章番号が振られる。 |
| `:appendices` | 付録の原稿ファイル名。「付録A」「付録B」の形式で章番号が振られる。 |
| `:afterwords` | 「おわりに」などの原稿ファイル名。章番号は振られない。             |

カタログファイルに列挙したファイルは、`config.edn`のキー`:input`で指定したディレクトリに格納してください。

### 実行

コマンドラインで`node <INSTALLED PATH>/clono.js`を実行してください。`config.edn`のキー`:output`で指定したディレクトリに変換後のMarkdownが出力されます。あわせて、目次ファイル`toc.md`と索引ファイル`index.md`も作成されますので、必要に応じて`vivliostyle.config.js`の`entry`に追加してください。

## スタイルシート

`vivliostyle.config.js`の`theme`に、`clono.js`に同梱されている`clono.css`を追加してください。`clono.css`には機能を実現するための最低限の記述のみが含まれていますので（逆にいうと、もともとのスタイルをできるだけ壊さないようにしています）、あなたの本のデザインに馴染ませるための記述は別途追加してください。

### CSSの詳細

`clono.css`は主に以下の機能を提供しています。

1. **自動番号付け**: 章・節・図・表・リストなどに自動的に番号を振る機能
2. **参照リンク**: `refHeading`や`refFigure`などで参照先の番号を表示する機能
3. **脚注スタイル**: 脚注のレイアウトと表示形式
4. **目次と索引のレイアウト**: 目次と索引のドットリーダーなどの装飾

### 多言語対応

`clono.css`はCSSカスタムプロパティ（変数）を使用して、章や図表などのラベルをカスタマイズできます。以下の変数を上書きすることで、英語表記などへの変更が可能です。

```css
/* 日本語（デフォルト） */
:root {
    --label-chapter: "第";
    --label-chapter-suffix: "章";
    --label-appendix: "付録";
    --label-code: "リスト";
    --label-figure: "図";
    --label-table: "表";
}

/* 英語にする場合の例 */
:root {
    --label-chapter: "Chapter ";
    --label-chapter-suffix: "";
    --label-appendix: "Appendix ";
    --label-code: "Listing ";
    --label-figure: "Figure ";
    --label-table: "Table ";
}
```

### スタイルのカスタマイズ

clonoが生成するHTML要素には専用のクラス名が付与されているので、これらを利用して独自のスタイルを追加できます。

- `.cln-chapter`, `.cln-appendix` - 章と付録のコンテナ
- `.cln-code` - コードブロックのコンテナ
- `.cln-table` - 表のコンテナ
- `.cln-ref-heading`, `.cln-ref-heading-name` - 見出し参照
- `.cln-ref-code`, `.cln-ref-figure`, `.cln-ref-table` - コード・図・表参照
- `.cln-footnote` - 脚注
- `#cln-toc` - 目次
- `.cln-index` - 索引

例えば、以下のようなCSSを追加することで、章の見出しをカスタマイズできます。

```css
div.cln-chapter h1 {
    font-family: "游明朝", YuMincho, serif;
    font-size: 24pt;
    color: #336699;
    margin-top: 3em;
}
```

## プロジェクト構造

典型的なプロジェクト構造は以下のような形になります。

```
your-book/
├── catalog.edn            # カタログファイル
├── config.edn             # 設定ファイル
├── clono.js               # メインスクリプト
├── clono.css              # スタイルシート
├── dist/                  # 出力ディレクトリ
├── plugins/               # プラグインディレクトリ
│     └── code.js         # codeノード用プラグイン
├── src/                   # 入力ディレクトリ
│     ├── chapter01.md
│     ├── chapter02.md
├── images/                # 画像ディレクトリ
│     └── sample.png
└── vivliostyle.config.js  # Vivliostyleの設定
```

## 独自記法

VFMに機能を追加するため、clonoでは以下の独自記法を採用しています。

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

後述の見出し参照の機能を利用する場合、見出しIDを`:label{#見出しID}`で指定することができます。

```markdown
## レベル2の見出し:label{#heading-id}
```

なお、レベル1の見出しについては、ファイル名の拡張子を除いた部分が自動的に章IDとして付与され、`label`指定は無視されます（ファイル名が`manuscript.md`であれば、章IDは`manuscript`になります）。

> [!TIP]
> 上記の例では見出し文字列の後ろに`label`を記述しましたが、実際には`#`の後ろであればどこに置いても機能します。
> 
> ```markdown
> ## :label{#heading-id}レベル2の見出し
> 
> ## レベル2の:label{#heading-id}見出し
> ```
> 
> とはいえ、わざわざ見出し文字列を分断するメリットはないでしょうから、基本的には**末尾か先頭に記述することをおすすめします**。

>[!CAUTION]
> VFMにはブロック要素のidやclassを指定する機能がありますが、clonoを利用する際にはこの機能が正しく動作しないケースがあり、特に見出し周りでは確実に機能しないことを確認しています。いずれはこの記法も利用できるようにしたいと考えていますが、現時点では制限事項とさせてください。
> 
> ```markdown
> <!-- このような記述はできない -->
> # 見出し{#id .class}
> ```

### 見出し参照

章IDや見出しIDを章番号、節番号……に展開する`refHeading`と`refHeadingName`が提供されています。

| 要素名           | 展開時の挙動                                                             |
| ---------------- | ------------------------------------------------------------------------ |
| `refHeading`     | 「第1章」「1.2」のように番号のみが展開される。                           |
| `refHeadingName` | 「第1章 最初の章」「1.2 概要」のように見出し文字列も合わせて展開される。 |

章見出しを参照する場合は`:refHeading{#章ID}`のように記述してください。レベル2以下の見出しを参照する場合、同一の章にある見出しを参照する場合は`:refHeading{#見出しID}`、別の章にある見出しを参照する場合は`:refHeading{#章ID|見出しID}`のように記述してください。`refHeadingName`の場合も同様です。

#### 見出し参照の例

以下のような2つのMarkdownファイルがあるとします。

```markdown
<!-- chapter01.md -->

# 通常役

## 立直:label{riichi}

https://kinmaweb.jp/yaku/reach

## 断么九:label{all-simples}

https://kinmaweb.jp/yaku/tanyao

（※後略）

<!-- chapter02.md -->

# 役満

## 大三元:label{three-dragons}

https://www.youtube.com/watch?v=hLC80pstk7o

## 国士無双:label{atamahane}

https://www.youtube.com/watch?v=3lwJe8JdbOQ

（※後略）
```

このとき、`chapter01.md`内での記述と、展開後の表記は以下のようになります。

<table>
  <thead>
    <tr>
      <th><code>chapter01.md</code>内での記述</th>
      <th>展開後の表記</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>:refHeading{#chapter01}</code></td>
      <td>第1章</td>
    </tr>
    <tr>
      <td><code>:refHeadingName{#chapter01}</code></td>
      <td>第1章 通常役</td>
    </tr>
    <tr>
      <td><code>:refHeading{#all-simples}</code></td>
      <td>1.2</td>
    </tr>
    <tr>
      <td><code>:refHeadingName{#all-simples}</code></td>
      <td>1.2 断么九</td>
    </tr>
    <tr>
      <td><code>:refHeading{#chapter02|three-dragons}</code></td>
      <td>2.1</td>
    </tr>
    <tr>
      <td><code>:refHeadingName{#chapter02|three-dragons}</code></td>
      <td>2.1 大三元</td>
    </tr>
  </tbody>
</table>

> [!TIP]
> 「第1章」「1.2」などの番号の部分はCSSで表現するため、出力後のMarkdown内に直接記述されているわけではありません。

> [!NOTE]
> 開発者の@7pairsは[U-NEXT Pirates](https://m-league.jp/teams/pirates/)を応援しています。

### 画像

`:figure[キャプション]{src=ファイルパス その他の属性}`の記述で画像を埋め込むことができます。「その他の属性」は[VFMの画像記法](https://vivliostyle.github.io/vfm/#/vfm#with-caption-and-single-line)で`{}`内に書かれる属性（`width`など）を想定しています。

```markdown
:figure[サンプル画像]{src=../images/sample.png width=350}
```

後述の画像参照機能を利用する場合、ファイル名の拡張子を除いた部分（上記の例では`sample`）が画像IDとなります。

> [!TIP]
> 上述のとおり、ファイル名の拡張子を除いた部分が画像IDとなるため、**同一の章の中で同じ画像や同じファイル名の画像が複数存在すると、画像参照の機能が正常に動作しません**（別の章であればファイル名が重複しても問題ありません）。
> 
> ```markdown
> <!-- これらの画像はすべてIDが"image"となる -->
> :figure[画像1]{src=../images/image.png}
> :figure[画像2]{src=../images/image.png}
> :figure[画像3]{src=../images/sub/image.png}
> ```

> [!CAUTION]
> Markdownを変換する際に、画像ファイルのパスはそのまま維持されます。画像ファイルを相対パスで指定する場合は、元ファイルからのパスではなく、出力先ディレクトリからのパスを記述してください。

### 画像参照

画像IDを画像番号（図1.2など）に変換する機能です。

同一の章にある画像を参照する場合は`:refFigure{#画像ID}`、別の章にある画像を参照する場合は`:refFigure{#章ID|画像ID}`と指定してください。

### コード

VFMと同様にバックスラッシュ3つで前後を囲ってください。最初のバックスラッシュの後ろには`言語 キャプション`を指定します。キャプション部分には見出しと同様に`:label{#コードID}`と記述してコードIDを付与することもできます。

<pre>
```kotlin サンプルコード:label{#hello}
fun hello() {
    return "Hello, World!"
}
```
</pre>

> [!TIP]
> 構文解析の都合上、言語の指定を省略することはできません。シンタックスハイライトが不要な場合は、プレーンテキスト（`text`、`plaintext`）などを指定してください。
> 
> <pre>
> ```text ハイライト不要:label{#plain}
> これはハイライトされないコードです。
> ```
> </pre>

### コード参照

コードIDをコード番号（リスト1.2など）に変換する機能です。

同一の章にあるコードを参照する場合は`:refCode{#コードID}`、別の章にあるコードを参照する場合は`:refCode{#章ID|コードID}`と指定してください。

### 表

表そのものはVFMやHTMLで記述し、その前後を`:::table[キャプション]{#表ID}`と`:::`で囲んでください。

```markdown
:::table[球場名の変遷]{#nekoyashiki}

| 西暦 | 球場名                |
| ---- | --------------------- |
| 1979 | 西武ライオンズ球場    |
| 1998 | 西武ドーム            |
| 2005 | インボイスSEIBUドーム |
| 2007 | グッドウィルドーム    |
| 2008 | 西武ドーム            |
| 2015 | 西武プリンスドーム    |
| 2017 | メットライフドーム    |
| 2022 | ベルーナドーム        |

:::
```

> [!NOTE]
> 開発者の@7pairsは[埼玉西武ライオンズ](https://www.seibulions.jp/)を応援しています。

### 表参照

表IDを表番号（表1.2など）に変換する機能です。

同一の章にある表を参照する場合は`:refTable{#表ID}`、別の章にある表を参照する場合は`:refTable{#章ID|表ID}`と指定してください。

### 脚注

VFMの後注（章の末尾に説明文が出力される）の機能を脚注（各ページの下部に説明文が出力される）の機能として再定義しました。

```markdown
これは本文です[^1]。これはもうひとつの本文です[^fn]。

[^1]: これは脚注です。
[^fn]: 脚注のIDは数字でなくても構いません。
```

>[!CAUTION]
> 逆に後注の機能は使えなくなってしまいます。将来的にはON/OFFができるようにしたいと考えていますが、現時点では制限事項とさせてください。

### 索引

`:index[単語]{ruby=読み}`で索引項目を作成します。「読み」は日本語の場合はひらがなで、英数字の場合は大文字で記述してください。

索引項目を文章中で強調したい場合は、`:index[単語]{ruby=読み strong}`と記述してください。`**:index[単語]{ruby=読み}**`と書くと正確に展開されません。

```markdown
:index[clono]{ruby=CLONO strong}は:index[原稿]{ruby=げんこう}内のVFM記法を拡張します。
```

### コラム

コラムの前後を`:::column[キャプション]`と`:::`で囲んでください。

```markdown
:::column[コラムのタイトル]

コラムの内容です。

:::
```

## プラグイン機能

clonoは、特定のノードタイプの出力処理をカスタマイズするためのプラグイン機能を提供しています。プラグインを作成することで、既存の記法の挙動を変更したり、独自の記法を追加することができます。

### プラグインの配置

プラグインは`clono.js`を配置したディレクトリ内の`plugins`フォルダに格納します。ファイル名は処理対象のノードタイプに合わせて命名してください。たとえば、`code`ノードの処理をカスタマイズする場合は`code.js`というファイル名にします。

### プラグインの実装

プラグインはCommonJSモジュールとして実装します。以下は簡単なプラグインの例です。

```javascript
module.exports = function(node, baseName) {
    // nodeは処理対象のAST（抽象構文木）ノード
    // baseNameは処理中のMarkdownファイル名（拡張子を除いた部分）

    // HTMLを文字列で返す
    return "<div class='custom-node'>カスタム処理されたノード</div>";
};
```

プラグインが適切なHTMLを返すと、その出力がclonoの標準処理よりも優先して使用されます。プラグインの処理中にエラーが発生した場合は、自動的に標準処理にフォールバックします。

### 対応ノードタイプ

プラグインで処理可能な主要なノードタイプは以下の通りです。

- `paragraph` - 段落
- `heading` - 見出し
- `thematicBreak` - 水平線
- `blockquote` - 引用
- `list` - リスト
- `listItem` - リスト項目
- `code` - コードブロック
- `html` - HTML

また、clono独自の拡張ノードタイプも対象にできます。

- `column` - コラム
- `figure` - 画像
- `index` - 索引
- `refCode`, `refFigure`, `refHeading`, `refHeadingName`, `refTable` - 各種参照

### 動作確認

プラグインが正しく動作しているかどうかを確認するには、通常どおりclonoを実行し、出力されたMarkdownファイルの内容を確認してください。また、処理中に問題が発生した場合は、コンソールにエラーや警告が出力されます。

## トラブルシューティング

よくある問題と解決策を紹介します。

### 参照リンクが機能しない

- IDが正しく指定されていることを確認してください。
- 別の章を参照する場合、章IDが正しく指定されていることを確認してください。

### プラグインが読み込まれない

- プラグインファイルが`plugins`ディレクトリに配置されていることを確認してください。
- ファイル名がノードタイプと一致していることを確認してください。
- プラグインのコードが有効なJavaScriptであることを確認してください。

## 謝辞

はじめに、Vivliostyleの開発関係者のみなさまに感謝いたします。[公式サイト](https://vivliostyle.org/ja/)のタイトルに書かれている「楽しくCSS組版！」が実現できているのは、みなさまの尽力のおかげです。ありがとうございます。

また、clonoの開発にあたっては、以下の書籍や同人誌を参考にさせていただきました。ありがとうございます。

- リブロワークス（著），Vivliostyle（監修）（2023）『[Web技術で「本」が作れるCSS組版Vivliostyle入門](https://www.c-r.com/book/detail/1493)』シーアンドアール研究所
- 古賀広隆（著）（2024）『[VivlioStyleとRehype/RemarkではじめるCSS組版による同人誌制作](https://techbookfest.org/product/irrGnuyPPYz2g1jHizV6cZ?productVariantID=gGZyLqhPz1LhFsPC0RemrM)』げぐはつ書房

clonoというツール名は、開発言語であるClojureScriptに由来していますが、VTuberの[千羽黒乃さん](https://www.youtube.com/@senba_crow)に（勝手に）あやかったものでもあります。千羽師匠の今後のますますのご活躍を祈念しておりますのじゃ。

## 変更ログ

### v1.1.0 (2025-05-06)

- エラーメッセージの強化
- 内部構造の見直し

### v1.0.0 (2025-04-09)

- 初回リリース

## ライセンス

clonoは[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)のもとで公開します。
