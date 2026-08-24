# clono

[![CI](https://github.com/7pairs/clono/actions/workflows/ci.yml/badge.svg)](https://github.com/7pairs/clono/actions/workflows/ci.yml)

`clono`（クロノ）は、Markdownに独自の意味を安全に付加し、構造化された変換パイプラインを通して目的の形式へ変換するためのMarkdown変換基盤です。

書籍制作に必要な独自記法を、正規表現による文字列置換ではなくASTなどの構造化表現を介して処理します。局所的な構文変換に加え、索引や相互参照のように文書全体の情報を収集・解決する処理を扱い、将来は利用者が変換処理を追加または差し替えられる仕組みの提供を目指します。

Vivliostyleを主要な出力先として想定し、Vivliostyleが持つ機能を生かしながら、その前段で文書の構文と意味を扱うことに集中します。

## 開発状況

現在は開発初期段階です。Node.js向けCLIで単一のMarkdownファイルを変換できます。独自記法は文字揃え記法、コラム記法、強制改ページ記法を実装しており、複数ファイルや書籍プロジェクトの一括変換にはまだ対応していません。

npmパッケージとしての公開はまだ行っていません。現時点のCLIは、開発環境でビルドして利用します。

## 必要な環境

開発には次の環境が必要です。

| ソフトウェア | バージョン | 用途 |
| --- | --- | --- |
| Node.js | 24.19.0 | 開発時の基準環境 |
| npm | Node.jsに同梱されるバージョン | 依存関係と開発用コマンドの管理 |
| Temurin JDK | 21.0.12 | ClojureScriptのビルド |

生成されたCLIの実行時にJDKは必要ありません。将来の利用者向けには、Node.js 22.13.0以降の22系および24系をサポートする方針です。

## 開発環境のセットアップ

リポジトリを取得します。

```shell
git clone https://github.com/7pairs/clono.git
cd clono
```

このリポジトリには、Node.jsのバージョンを指定する`.node-version`と、JDKのバージョンを指定する`.sdkmanrc`が含まれています。nodenvとSDKMANを使用する場合は、次のコマンドで開発環境を準備できます。

```shell
nodenv install
sdk env install
npm ci
```

これらのバージョン管理ツールは必須ではありません。使用しない場合は、必要なバージョンのNode.jsとJDKを別の方法で用意してから`npm ci`を実行してください。

## 開発用コマンド

| コマンド | 内容 |
| --- | --- |
| `npm run build` | CLIのdevelopmentビルドを一度実行する |
| `npm run build:release` | CLIのreleaseビルドを実行する |
| `npm run watch` | CLIを監視し、変更時に再ビルドする |
| `npm test` | Node.js上でテストを一度実行する |
| `npm run test:watch` | テストを監視し、変更時に再実行する |

CLIをビルドして起動するには、次のコマンドを実行します。

```shell
npm run build
node dist/clono.js --help
```

## 単一ファイルの変換

入力Markdownと出力先を指定して、単一のファイルを変換します。

```shell
node dist/clono.js manuscript.md --output build/manuscript.md
```

出力先の親ディレクトリは、コマンドを実行する前に作成してください。既存の出力ファイルは上書きします。変換に成功した場合は何も表示せず、診断またはファイル操作エラーが発生した場合は標準エラーへ問題を表示して終了コード`1`を返します。

利用可能な記法と詳しいCLIの契約は、[clono著者向け記法](docs/specifications/authoring-syntax.md)と[単一ファイル変換CLI仕様](docs/specifications/single-file-cli.md)を参照してください。

文字揃え記法、コラム記法、強制改ページ記法が生成する構造に必要な基盤CSSは、`styles/clono.css`にあります。このCLIは基盤CSSを出力先へコピーしないため、現時点では利用者が書籍プロジェクト側で組み込む必要があります。正式な組み込み方法は、書籍プロジェクトを扱うCLIとともに今後決定します。

## ドキュメント

- [プロジェクト憲章](docs/project-charter.md): プロジェクトの目的、設計原則、開発方針
- [開発ワークフロー](docs/development-workflow.md): Sora Flowによる通常開発とリリースのブランチ戦略
- [設計判断の記録](docs/decisions/): 採用した技術や方針と、その判断理由
- [著者向け記法](docs/specifications/authoring-syntax.md): 利用可能な記法と開発状態
- [単一ファイル変換CLI仕様](docs/specifications/single-file-cli.md): CLIの入出力、診断、終了コード

## ライセンス

`clono`は[Apache License 2.0](LICENSE)で提供します。
