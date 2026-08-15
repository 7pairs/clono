# 0002: Node.js向けClojureScript開発基盤を採用する

- 状態: 採用
- 決定日: 2026-08-14

## 背景

`clono`は、書籍プロジェクト内のMarkdownを変換するNode.js向けCLIとして提供する。実装言語には、重大な技術的障害が確認されない限りClojureScriptを使用することを、[プロジェクト憲章](../project-charter.md)で定めている。

開発基盤には、Node.js向け成果物の生成、JavaScriptライブラリとの相互運用、npmによる配布、Node.js上でのテストを一貫して扱えることが求められる。また、VivliostyleもNode.jsを利用するため、`clono`の利用者にNode.js以外の実行環境を追加で要求しないことが望ましい。

`clono`では、Generic Directivesを含むMarkdownをASTへ変換するため、ESM専用のJavaScriptライブラリを利用する可能性がある。このため、ClojureScriptをNode.js向けにコンパイルできるだけでなく、現在のJavaScriptエコシステムと実用上十分に相互運用できることを、採用前に確認する必要があった。

## 決定

### ビルド方式

ClojureScriptのビルドには[shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html)を採用し、npmの`devDependencies`としてプロジェクト単位で導入する。LeiningenまたはClojure CLIには組み込まず、npm scriptsからローカルにインストールされたshadow-cljsを実行する。

パッケージ管理と開発用コマンドの入口にはnpmを使用し、`package-lock.json`をGitで管理する。CIでは`npm ci`を使用する。

### 実行環境

開発時の基準環境にはNode.js 24.19.0を使用し、`.node-version`で固定する。

利用者に対しては、Node.js 22.13.0以降の22系および24系をサポートする。`package.json`の`engines.node`には、次の範囲を指定する。

```json
"^22.13.0 || ^24.0.0"
```

Node.js 22.13.0を下限とするのは、shadow-cljsのNode.js向け成果物がCommonJSの`require()`からESM専用パッケージを読み込む構成になり、Node.js 22.13.0以降では[この機能](https://nodejs.org/download/release/latest-jod/docs/api/modules.html#loading-ecmascript-modules-using-require)が標準で警告を出さずに利用できるためである。Node.jsのメジャーバージョンごとの対応状況は、[Node.jsのリリース情報](https://nodejs.org/en/about/previous-releases)に基づいて見直す。

開発とCIにはTemurin JDK 21を使用する。ローカル開発環境では、`.sdkmanrc`にTemurin 21.0.12を指定する。JDKはshadow-cljsによるビルドにのみ必要とし、配布した`clono`の実行時には要求しない。

### 成果物

CLIはshadow-cljsの`:node-script`ターゲットでビルドし、`dist/clono.js`へ出力する。`package.json`の[`bin`](https://docs.npmjs.com/cli/configuring-npm/package-json/)に次の対応を定義し、`clono`コマンドとして提供する。

```json
{
  "bin": {
    "clono": "dist/clono.js"
  }
}
```

実行時に必要なJavaScriptライブラリは、生成したJavaScriptへ完全には内包せず、npmの`dependencies`として配布する。shadow-cljsやその他のビルド専用パッケージは`devDependencies`として管理する。

`dist/`はGitで管理せず、npmパッケージの作成前にreleaseビルドで生成する。配布対象は`package.json`の`files`で必要なファイルに限定する。

初期段階ではCLIのみを公開し、`package.json`の`main`や`exports`によるJavaScript向けライブラリAPIは提供しない。プラグインシステムやJavaScript向け公開APIの要件が具体化した時点で、別途設計する。

### 開発用コマンドとテスト

次のnpm scriptsを開発用コマンドとして提供する。

- `npm run build`: CLIのdevelopmentビルドを一度実行する
- `npm run build:release`: CLIのreleaseビルドを実行する
- `npm run watch`: CLIを監視し、変更時に再ビルドする
- `npm test`: Node.js上でテストを一度実行する
- `npm run test:watch`: テストを監視し、変更時に再実行する
- `npm run prepack`: npmパッケージの作成前にreleaseビルドを実行する

テストにはClojureScript標準の[`cljs.test`](https://cljs.github.io/api/cljs.test/)と、shadow-cljsの`:node-test`ターゲットを使用する。テスト名前空間は`-test`で終える。

通常のテストでは、テスト用JavaScriptをコンパイルした後にNode.jsで実行し、テスト結果をプロセスの終了コードとして呼び出し元へ返す。watch用のテストでは`:autorun true`を使用する。`:autorun`で起動されたNode.jsプロセスの終了コードはshadow-cljsから返されないため、通常実行とwatch実行には別のビルド定義を使用する。

## 理由

- Node.js向けCLIを生成する`:node-script`ターゲットが用意されている
- npmパッケージをClojureScriptから直接利用できる
- developmentビルド、releaseビルド、watch、REPL、Node.js上のテストを一つのビルドツールで扱える
- npmを入口にすることで、JavaScript依存関係と開発用コマンドをNode.js利用者に馴染みのある形で管理できる
- ビルド時にのみJDKを使用し、利用者にはNode.js以外の実行環境を要求せずに済む
- プロジェクトオーナーにshadow-cljsの利用経験があり、導入と運用の学習コストを抑えられる
- CLIだけを公開することで、未確定の内部APIを後方互換性の対象にせずに済む

## 技術検証

採用判断に先立ち、リポジトリ外の一時プロジェクトで次の構成を検証した。

- Node.js 24.19.0
- OpenJDK 25.0.3
- shadow-cljs 3.4.12
- unified 11.0.5
- remark-parse 11.0.0
- remark-directive 4.0.0

検証では、次の結果を確認した。

- ESM専用の`unified`、`remark-parse`、`remark-directive`をClojureScriptから読み込めた
- Generic Directivesを含むMarkdownをmdastへ変換できた
- `:node-script`のdevelopmentビルドとreleaseビルドが成功した
- npmのproduction依存関係だけを導入した環境で、release成果物を実行できた
- release成果物の実行にJDKを必要としなかった
- JavaScript依存関係はrelease成果物へ完全には内包されず、実行時に`node_modules`から読み込まれた

OpenJDK 25.0.3でのビルド時には、Closure Compilerが使用するJava内部APIについて将来の廃止を知らせる警告が出た。ビルドには成功したが、shadow-cljsがJava 21以上のLTSを推奨していることも踏まえ、開発とCIにはJDK 21を使用する。

## 比較した候補

### Clojure CLIと公式ClojureScriptコンパイラ

Clojure CLIから`cljs.main`を起動する構成は、公式ツールを中心とした単純な構成にできる。一方、`clono`の中核となるnpmパッケージとの相互運用やNode.js向け成果物の構成を、shadow-cljsほど一貫して扱えない。追加のJavaScript用bundlerを導入する可能性もあるため、採用しない。

### Leiningen

プロジェクトオーナーに利用経験があるが、Node.js向けClojureScriptプロジェクトへ`project.clj`とJVM側の依存関係管理を追加する明確な利点がない。npmを中心とするJavaScript側の依存関係管理と役割が重複するため、採用しない。

### shadow-cljsをLeiningenまたはClojure CLIへ組み込む構成

必要になった場合にClojureまたはJavaの依存関係を追加しやすい。一方、現時点ではnpmからshadow-cljsを直接利用する構成で要件を満たしており、複数の依存関係管理方式を併用する必要がないため、採用しない。

### ESM形式の成果物

ESM専用ライブラリとの形式を揃えられる可能性がある。一方、初期の公開インターフェースは単一のCLIであり、`:node-script`はshebangと起動用の`main`関数を備えた成果物を直接生成できる。検証でも必要な相互運用性を確認できたため、初期段階では採用しない。

## 影響

- 開発者とCIには、Node.js、npm、JDK 21が必要になる
- npmから導入する利用者にはnpmが必要になるが、インストール済みのCLIを実行する際に必要な実行環境はNode.jsだけである
- JavaScript依存関係はnpmパッケージのインストール時に取得される
- 開発環境とCIで同じnpm scriptsを使用する
- CIではNode.js 22.13系と24系の両方でテストする
- CIではテストに加えてreleaseビルドとCLIの起動を確認する
- Node.jsのサポート範囲を変更する場合は、`engines.node`、CI、ドキュメントを同時に更新する
- JavaScript向けライブラリAPIやプラグインAPIは、この決定だけでは提供されない

## 未確認事項

技術検証はmacOSとNode.js 24.19.0で実施した。次の事項は、GitHub Actionsの導入時に確認する。

- Linux上でdevelopmentビルド、releaseビルド、テスト、CLI実行が成功すること
- Node.js 22.13系でESM専用のJavaScript依存関係を読み込み、CLIを実行できること
- JDK 21でdevelopmentビルド、releaseビルド、テストが成功すること

## 見直す条件

次のいずれかが生じた場合は、この決定を見直す。

- shadow-cljsの保守状況または互換性に重大な問題が生じた場合
- 利用するESMパッケージを`:node-script`の成果物から読み込めなくなった場合
- JavaScript依存関係にトップレベル`await`が導入され、CommonJSの`require()`では読み込めなくなった場合
- プラグインシステムまたはJavaScript向け公開APIに、ESM形式など別の成果物が必要になった場合
- npm以外の配布方法や、JavaScript依存関係を内包した単一成果物が必要になった場合
- Node.js、JDK、Vivliostyleのサポート方針が変わり、現在のバージョン方針を維持できなくなった場合
