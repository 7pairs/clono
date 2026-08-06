# ADR 0001: npmで配布するNode.js CLIをshadow-cljsでビルドする

- 状態: 採用
- 決定日: 2026-08-03

## 背景

clonoは、独自記法を含むMarkdownを入力し、Vivliostyle Flavored Markdown（VFM）に準拠したMarkdownを出力する。生成したVFM準拠Markdownは、後続のVivliostyleによる組版処理の入力となる。

clonoには、複数ファイルを対象とした参照や索引の意味解決が必要である。また、プロジェクト憲章では、変換結果を人間が検査できる形で出力することと、利用者へ不必要な実装知識を要求しないことを重視している。

最初の利用者はVivliostyleを使用しているため、Node.jsを利用環境へ導入することは許容できる。一方、clonoの実装にはClojureScriptを使用するが、そのコンパイル環境を利用者へ要求することは避けたい。

## 決定

### 配布形態

clonoを独立したNode.js CLIとして実装し、npmパッケージとして配布する。

npmパッケージは`clono`コマンドを公開する。書籍プロジェクトではclonoをローカルの開発依存関係としてインストールし、npmスクリプトなどからバージョンを固定して実行する。

clonoの公開インターフェースは、初期段階ではCLIだけとする。JavaScriptライブラリとしてのAPIは提供しない。

### Vivliostyleとの責務分担

clonoは、独自記法を含むMarkdownからVFM準拠Markdownを生成した時点で処理を終了する。Vivliostyle CLIの起動と組版処理は、書籍プロジェクトのビルド工程における後続の独立した処理とする。

clonoは、初期段階ではVivliostyle CLIのラッパーとして動作せず、Vivliostyleの`documentProcessor`にも直接統合しない。

### ビルド

ClojureScriptのビルドにはshadow-cljsを使用し、CLIのビルドターゲットには`:node-script`を使用する。本番用ビルドでは、Node.jsから実行できる単一のJavaScriptエントリーポイントを生成する。

JavaScriptの依存関係とshadow-cljs自体は、npmの`package.json`と`package-lock.json`で管理する。ClojureScriptの依存関係は、追加の必要性が生じるまで`shadow-cljs.edn`で管理する。

shadow-cljsはプロジェクトの開発依存関係としてインストールし、グローバルインストールを要求しない。ビルドやテストなどの開発コマンドは、npmスクリプトを共通の入口とする。

### 実行環境と開発環境

clonoの利用者にはNode.js 22以上を要求する。開発とCIではNode.js 24 LTSを標準環境とする。

clonoの開発者には、shadow-cljsの実行に必要なJava 21以上を要求する。npmで配布されたclonoの利用者にはJavaを要求しない。

### テスト

ClojureScriptのテストには`cljs.test`を使用し、shadow-cljsの`:node-test`ターゲットでNode.js上から実行する。テストはnpmスクリプトから実行でき、CIでも同じコマンドを使用する。

## 理由

- npmパッケージとして配布することで、Vivliostyleと同じNode.jsのツールチェーンへ自然に組み込める。
- 書籍プロジェクトへローカルインストールすることで、clonoのバージョンを固定し、再現可能なビルドを構成できる。
- clonoとVivliostyleを別プロセスにすることで、それぞれの責務と障害箇所を分離できる。
- VFM準拠Markdownを中間生成物として残すことで、変換結果を目視および差分で検査できる。
- `:node-script`は、ClojureScriptからNode.jsで直接実行できる単一のJavaScriptエントリーポイントを生成でき、clonoのCLIという配布形態に適している。
- shadow-cljsをローカルの開発依存関係として固定することで、開発者ごとのバージョン差を抑えられる。
- 利用者にはコンパイル済みのJavaScriptを提供するため、ClojureScriptやJavaの知識と環境を要求せずに済む。
- npmを採用することで、Vivliostyle利用者へ追加のパッケージマネージャーを要求せずに済む。

## 結果とトレードオフ

### 利点

- clono単体で変換処理を実行し、テストできる。
- Vivliostyleの内部APIや設定形式への強い依存を避けられる。
- 生成したVFM準拠Markdownを調査し、問題の所在を切り分けやすい。
- npmのバージョン管理とロックファイルを利用した再現可能な導入ができる。
- ClojureScriptのREPL、監視ビルドおよび最適化ビルドを利用できる。

### コストと制約

- 書籍のビルド工程は、clonoとVivliostyleの少なくとも2段階になる。
- clonoの開発には、Node.jsに加えてJavaの導入が必要になる。
- npmパッケージの公開、バージョン管理および配布物の検証が必要になる。
- JavaScriptの依存関係を使用する場合、単一のJavaScriptエントリーポイントが生成されても、すべての実行時依存がそのファイルへ含まれるとは限らない。
- Vivliostyleの`documentProcessor`と直接統合した場合に比べ、Vivliostyleの設定だけで処理を完結させることはできない。

## 採用しなかった選択肢

### Vivliostyleの`documentProcessor`へ直接統合する

初期方式としては採用しない。`documentProcessor`はMarkdownからHTMLへの変換処理を拡張できるが、clonoには複数ファイルを対象とした意味解決と、検査可能なVFM準拠Markdownの生成が必要である。直接統合すると、Vivliostyleへの結合が強くなり、責務と問題の切り分けも難しくなる。

局所的な変換だけを提供する必要が生じた場合は、将来の追加インターフェースとして再検討できる。

### clonoからVivliostyle CLIを起動する

初期方式としては採用しない。clonoの責務が前処理を超えて拡大し、Vivliostyleのオプション、終了コードおよびバージョンへの追従が必要になるためである。

書籍プロジェクト側のnpmスクリプトで両コマンドを組み合わせる。

### JavaScriptライブラリAPIを提供する

初期方式としては採用しない。最初の利用例はビルド工程からCLIを実行するものであり、ライブラリAPIの具体的な利用者と要件がないためである。

### Node.jsを必要としないネイティブ実行ファイルを配布する

初期方式としては採用しない。Vivliostyleの利用環境にはすでにNode.jsが必要であり、追加のビルド方式、OS別成果物および配布工程を導入する価値が小さいためである。

### pnpmまたはYarnを使用する

初期方式としては採用しない。npmはNode.jsとともに導入でき、Vivliostyle利用者へ追加のパッケージマネージャーを要求しないためである。

### `deps.edn`またはLeiningenで依存関係を管理する

初期方式としては採用しない。現在の依存関係はshadow-cljsの標準構成で管理でき、複数の依存管理方式を導入する必要がないためである。

## 今回決定しない事項

次の事項は、CLI仕様、機能仕様またはリリース仕様で必要になった時点に決定する。

- `build`コマンドの具体的な引数
- 設定ファイルの有無と形式
- 入力および出力ディレクトリの構造
- CommonJSまたはES Modulesの選択
- npm依存を含む完全な単一ファイル化
- JavaScriptライブラリAPIの将来的な提供
- 初回npm公開のバージョンと時期

## 関連文書

- [clono プロジェクト憲章](../project-charter.md)
- [shadow-cljs User's Guide: Node.js Scripts](https://shadow-cljs.github.io/docs/UsersGuide.html#_node_js_scripts)
- [shadow-cljs User's Guide: Testing](https://shadow-cljs.github.io/docs/UsersGuide.html#_testing)
- [npm package.json: `bin`](https://docs.npmjs.com/cli/configuring-npm/package-json/#bin)
- [Vivliostyle CLI Config Reference: `documentProcessor`](https://docs.vivliostyle.org/ja/cli/config/#documentprocessor)
- [Node.js Releases](https://nodejs.org/en/about/previous-releases)
