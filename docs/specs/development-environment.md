<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# 開発環境の仕様

- 状態：合意済み。手順2の開発基盤は実装・ローカル検証・独立レビュー・二人の確認完了。製品CLIとCIは未実装。
- 関連する手順書：[0003 最小限の開発基盤とバージョン表示を実装する](../plans/0003-set-up-development-foundation.md)
- 関連する仕様：[実装環境](implementation-environment.md)、[CLI](cli.md)

## 1. 目的と範囲

ClojureScriptで製品コードを書き、ローカルでビルド・テスト・REPLを利用できる環境を整える。同じビルドとテストをmacOS・Windows・LinuxのCIで実行する。

今回の製品機能は、バージョン表示と最小限の引数処理とする。インストール、公開、リリースの自動化は対象に含めない。

## 2. 使用する環境

| 項目 | 使用する構成 |
|---|---|
| Node.js | 24.22.0 |
| npm | 11.19.1を基準とし、検証時の版を記録する |
| ローカルのJDK | SDKMANで管理するTemurin 21.0.12 |
| CIのJDK | 実行時点で取得できるTemurin 21の最新更新版 |
| ビルド | shadow-cljs 3.5.7 |
| ClojureScript | shadow-cljsが使用する版。個別に上書きしない |
| 単体テスト | `cljs.test` |
| CLIの結合テスト | Node.js標準の`node:test`と`node:assert/strict` |

npmの依存は`package-lock.json`を管理し、CIでは`npm ci`で導入する。JDKの更新版やランナーのイメージは変わり得るため、CIログに実際の環境を残す。

ローカルのJDKは、SDKMANで更新版を利用できる時点で更新を検討する。手動で別経路のJDKを導入することは前提にしない。

## 3. ファイル配置と責務

| 配置 | 役割 |
|---|---|
| `src/main/clono/cli.cljs` | 引数から出力内容と終了コードを決める関数 |
| `src/main/clono/main.cljs` | Node.jsの引数を取得し、出力と終了コードを反映する入口 |
| `src/test/clono/cli_test.cljs` | 引数判定などの単体テスト |
| `test/cli/cli.test.mjs` | ビルド済みCLIを起動する結合テスト |
| `scripts/` | バージョン情報の生成など、開発用の補助処理 |
| `target/generated/clono/version.cljs` | 自動生成するバージョン情報 |
| `target/dev/` | 一度実行する開発用CLIのビルド |
| `target/repl/` | REPL用のビルド |
| `target/test/` | 単体テストのビルド |
| `dist/` | 配布用CLIのビルド |
| `shadow-cljs.edn` | ソース配置と各ビルドの設定 |
| `package.json` | バージョン番号、npm依存、開発コマンド |
| `.github/workflows/ci.yml` | CIの設定 |

製品コードは調査用ディレクトリへ依存しない。Markdown処理系やVivliostyleは、今回の製品コードの依存としては追加しない。

`package.json`には`"type": "module"`と`"private": true`を設定する。後者は今回公開しない方針を設定にも反映するために使う。[npmのprivateの仕様](https://docs.npmjs.com/cli/v11/configuring-npm/package-json/#private)

## 4. ビルドとバージョン情報

### 製品のビルド

製品はshadow-cljsのESMターゲットを使い、開発用と配布用を別のディレクトリへ出力する。

CLIの入口は、どちらも`cli.js`とする。

```text
node target/dev/cli.js --version
node dist/cli.js --version
```

開発用CLIにはREPL接続用クライアントを含めず、一度実行して終了できる構成にする。配布用ビルドには、開発用の接続処理を含めない。

配布用ビルドではsource mapを生成する。ただし、Node.js標準機能だけで元のCLJS位置を表示できるとは保証しない。既存の[検証結果](../../research/implementation-environment/results/verification.md)にある補助方法を開発時の案内へ引き継ぐ。

### バージョン番号の生成

手作業で更新するバージョン番号は、ルートの`package.json`の`version`だけとする。初期値は`0.0.0`とする。

ビルド前に、小さなNode.jsスクリプトで`version`を読み取り、`target/generated/clono/version.cljs`を生成する。製品はこの定数を取り込み、実行時には`package.json`を読まない。

生成処理は次のように扱う。

- バージョン情報を利用するビルド・監視コマンドの開始前に実行する。
- 番号が存在しない、文字列でない、空である場合は失敗させる。
- 内容が同じ場合はファイルを書き換えない。
- 生成ファイルはGit管理しない。
- 番号の変更後は、監視プロセスを再起動する。

今回は`package.json`の変更を常時監視する仕組みを追加しない。

生成処理をNode.jsに置くのは、ビルド前のJSON読み取りとファイル生成に限定するためであり、製品本体の責務をJavaScriptへ移すものではない。

## 5. 開発コマンドとREPL

| コマンド | 動作 |
|---|---|
| `npm run build:dev` | バージョン情報を生成し、開発用CLIをビルドする |
| `npm run build` | バージョン情報を生成し、配布用CLIをビルドする |
| `npm run dev` | バージョン情報を生成し、REPL用ビルドを監視する |
| `npm run repl:runtime` | REPL用ビルドをNode.jsで起動する |
| `npm run repl` | 起動済みのREPL用ビルドへ端末から接続する |
| `npm run test:unit` | 単体テストをビルドし、Node.jsで一回実行する |
| `npm run test:watch` | CLJSの単体テストを監視・自動実行する |
| `npm run test:tools` | バージョン情報の生成処理をテストする |
| `npm run test:cli` | 開発用・配布用CLIをビルドし、結合テストを実行する |
| `npm test` | 生成処理のテスト、単体テスト、CLIの結合テストを順に実行する |
| `npm run verify` | ローカルとCIで共通の検証として`npm test`を実行する |

一回実行するコマンドは、途中の処理が失敗したら非0で終了する。監視コマンドは、修正を続けられるよう、テスト失敗を表示して監視を継続する。

REPLの利用手順は次とする。

1. `npm run dev`で監視を開始する。
2. 別の端末で`npm run repl:runtime`を実行する。
3. Calva、またはさらに別の端末の`npm run repl`から接続する。

REPL用ビルドを読み込んでも、CLIの引数処理や出力を自動実行しない。関数を読み込み、必要な引数を与えて評価する。

shadow-cljsのビルド固有REPLは、監視プロセスとJavaScriptの実行環境の両方を必要とする。この手順は、その二つを明示している。[shadow-cljsのREPL接続](https://shadow-cljs.github.io/docs/UsersGuide.html#_build_specific_repl)

### 手順2時点の実装状況

現在の`clono.cli/probe`、`clono.main/main`とCLJSのテストは、ビルド・REPL・テスト実行を確認するための仮コードである。CLIの結合テストも、開発用・配布用の入口が出力なしで起動・終了することだけを確認する。`npm run verify`の成功を、製品CLIの仕様を満たしたこととは扱わない。手順3で仮コードと接続確認用テストを製品の実装・テストへ置き換える。

バージョン情報の生成処理と、そのテストは後続でも使用する。`.node-version`でNode.jsの版、`.sdkmanrc`でSDKMANのJDKを指定する。SDKMANでの切り替えには、リポジトリルートで`sdk env`を実行する。既定のJDKは変更しない。

## 6. テストの分担

### 単体テスト

`cljs.test`で、引数から結果を決める関数を確認する。関数は標準出力への書き込みやプロセスの終了を行わず、次の情報を返す。

- 標準出力へ書く文字列。
- 標準エラーへ書く文字列。
- 終了コード。

テスト用ビルドにはshadow-cljsの`:node-test`を使い、出力ファイルの拡張子は`.cjs`とする。これはテスト実行用の構成であり、製品のCommonJS対応を意味しない。

一回実行とCIでは、コンパイル後に生成されたテストをNode.jsで明示的に実行する。`:autorun`の結果だけで成功と判定しない。[shadow-cljsのテスト実行仕様](https://shadow-cljs.github.io/docs/UsersGuide.html#target-node-test)

### CLIの結合テスト

`node:test`から、現在テストを実行しているNode.jsの`process.execPath`を使ってCLIを子プロセスとして起動する。シェルを介さず、実行ファイルと引数を分けて渡す。

確認するものは、標準出力・標準エラー・終了コード・正常に終了したかの4点とする。起動失敗やタイムアウトを、期待したCLIエラーとして扱わない。

同じテスト項目を、開発用と配布用のCLIに適用する。製品の関数をテスト用に公開することは、このテストの前提にしない。

## 7. CI

GitHub Actionsで次のマトリクスを使う。

| OS | ランナー | アーキテクチャ |
|---|---|---|
| macOS | `macos-15` | arm64 |
| Windows | `windows-2025` | x64 |
| Linux | `ubuntu-24.04` | x64 |

これらは仕様検討時点の標準ランナーとして掲載されている。全アーキテクチャや、各OSの全バージョンへの対応を保証するものではない。[GitHubのランナー一覧](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)

実行条件は次とする。

- `develop`・`main`向けのPR。
- `develop`・`main`へのpush。
- Draft PRも実行対象とする。
- パスによる実行除外は、今回は設けない。

各ジョブでは、チェックアウト、Node.js・JDKの準備、`npm ci`、`npm run verify`を行う。CIではnpmも11.19.1にそろえる。

JDKは`distribution: temurin`、`java-version: '21'`、`check-latest: true`で選び、実際の版をログに残す。ローカルとCIのJDK更新版が一致することは要求しない。[setup-javaの更新版取得](https://github.com/actions/setup-java/blob/main/docs/advanced-usage.md)

マトリクスの`fail-fast`は無効にし、いずれかのOSが失敗しても他OSの結果を収集する。失敗を許容するジョブは設けない。

ソース取得などのActionsは、使用する公式リリースを完全なコミットSHAで固定する。トークンの権限は`contents: read`とし、公開処理や書き込み権限は追加しない。

## 8. 生成物と設定の管理

Git管理から除外する対象は次とする。

- `node_modules/`
- `.shadow-cljs/`
- `target/`
- `dist/`
- `.clj-kondo/.cache/`
- `.lsp/.cache/`

共有する設定ファイルは管理対象に残す。`.clj-kondo/`や`.lsp/`全体は除外しない。

追加するソース・設定・文書の表示は、既存の[ライセンス方針](licensing.md)に従う。JSONなどコメントを書けない管理対象ファイルには、対応する表示を別の文書で管理する。

## 9. 開発基盤の検証項目

| ID | 確認内容と期待結果 |
|---|---|
| D01 | 新しいチェックアウトで依存を導入し、`npm run verify`が成功する |
| D02 | 単体テストを意図的に失敗させると、一回実行のテストコマンドと`verify`が非0で終了する |
| D03 | CLIの結合テストを意図的に失敗させた場合も、同様に非0で終了する |
| D04 | コンパイル失敗後に古い成果物のテストだけを実行して成功扱いにしない |
| D05 | 単体テストの監視中に変更が反映され、失敗後に修正すると成功へ戻る |
| D06 | REPLで製品の関数を評価でき、ソース変更が反映される。接続だけではCLI処理を実行しない |
| D07 | 生成物とキャッシュは除外され、共有設定は除外されない |
| D08 | 3OSで同じ検証一式が成功し、環境情報をログから確認できる |
| D09 | 同じバージョン番号で生成処理を再実行しても、生成ファイルの内容と最終更新時刻が変わらない |

D02〜D05の意図的な変更は検証後に戻す。接続確認のための仮コードも、不要になったものは残さない。
