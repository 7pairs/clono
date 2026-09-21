# コラムrenderer契約に関する調査

- 状態: 調査済み
- 調査日: 2026-09-21
- 最終更新日: 2026-09-21
- 検証環境:
  - OS: macOS 26.6.2
  - Node.js: 24.19.0
  - npm: 11.17.0
  - JDK: Temurin 21.0.12
  - ClojureScriptビルド: shadow-cljs 3.4.12
  - Markdown変換: `@vivliostyle/vfm` 2.7.0

## 背景

[プロジェクト憲章](../project-charter.md)では、利用者がJavaScriptなどで変換処理を追加または差し替えられるプラグインシステムを、clonoの将来の方向性として定めている。Thunder Clawの次回作では、印刷用の外観に合わせてコラムを二重、三重の要素や装飾用の`span`で囲む必要が生じる可能性が高い。

clono本体へ特定テーマ用のHTML構造を組み込むのではなく、最初の具体的な拡張点として、コラムの出力を利用者定義のrendererで差し替えることを検討する。[ローカルES Moduleによるプラグイン読み込み方式に関する調査](plugin-loading.md)では、ローカル`.mjs`の読み込みとrenderer関数の呼び出しが成立することを確認した。本調査では、そのrendererへ渡す候補データ、戻り値、VFMとの境界、失敗時の扱い、および単一ファイル変換からプロジェクト設定を選択する方法を検証する。

## 調査目的

- 現在のコラム出力を独立した既定rendererで再現できるか確認する
- 同じ呼び出し境界で、印刷用の多重ラッパーを持つ外部rendererを実行できるか確認する
- rendererが返すraw HTMLとMarkdownの混在形式をVFMが処理できるか確認する
- 動的なタイトルをHTMLのテキスト内容として安全に配置できるか確認する
- rendererの例外、不正な戻り値およびPromiseを、部分出力を伴わない変換失敗として扱えるか確認する
- `clono transform`から利用するプロジェクト設定を明示する方法を検討する

本調査は、公開API、設定項目、CLIオプション、診断コードまたは文言を正式に決定するものではない。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/plugin-renderer-contract/)をリポジトリ内へ保存している。このfixtureはclono本体から独立したClojureScriptおよびnpmプロジェクトであり、`package.json`と`package-lock.json`によって依存関係を固定する。

### 候補となる入出力

rendererへ渡す値には、次の凍結したJavaScriptオブジェクトを使用した。

```javascript
{
  title: "ちょっと休憩",
  body: "本文には**強調**がある。"
}
```

`title`は意味検証済みのプレーンテキスト、`body`は許可する内容モデルの検証と必要な組み込み変換を終えた後のVFM向けMarkdownを想定した。ただし、本調査ではclono本体のmdastからこのオブジェクトを構築しておらず、この前提を製品コードで実現する処理は未検証である。

rendererは、完成したHTML文書ではなく、コラム用のraw HTMLと本文Markdownを組み合わせた文字列を同期的に返す候補とした。既定rendererの戻り値を次に示す。

```markdown
<aside class="clono-column">

<p class="clono-column-title">ちょっと休憩</p>

本文には**強調**がある。

</aside>
```

本文を囲むraw HTMLの開始タグ直後と終了タグ直前に空行を置き、VFMが内側をMarkdownとして処理できる境界を作った。

### カスタムrenderer

印刷用テーマで必要となり得る構造を模し、外枠、内枠、本文枠、および二つのタイトル用`span`を持つrendererをJavaScriptで作成した。

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

clono基盤CSSとの接点として、既存の`clono-column`と`clono-column-title`を保持し、利用者固有のclassを追加した。このclass構成も正式なプラグイン契約として決定したものではない。

### 失敗時の候補境界

複数のコラムを順にrendererへ渡す候補処理を作成し、途中で次の問題が起きる場合を検証した。

- rendererが同期的に例外を投げる
- rendererが文字列ではなくJavaScriptオブジェクトを返す
- rendererがPromiseを返す

候補処理は、成功したコラムの出力を変換完了まで公開せず、失敗時には`output`を`nil`として位置付き診断を返す。rendererが例外を投げた場合は、その時点で後続のコラムを呼び出さない。

### 単一ファイル変換からの設定選択

プラグインを使用する単一ファイル変換では、入力ファイルから設定を暗黙に探索せず、次のようにプロジェクトを明示する候補を検証した。

```text
clono transform <input> --output <output> --project <project>
```

`--project`を指定した場合は、起動時のカレントディレクトリを基準に`<project>`を解決し、その直下の`clono.config.mjs`を読み込む。設定内のプラグインパスは、設定ファイルがあるディレクトリを基準に解決する。省略した場合は設定を読み込まず、組み込みの既定rendererを使用する。

fixtureでは、起動場所とは異なり、空白と`#`を含むプロジェクトを相対パスで指定した。設定とプラグインを`pathToFileURL()`で`file:` URLへ変換し、releaseビルドしたNode.jsスクリプトから読み込んだ。

## 検証結果

### 既定rendererとカスタムrenderer

既定rendererは、調査時点のclonoが生成する`aside.clono-column`と`p.clono-column-title`を持つMarkdown断片を再現できた。同じ`title`と`body`をカスタムrendererへ渡し、多重ラッパーと複数の`span`を持つ別の断片も生成できた。入力オブジェクトを凍結しても両rendererは動作し、入力値を変更しなかった。

この結果から、コラム固有の意味データと出力構造を分離し、組み込みrendererと外部rendererを同じ呼び出し境界で扱う構成は成立する。

### 本文MarkdownとVFMの境界

既定rendererとカスタムrendererの戻り値をVFM 2.7.0へ渡した。強い強調、強調、インラインコード、外部リンクおよび箇条書きは、いずれも期待するHTML要素へ変換された。カスタムrendererでは、これらの要素が最も内側の本文枠に配置され、多重ラッパーの親子関係も保持された。

したがって、本文枠の開始タグ直後と終了タグ直前に空行を置く形式により、rendererが外側のraw HTML構造を変更しながら、本文のMarkdown処理をVFMへ委譲できる。

ただし、索引指定、脚注、番号付き画像、表およびコードブロックなど、clono固有の変換を含む実際のコラム本文との結合は確認していない。

### 動的なタイトルのHTMLエンコード

タイトルへ`&`、閉じタグ、`script`要素に見える文字列、HTML属性に見える文字列、および引用符を含めた。既定rendererとカスタムrendererはいずれも、これらをHTMLのテキスト内容として文字参照へ変換できた。

VFM変換後は元のタイトル文字列として復元され、`script`要素や意図しない属性は生成されなかった。タイトルをraw HTMLへ配置するrendererが、その値をHTMLのテキスト内容としてエンコードする責務を持つ構成は成立する。

この結果は、rendererが返す任意のraw HTML全体をclonoが安全化できることを意味しない。ローカルプラグインは利用者が信頼するコードであり、固定の要素や属性を含む出力構造そのものはプラグインの責任で生成される。

### 変換停止と診断

二番目のコラムでrendererが例外を投げるケースでは、一番目について生成済みの断片も返さず、三番目のrendererを呼び出さなかった。結果は`output`を`nil`とし、失敗した原稿のファイル名、行、列、directive名および元の例外メッセージを含む候補診断へ変換できた。

JavaScriptオブジェクトを返すrendererとPromiseを返すrendererも、文字列として処理せず、互いに異なる理由を持つ失敗として扱えた。したがって、初期rendererを同期関数に限定し、診断が一件でもあれば部分的な変換結果を公開しない構成は成立する。

fixtureで使用した診断の項目、コードに相当する分類、文言および最初のrenderer失敗で処理を停止する規則は仮の契約である。

### `transform`からのプロジェクト設定

候補の`--project`を入力および出力とは独立して解析し、明示したプロジェクトの`clono.config.mjs`から外部rendererを読み込んで変換へ適用できた。空白と`#`を含むパスも扱えた。

プロジェクトの`manuscripts`ディレクトリを起動場所としても、`--project`を省略した場合は祖先の設定を探索せず、既定rendererを使用した。この結果から、既存の設定なし単一ファイル変換を維持しながら、必要な場合だけプロジェクト設定を明示する構成は成立する。

## 評価

コラム出力を差し替える最小rendererは、技術的に成立する。初期契約の候補として、意味検証済みの`title`とVFM向けMarkdownの`body`を持つJavaScriptオブジェクトを同期関数へ渡し、raw HTMLとMarkdownを組み合わせた文字列を受け取る方式を採用できる。

外部rendererは多重ラッパーや装飾用`span`を自由に構築できるため、Thunder Clawの印刷用テーマに必要なHTML構造をclono本体へ固定せず実現できる。既定rendererも同じ境界へ置くことで、組み込み処理だけが特権的な出力経路へ依存することを避けられる。

また、renderer失敗時に部分出力を公開しない契約と、`transform`でプロジェクトを明示的に選択する候補も成立した。これらは最小プラグイン仕様とADRを作成するための根拠にできる。

一方、今回のfixtureは製品コードのmdast変換、正式な設定検証またはCLIへ統合されていない。本調査の候補名やデータ構造を、そのまま後方互換性を持つ公開APIとして扱わない。

## 後続設計への示唆

後続の仕様およびADRでは、次の方針を候補にできる。

- 最初の公開拡張点をコラムの出力を差し替える`column` rendererとする
- 組み込みの既定rendererと外部rendererを同じ関数境界で扱う
- rendererへ凍結したJavaScriptオブジェクトを渡し、初期契約では`title`と`body`を公開する
- `title`を意味検証済みのプレーンテキスト、`body`をclonoの必要な変換を終えたVFM向けMarkdownとする
- rendererを同期関数へ限定し、raw HTMLと本文Markdownを組み合わせた文字列だけを正常な戻り値として受理する
- 動的な値をraw HTMLへ配置するrendererに、文脈に応じたエンコードを要求する
- rendererの例外、文字列以外の戻り値およびPromiseを診断し、部分的な出力を公開しない
- `transform`でプラグインを使用する場合は、祖先探索ではなくプロジェクトをCLIで明示する
- `--project`を省略した`transform`では、設定を読み込まず既定rendererを使用する

これらは正式な決定ではない。設定、プラグイン情報、公開データ、診断および互換性規則を仕様とADRで決定してから実装する。

## 成立条件と未確認事項

今回の結果は、VFM 2.7.0が処理できるraw HTMLとMarkdownの混在形式、Node.jsの標準ES Moduleとして読み込めるローカル`.mjs`、およびNode.jsの通常プロセスで動作するreleaseビルドを前提とする。

次の事項は検証または決定していない。

- `plugins`を正式な`clono.config.mjs`の設定項目とする場合の構造と検証規則
- `--project`を正式なCLIオプションとするか、およびその引数エラー契約
- プラグインパスの許可範囲、シンボリックリンク、およびプロジェクトルート外への参照
- renderer名、プラグイン名、バージョンおよび`apiVersion`の正式な契約
- `title`と`body`以外に公開する値
- clono本体のmdastから`body`を構築し、rendererの戻り値をmdastへ安全に戻す方法
- 索引指定、脚注、画像、表およびコードブロックを含むコラム本文との結合
- rendererが返すraw HTMLとMarkdownに対する構造検証
- rendererが生成する固定HTML、URL属性、class名またはその他の動的な値に対する安全条件
- 診断コード、文言、順序、複数の失敗を収集する範囲、および処理継続規則
- 複数rendererの合成、適用順序または状態共有
- `clono build`へのプラグイン統合
- Node.js 22系、WindowsおよびLinuxでの実行

## 再現方法

[検証用fixtureのREADME](fixtures/plugin-renderer-contract/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm run verify`でClojureScriptテスト、VFMとの結合検証、およびreleaseビルドしたNode.jsスクリプトによるプロジェクト設定の読み込み検証を実行できる。

## 再調査する条件

- VFMがraw HTML内のMarkdownを処理する条件または出力構造が変わる場合
- rendererへ渡す本文をMarkdown文字列として表現できない要件が生じた場合
- コラム以外の標準Markdown、組み込み記法または文書全体を置換する拡張点が必要になった場合
- 非同期renderer、複数rendererの合成または状態共有が必要になった場合
- 信頼されていない第三者のrendererを安全に実行する必要が生じた場合
- Windows、LinuxまたはNode.js 22系で、VFM変換、パス解決またはES Module読み込みの差異が見つかった場合

## 参照資料

- [プロジェクト憲章](../project-charter.md)
- [0003: Generic DirectivesとmdastによるMarkdown変換パイプラインを採用する](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [ローカルES Moduleによるプラグイン読み込み方式に関する調査](plugin-loading.md)
- [Vivliostyleのコラム表現に関する調査](vivliostyle-column.md)
- [検証用fixture](fixtures/plugin-renderer-contract/)
