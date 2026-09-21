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
├── shadow-cljs.edn
└── src/
    ├── main/clono/research/
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

## 検証環境

- 実行確認: macOS、Node.js 24.19.0、Temurin JDK 21.0.12
- 対応範囲: Node.js 22.13.0以降の22系、または24系
- shadow-cljs 3.4.12

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

## 検証範囲の境界

現時点では、次の事項を検証または決定しない。

- 外部プラグインのrendererによる差し替え
- rendererへ本文Markdownを渡す処理と、戻り値をmdastへ戻す処理
- コラム本文に含まれる索引指定、脚注、画像、表、コードブロックなどとの結合
- 戻り値に含まれるraw HTMLとMarkdownの構造検証
- `title`と`body`以外に公開する情報
- rendererの正式な関数シグネチャ、診断および失敗契約
- `clono transform`または`clono build`との統合

後続の検証では、同じ境界を使用して外部rendererによる多重ラッパーを試作し、VFM変換後のHTML構造と本文Markdownの保持を確認する。

## 参照資料

- [clono著者向け記法](../../../specifications/authoring-syntax.md#コラム記法)
- [Vivliostyleのコラム表現に関する調査](../../vivliostyle-column.md)
- [ローカルES Moduleによるプラグイン読み込み方式に関する調査](../../plugin-loading.md)
