# プラグイン読み込み方式の検証用fixture

## 目的

ローカルのES Moduleをclonoのプラグイン候補として読み込み、ClojureScriptから検証および呼び出しできるかを調査するための独立したfixtureである。

このコミットでは、調査に使用するNode.jsとClojureScriptの実行環境、テストの入口、および再現手順だけを用意する。プラグインモジュール、候補設定、読み込み処理および正式なプラグイン契約はまだ実装しない。

## 現在の構成

```text
plugin-loading/
├── .gitignore
├── README.md
├── package.json
├── package-lock.json
├── shadow-cljs.edn
└── src/
    └── test/
        └── clono/
            └── research/
                └── plugin_loading_test.cljs
```

後続の検証では、このfixtureへ候補となる書籍プロジェクト、プラグインモジュール、およびClojureScriptの読み込み処理を追加する。

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

`src/test/clono/research/plugin_loading_test.cljs`は、fixtureのテストがNode.js上で実行されることだけを確認する。プラグイン読み込みに関する検証ケースは、後続のコミットで追加する。

## 検証範囲の境界

このfixtureはclono本体から参照しない。現時点では、次の事項を検証または決定しない。

- `clono.config.mjs`におけるプラグイン設定の形式
- プラグインモジュールのexport形式と基本情報
- プラグインパスの許可範囲
- rendererへ渡すデータと戻り値
- rendererの同期実行と失敗契約
- 複数プラグインの競合規則
- `clono transform`および`clono build`との統合

これらはfixtureへ再現可能な検証ケースを追加した後、調査記録、仕様およびADRで段階的に決定する。
