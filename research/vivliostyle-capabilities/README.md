# VivliostyleおよびVFMの機能調査

このnpmプロジェクトには、clonoの仕様検討に使用するVivliostyleおよびVFMの再現可能な最小サンプルを置く。
調査の目的、対象および判定方法は[`docs/research/vivliostyle-capabilities.md`](../../docs/research/vivliostyle-capabilities.md)を参照すること。

## clono本体との分離

このディレクトリは、clono本体から独立した非公開の調査用npmプロジェクトである。
ここで使用するパッケージは、clono本体の実行時依存関係およびnpm配布物には含めない。

`@vivliostyle/cli`はAGPL-3.0、`@vivliostyle/vfm`はApache-2.0で提供されている。
Vivliostyle CLIは調査用の外部ツールとしてのみ使用し、clonoへ組み込まない。

## 依存関係の安全性

調査開始時点の`npm audit`では、固定した最新版の推移的依存関係に1件のlow、10件のmoderateおよび3件のhighの脆弱性が報告されている。
本調査では、リポジトリ内のサンプルなど信頼できるローカル入力だけを処理し、調査用ツールを外部公開サービスとして実行しない。

調査対象のバージョンを暗黙に変えないため、`npm audit fix`は実行しない。
依存バージョンを更新して追加検証するときに、監査結果も改めて確認する。

## 必要な環境

- macOS
- Node.js 24.19.0
- npm

Node.jsのバージョンは、このディレクトリの`.node-version`でも固定している。

## セットアップ

このディレクトリで、固定された依存関係をインストールする。

```console
npm ci
```

使用するツールのバージョンを確認する。

```console
npm run check
```

個別のCLIを実行する場合は、次のnpmスクリプトへ引数を渡す。

```console
npm run vivliostyle -- --help
npm run vfm -- --help
```

## ディレクトリ構成

```text
cases/       機能ごとの入力、設定および再現手順
styles/      各サンプルで共有する最小限のCSS
output/      HTML、PDFなどの生成物（Git管理対象外）
```

各サンプルは原則として一つの疑問だけを扱い、再現コマンドと目視確認項目をそのサンプル内のREADMEへ記録する。
生成物は`output/`へ出力し、成功と判断するために手修正しない。
各Vivliostyle設定では`output/**`を資産コピーの対象外とし、過去の生成物やworkspaceが次回のworkspaceへ再帰的にコピーされないようにする。
