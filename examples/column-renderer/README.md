# 多重ラッパーを持つコラムrendererの例

前作『Androidを 実機で テストしろ!!』の「`div.column`の中にタイトルと本文を置く」構造を出発点として、印刷用テーマのために三重の`div`とタイトル内の`span`を追加したサンプルです。前作のデザインそのものを再現するものではありません。

## 使い方

[`column.mjs`](column.mjs)を、書籍プロジェクトの`plugins/column.mjs`へコピーします。プロジェクトルートの`clono.config.mjs`に次の指定を追加してください。

```javascript
export default {
  sourceRoot: "manuscripts",
  outputRoot: "build/manuscripts",
  publication: [
    { type: "document", path: "chapter.md", kind: "chapter", includeInToc: true },
  ],
  plugins: ["./plugins/column.mjs"],
};
```

原稿では通常のコラム記法を使用します。

```markdown
:::column[ちょっと休憩]
本文には**強調**や[リンク](https://example.com/)を含められます。
:::
```

`clono build <project>`で変換すると、外側の`div.column`、装飾用の`div.column-frame`、本文を包む`div.column-content`の順に出力します。タイトルは`h4`内の`span.column-title-text`へ、本文はその後へMarkdownのまま配置します。本文のHTML化はVFMに任せます。

外側の`.clono-column`とタイトルの`.clono-column-title`を残しているため、clonoの基盤CSSが定める改ページ制御は利用できます。追加したclassの見た目は、書籍のテーマCSSで指定してください。

タイトルをHTMLテキストとして出力する前にエンコードします。本文はMarkdownとして処理させるため、全体をHTMLエンコードしません。前作ではタイトルからIDを生成していましたが、この例では同名のコラムによるID重複を避けるため、IDを付けません。

プラグインは信頼されたローカルコードとして実行され、clonoは返されたHTMLをサニタイズしません。出力の安全性とテーマCSSの整合はプラグイン利用者が確認してください。詳しい契約は[プラグイン仕様](../../docs/specifications/plugins.md)を参照してください。
