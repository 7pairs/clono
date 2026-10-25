<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# clono

clonoは、Vivliostyleで技術同人誌を制作するために、不足する機能を補うツールである。開発者と、開発者が所属する技術同人誌サークル「Thunder Claw」の用途を優先して開発する。

## 開発について

開発の進め方と関連文書は、[開発フロー](docs/development-workflow.md)を参照する。

本体は可能な限りClojureScriptで実装し、Node.js上で実行する。shadow-cljsでESMを生成し、本ごとのJavaScript拡張は.mjsファイルで扱う方針である。環境要件と制約は[実装環境の仕様](docs/specs/implementation-environment.md)、採用理由は[ADR 0002](docs/decisions/0002-choose-implementation-environment.md)にまとめている。

### セットアップと検証

Node.js 24.22.1、npm 11.21.1、SDKMANで管理するTemurin 21.0.12を用意する。Node.jsの指定は`.node-version`、JDKの指定は`.sdkmanrc`に記載している。以下はリポジトリルートで実行する。

Node.js・npmはセキュリティ対応として更新した。更新後の依存導入・ビルド・テスト・REPLは未検証であり、[更新の手順書](docs/plans/0007-update-nodejs-for-security.md)の手順3で確認する。npmの採用条件と残る問題は[開発環境の仕様](docs/specs/development-environment.md#npmの採用条件と残る問題)を参照する。

```sh
sdk env
node --version
npm --version
java --version
npm ci
npm run verify
```

`sdk env`は、SDKMANと指定のJDKが導入済みのシェルで実行する。既定のJDKは変更しない。従来の環境では、このセットアップ手順をローカルのmacOS環境で確認した。CIはセットアップActionsで各OSの環境を準備する。

`verify`は、生成処理のテスト、CLJSの単体テスト、開発用・配布用のビルドとCLIの結合テストを順に実行する。失敗すると非0で終了する。

個別のビルド・テスト、REPLとテストの監視、デバッグの補助は[開発環境の仕様](docs/specs/development-environment.md#5-開発コマンドとrepl)を参照する。従来の環境ではmacOSでローカル検証済みであり、3OSのCI設定も用意した。更新後のGitHub Actions上のmacOS・Windows・Linuxでの実行結果は、更新の手順書に対応するPRで確認する。

### 現在のCLI

現在の機能はバージョン表示と最小限の引数処理である。Markdown変換などの本を作る機能は未実装であり、インストール・npm公開・リリース方法はまだ定めていない。

`npm run verify`でビルドした後は、次のように実行できる。個別にビルドする場合は`npm run build:dev`または`npm run build`を使う。

```sh
node target/dev/cli.js --version
node dist/cli.js --version
```

現在の表示は`0.0.0`と末尾の改行である。ビルド済みCLIの実行にはNode.jsを使い、JDKは不要となる。配布用出力を別の場所へ移す場合は`dist/`の内容を一式コピーする。

引数なしでは`Usage: clono --version`を標準出力へ表示して正常終了する。それ以外の引数は、受け取った引数全体をJSON配列で含むエラーと使い方を標準エラーへ表示し、終了コード1で終了する。`--help`と`-v`も現在は受け付けない。具体的な出力は[CLIの仕様](docs/specs/cli.md)を参照する。

バージョン番号の管理元は`package.json`の`version`である。番号を変更したら再ビルドする。監視中の場合は監視プロセスを再起動する。

実装環境の調査・検証を再現する場合は、[検証資料](research/implementation-environment/README.md)を参照する。調査環境の依存とコマンドは、ルートの製品開発環境とは分けて管理している。

## ライセンス

Copyright 2026 HASEBA Junya (7pairs)

clonoはApache License 2.0（`Apache-2.0`）で配布する。正式な条件は[LICENSE](LICENSE)、適用範囲と例外・表示ルールは[ライセンス方針](docs/specs/licensing.md)を参照する。

第三者由来の内容は、それぞれの権利とライセンス条件に従う。利用者の原稿や生成した本には、clonoを使ったという理由でApache-2.0を適用せず、本の配布・頒布形式も制限しない。
