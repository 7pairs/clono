# プラグインrenderer契約の検証用fixture

## 目的

コラムの出力を利用者定義のrendererで差し替える最小プラグイン契約を検討するため、既定rendererと外部rendererが共有できる入出力境界を段階的に検証する。

最初の検証では、現在のclonoが生成するコラム用のVFM向けMarkdownを、`title`と`body`を受け取る独立した既定rendererで再現できることを確認する。候補となるプロパティ名、文字列形式および関数シグネチャは、正式なプラグイン契約ではない。

## 現在の構成

```text
plugin-renderer-contract/
├── .gitignore
├── README.md
├── package.json
├── package-lock.json
├── scripts/
│   └── verify-vfm.mjs
├── shadow-cljs.edn
└── src/
    ├── main/clono/research/
    │   ├── custom_column_renderer.js
    │   ├── generate_markdown.cljs
    │   └── plugin_renderer_contract.cljs
    └── test/clono/research/
        └── plugin_renderer_contract_test.cljs
```

## 候補となる既定renderer

既定rendererは、次のJavaScriptオブジェクトを入力候補とする。

```javascript
{
  title: "ちょっと休憩",
  body: "本文には**強調**がある。"
}
```

`title`は意味検証済みのプレーンテキストであり、rendererがHTMLのテキスト内容としてエンコードする。`body`は許可する内容モデルの検証と必要な組み込み変換を終えた後、VFM向けMarkdownへ直列化した本文とする。著者が入力した未検証のMarkdown文字列を、そのまま外部プラグインへ渡す契約ではない。

既定rendererは、現在のコラム変換と同じ次のVFM向けMarkdown断片を返す。

```markdown
<aside class="clono-column">

<p class="clono-column-title">ちょっと休憩</p>

本文には**強調**がある。

</aside>
```

戻り値は完成したHTML文書でも、コラム本文をHTMLへ変換した文字列でもない。コラム用のraw HTMLと、VFMへ委譲する本文Markdownを組み合わせたMarkdown断片である。

## 候補となるカスタムrenderer

`custom_column_renderer.js`は、既定rendererと同じ入力を受け取り、印刷用テーマで外観を細かく制御する用途を模した次のMarkdown断片を返す。

```markdown
<aside class="clono-column custom-column">
<div class="custom-column-outer">
<div class="custom-column-inner">
<p class="clono-column-title custom-column-title">
<span class="custom-column-title-mark" aria-hidden="true">COLUMN</span>
<span class="custom-column-title-text">ちょっと休憩</span>
</p>
<div class="custom-column-body">

本文には**強調**がある。

</div>
</div>
</div>
</aside>
```

既存の基盤CSSが対象とする`clono-column`と`clono-column-title`は残し、利用者固有の外観に使用するclassを追加する。タイトルはカスタムrenderer自身がHTMLのテキスト内容としてエンコードし、装飾用の文字列と著者が指定したタイトルを別々の`span`へ配置する。本文Markdownは内側の`div`へ置くが、renderer内ではHTMLへ変換しない。候補形式では、本文枠の開始タグ直後と終了タグ直前に空行を置き、raw HTMLの内側にある本文をVFMがMarkdownとして処理できる境界を作る。

このJavaScriptファイルはfixtureのClojureScriptビルドへ直接含める。ローカル`.mjs`の動的読み込みは[プラグイン読み込み方式のfixture](../plugin-loading/)で検証済みであり、この段階では読み込み方式ではなく、既定rendererとカスタムrendererが同じ入出力境界を共有できることに検証対象を絞る。

## 検証環境

- 実行確認: macOS、Node.js 24.19.0、Temurin JDK 21.0.12
- 対応範囲: Node.js 22.13.0以降の22系、または24系
- shadow-cljs 3.4.12
- HTML変換: `@vivliostyle/vfm` 2.7.0
- HTML検証: `node-html-parser` 9.0.1

正確な依存関係は`package.json`と`package-lock.json`で固定する。

## 実行方法

```shell
set -eu
npm ci
npm run verify
```

`target/`、`.shadow-cljs/`および`node_modules/`は生成物であり、Gitの管理対象には含めない。

## 現在の自動検証

`src/test/clono/research/plugin_renderer_contract_test.cljs`は、次を確認する。

- 既定rendererが、現在の`aside.clono-column`と`p.clono-column-title`を持つMarkdown断片を返す
- タイトルに含まれるHTML上の特殊文字を、現在のコラム変換と同じ文字参照へエンコードする
- 複数ブロックを含む本文Markdownを変更しない
- 凍結した入力オブジェクトを変更しない
- 同じ呼び出し境界でJavaScript製のカスタムrendererを実行できる
- カスタムrendererが二重・三重のラッパーと複数の`span`を含むMarkdown断片を返せる
- カスタムrendererでもタイトルをHTMLエンコードし、本文Markdownを変更しない

`src/main/clono/research/generate_markdown.cljs`は、既定rendererとカスタムrendererを使用して`output/`へ二つのMarkdown断片を生成する。`scripts/verify-vfm.mjs`はそれらをVFM 2.7.0でHTMLへ変換し、次を確認する。

- 既定rendererとカスタムrendererの両方で、コラムのタイトルと本文が保持される
- 閉じタグ、`script`要素に見える文字列および引用符を含む動的なタイトルがHTMLのテキスト内容としてエンコードされ、VFM変換後も要素や属性として解釈されない
- 強い強調、強調、インラインコードおよび外部リンクが、対応するHTML要素へ変換される
- 箇条書きと二つの項目が保持される
- カスタムrendererの外枠、内枠、本文枠およびタイトル用`span`が、期待する親子関係で保持される
- カスタムrendererの本文Markdownが、最も内側の本文枠でHTMLへ変換される

生成したMarkdownとHTMLは`output/`へ保存するが、Gitの管理対象には含めない。

## 検証範囲の境界

現時点では、次の事項を検証または決定しない。

- 動的に読み込んだ外部プラグインのrendererによる差し替え
- clono本体のmdastからrendererへ本文Markdownを渡す処理と、戻り値をmdastへ戻す処理
- コラム本文に含まれる索引指定、脚注、画像、表、コードブロックなどとの結合
- rendererが返す任意のraw HTMLとMarkdownに対する一般的な構造検証
- `title`と`body`以外に公開する情報
- rendererの正式な関数シグネチャ、診断および失敗契約
- `clono transform`または`clono build`との統合

後続の検証では、rendererの戻り値をmdastへ安全に戻せるかと、変換済みの索引指定、脚注などを含む実際のコラム本文を同じ境界で扱えるかを確認する。

## 参照資料

- [clono著者向け記法](../../../specifications/authoring-syntax.md#コラム記法)
- [Vivliostyleのコラム表現に関する調査](../../vivliostyle-column.md)
- [ローカルES Moduleによるプラグイン読み込み方式に関する調査](../../plugin-loading.md)
