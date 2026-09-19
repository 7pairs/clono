# プラグイン読み込み方式の検証用fixture

## 目的

ローカルのES Moduleをclonoのプラグイン候補として読み込み、ClojureScriptから検証および呼び出しできるかを調査するための独立したfixtureである。

このfixtureでは、候補設定に列挙したローカルのES Moduleを、設定ファイルがあるディレクトリを基準に読み込めることを検証する。候補設定とモジュールの形は読み込み方式を調査するための入力であり、正式なプラグイン契約ではない。

## 現在の構成

```text
plugin-loading/
├── .gitignore
├── README.md
├── package.json
├── package-lock.json
├── project with space#hash/
│   ├── clono.config.mjs
│   ├── renderer-behaviors.config.mjs
│   └── plugins/
│       ├── basic plugin.mjs
│       ├── cached-plugin.mjs
│       ├── invalid-return-plugin.mjs
│       ├── promise-return-plugin.mjs
│       ├── throwing-plugin.mjs
│       └── top-level-await#plugin.mjs
├── shadow-cljs.edn
└── src/
    ├── main/clono/research/
    │   ├── plugin_loading.cljs
    │   └── verify_loading.cljs
    └── test/clono/research/plugin_loading_test.cljs
```

`project with space#hash/clono.config.mjs`は、候補となる`plugins`配列をtop-level awaitで作成する。各相対パスは、設定ファイルと同じディレクトリを基準に解決する。プラグイン候補には、通常のdefault export、top-level awaitを使用するモジュール、および評価回数を記録するモジュールを用意する。

`src/main/clono/research/plugin_loading.cljs`は、ファイルシステム上の絶対パスを`file:` URLへ変換してから`import()`へ渡す。プラグイン候補のdefault exportは検証せず、ES Module namespace objectのまま結果へ保持する。

各候補モジュールのdefault exportには、相互運用を検証するため、`name`、`version`、`apiVersion`および`renderers.column`を持つJavaScriptオブジェクトを使用する。ClojureScriptは、このオブジェクトと関数をClojureScriptのデータ構造へ変換せずに参照する。これらの項目名、値、必須性およびrendererの入出力は正式な契約ではない。

`renderer-behaviors.config.mjs`は、rendererが例外を投げる候補、文字列以外を返す候補、およびPromiseを返す候補を列挙する。調査用の呼び出し処理は同期的に文字列を返すrendererだけを受理し、これら3種類を互いに区別できる結果として返す。結果のキー、値および診断文言は正式な失敗契約ではない。

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

`src/test/clono/research/plugin_loading_test.cljs`は、fixtureのテストがNode.js上で実行されることを確認する。

実際の`import()`は、Shadow CLJSの`:node-test`ターゲットが使用するVM評価ではなく、releaseビルドした通常のNode.jsスクリプトで検証する。`src/main/clono/research/verify_loading.cljs`をビルドした`target/verify-loading.cjs`は、次を確認する。

- 起動時のカレントディレクトリと異なる場所にある候補設定を読み込める
- 空白と`#`を含むファイルシステム上のパスを安全な`file:` URLとして読み込める
- 設定ファイルの場所を基準に、候補設定へ記述した相対パスを解決する
- 候補設定に記述した順序でモジュールを返す
- 設定ファイルとプラグインモジュール内のtop-level awaitを評価できる
- 同じURLのモジュールを繰り返し読み込んでも、Node.jsのimportキャッシュによって一度だけ評価される
- ClojureScriptから候補プラグインの`name`、`version`および`apiVersion`を読み取れる
- ClojureScriptから`renderers.column`を複数回呼び出し、日本語を含むJavaScriptオブジェクトを渡して文字列を受け取れる
- 凍結した入力オブジェクトを変更せずにrendererを実行できる
- 同じモジュールを繰り返し読み込んだ場合にrenderer関数の同一性が保たれる
- 同期的に文字列を返すrendererを受理できる
- rendererが同期的に投げた例外を捕捉し、元の例外を保持できる
- JavaScriptオブジェクトを返すrendererを不正な戻り値として識別できる
- Promiseを返すrendererを待機せず、非同期の戻り値として識別できる

## 検証範囲の境界

このfixtureはclono本体から参照しない。現時点では、次の事項を検証または決定しない。

- `plugins`配列を含む候補設定を正式な`clono.config.mjs`へ採用するか
- 候補としたdefault export、`name`、`version`、`apiVersion`および`renderers`を正式な契約へ採用するか
- プラグインパスの許可範囲
- rendererへ渡す正式なデータ構造と、raw HTML文字列に課す制約
- rendererの例外、不正な戻り値およびPromiseに対する正式な診断と失敗契約
- 複数プラグインの競合規則
- `clono transform`および`clono build`との統合

これらはfixtureへ再現可能な検証ケースを追加した後、調査記録、仕様およびADRで段階的に決定する。
