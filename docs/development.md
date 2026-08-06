# clono 開発ガイド

この文書は、clonoの開発環境を準備し、ビルド、テストおよびnpmパッケージの検証を行うための手順を示す。

プロジェクトの目的と判断基準は[プロジェクト憲章](project-charter.md)を、配布形態とビルド方式を選択した理由は[ADR 0001](decisions/0001-distribution-and-build.md)を参照すること。

## 必要な環境

clonoの標準開発環境は次のとおりである。

- Node.js 24.19.0
- npm
- Java 21以上

Node.jsの標準バージョンは、リポジトリ直下の`.node-version`でも指定している。nodenvを使用する場合は、このファイルを参照して対象バージョンをインストールする。

Javaはshadow-cljsによるコンパイルとテストに必要である。npmで配布するclonoの利用者には要求しない。CIではTemurin 21を使用する。

## 初期セットアップ

リポジトリを取得した後、npmの依存関係をインストールする。

```console
npm ci
```

`npm ci`は`package-lock.json`に記録されたバージョンを使用する。依存関係を意図的に更新する場合を除き、開発環境の準備に`npm install`は使用しない。

## 開発コマンド

### テスト

テストを一度実行する。

```console
npm test
```

ファイルの変更を監視し、変更のたびにテストを実行する。

```console
npm run test:watch
```

テストには`cljs.test`とshadow-cljsの`:node-test`ターゲットを使用する。テストコードは`src/test`に配置する。

### リリースビルド

Node.jsから実行できる単一のJavaScriptエントリーポイントを生成する。

```console
npm run build
```

生成物は`dist/clono.js`に出力される。

```console
node dist/clono.js --help
node dist/clono.js --version
```

`dist`は生成物のためGitの管理対象に含めず、直接編集しない。

### npmパッケージ

配布用tarballを作成する。

```console
mkdir -p target/package
npm pack --pack-destination target/package
```

`npm pack`は`prepack`スクリプトを通じてリリースビルドを実行する。パッケージには`README.md`、`LICENSE`、`package.json`および`dist`配下の配布物だけが含まれる。tarballはGitの管理対象外である`target/package`へ出力する。

GitHub Actionsでは、作成したtarballを一時ディレクトリへインストールし、インストールされた`clono`コマンドの`--help`、`--version`および異常終了時の終了コードを検証する。通常のCIではtarballをartifactとして保存しない。

ローカルでパッケージの内容だけを確認する場合は、次のコマンドを使用できる。

```console
npm pack --dry-run
```

## ソースコードの構成

主要なファイルとディレクトリの役割は次のとおりである。

```text
src/main/clono/cli.cljs       CLI引数から実行結果を決定する純粋な処理
src/main/clono/main.cljs      Node.jsの入出力とCLI処理を接続するエントリーポイント
src/test/clono/cli_test.cljs  CLIの振る舞いを検証するテスト
docs/decisions/               ADR
dist/                         リリースビルドの生成物
target/                       テストやパッケージ検証の一時生成物
```

CLIの判断処理は、Node.jsの`process`や`console`を直接操作しない純粋な処理として保つ。Node.js固有の入出力は`clono.main`へ閉じ込め、主要な振る舞いをプロセスの起動なしでテストできるようにする。

## CI

GitHub Actionsは、`main`および`develop`へのpushと、それらをマージ先とするPull Requestで実行する。

CIでは、Node.js 22と24のそれぞれについて次の内容を検証する。

1. npm依存関係の再現可能なインストール
2. ClojureScriptのテスト
3. リリースビルドとnpmパッケージの作成
4. 作成したパッケージの独立した環境へのインストール
5. インストールしたCLIの基本動作

CIの定義は`.github/workflows/ci.yml`を正本とする。

## 変更時の確認

Pull Requestを作成する前に、少なくとも次のコマンドが成功することを確認する。

```console
npm test
npm run build
npm pack --dry-run
```

CLIの表示、終了コードまたは引数の解釈を変更するときは、対応するテストも更新する。仕様、ADRまたは開発手順を変更するときは、実装と同じ変更の中で関連文書を更新する。

## 言語

言語方針の正本は、[プロジェクト憲章の「10. 言語方針」](project-charter.md#10-言語方針)とする。

- ソースコード、識別子、コードコメント、ログおよびCLIメッセージは原則として英語で記述する。
- `README.md`は英語で記述する。
- 仕様、設計文書および開発文書は日本語で記述する。
- Issue、Pull Requestおよびそこでの議論は日本語で記述する。
