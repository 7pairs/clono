# Markdown見出しIDのmdast検証fixture

このfixtureは、VFMの明示的な見出しID記法を、clonoと同じMarkdown解析・直列化ライブラリで処理したときのmdastを確認する。

見出し参照の最終仕様や本体実装を決定するものではなく、仕様策定に必要な技術的前提を再現可能な形で保存することを目的とする。

## 検証対象

- `h1`から`h4`までの見出しレベル
- プレーンテキストの見出しに付けた`{#id}`
- インラインコードと強調を含む見出しに付けた`{#id}`
- 明示IDを持たない見出し
- clonoの論理ID規則に一致しないID候補
- 同じ明示IDを持つ複数の見出し
- ID記法ではない通常の波括弧
- Markdownへ直列化し、再解析した後の構造

入力は[`input/headings.md`](input/headings.md)に保存している。

## 検証環境

- 実行確認: macOS、Node.js 24.19.0、Temurin JDK 21.0.12
- 対応範囲: Node.js 22.13.0以降の22系、または24系
- shadow-cljs 3.4.12
- `mdast-util-from-markdown` 2.0.3
- `mdast-util-to-markdown` 2.1.2
- clono本体と同じGeneric Directives、GFM脚注およびGFM表の解析拡張

正確な依存関係は`package.json`と`package-lock.json`で固定する。

## 実行方法

```shell
npm ci
npm test
```

完全なmdastを目視する場合は、次のコマンドを実行する。

```shell
npm run inspect
```

## 確認する契約

- `mdast-util-from-markdown`は、VFMの`{#id}`を専用プロパティとして解析せず、見出しの最後にあるtextノードへ保持する
- 見出しレベル、インラインMarkdownおよび入力位置は、ID候補と同時に参照できる
- 見出し末尾のID候補は、最後のtextノードから抽出できる
- 明示IDのない見出しと、ID記法ではない通常の波括弧をID候補として扱わない
- 重複するID候補は別々の見出しノードとして保持され、直列化前に検査できる
- clonoの論理ID規則に一致しない候補も直列化前に検査できる
- 不正な候補に含まれるMarkdown上の記号は直列化時にエスケープされる場合があるが、再解析したtextノードでは同じ候補値として参照できる
- 不正な候補は、clonoのパイプライン契約に従い、AST変換と直列化より前に診断する

このfixtureは、VFMが最終HTMLへ見出しIDを出力することや、Vivliostyleが見出し番号と参照文字列を生成することを再検証しない。これらの挙動は、[Vivliostyleの見出しID・連番・相互参照に関する調査](../../vivliostyle-heading-references.md)と、その検証用fixtureで確認している。
