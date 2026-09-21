# ローカルES Moduleによるプラグイン読み込み方式に関する調査

- 状態: 調査済み
- 調査日: 2026-09-20
- 最終更新日: 2026-09-21
- 検証環境:
  - OS: macOS 26.6.2
  - Node.js: 24.19.0
  - npm: 11.17.0
  - JDK: Temurin 21.0.12
  - ClojureScriptビルド: shadow-cljs 3.4.12

## 背景

[プロジェクト憲章](../project-charter.md)では、利用者がJavaScriptなどで変換処理を追加または差し替えられるプラグインシステムを、clonoの将来の方向性として定めている。一方、配布、依存関係解決、サンドボックスなどを含む完全な基盤は先行して構築せず、実際の変換機能を通じて必要な拡張点とAPIを検証する。

[ADR 0003](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)では、組み込み変換を実例として育て、必要な部分だけを将来のプラグインAPIとして公開する方針を採用した。その後、Thunder Clawの次回作で必要となる組み込み記法が揃い、コラムの出力構造を利用者が差し替える具体的な要件が生じた。

最小のプラグイン契約を設計する前に、Node.js向けにビルドしたClojureScriptから、利用者が用意したローカルのJavaScriptモジュールを読み込み、その情報とrenderer関数を扱えるか確認する必要がある。また、読み込み、exportの検証、rendererの呼び出し、および競合を、互いに区別できる失敗として扱えるかも確認する必要がある。

## 調査目的

- 設定ファイルを基準とする相対パスから、ローカルの`.mjs`を読み込めるか確認する
- 空白や`#`を含むファイルシステム上のパスを、安全に`import()`へ渡せるか確認する
- 設定ファイルとプラグインモジュールのtop-level awaitを評価できるか確認する
- 読み込んだJavaScriptオブジェクトと関数を、ClojureScriptのデータ構造へ変換せず扱えるか確認する
- 同期的なrendererを繰り返し呼び出し、JavaScriptオブジェクトを渡して文字列を受け取れるか確認する
- rendererが投げた例外、文字列以外の戻り値、およびPromiseを区別できるか確認する
- renderer名の重複、存在しないモジュール、および不正なdefault exportを区別できるか確認する
- Node.jsのES Moduleキャッシュが、同じモジュールの再読み込みへ与える影響を確認する

この調査は、設定項目、プラグイン情報、rendererの入出力、診断コードまたは競合規則を正式に決定するものではない。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/plugin-loading/)をリポジトリ内へ保存している。このfixtureはclono本体から独立したClojureScriptおよびnpmプロジェクトであり、`package.json`と`package-lock.json`によって依存関係を固定する。

候補設定では、設定ファイルのdefault exportに`plugins`配列を置き、次のようにローカルモジュールへの相対パスを列挙した。

```javascript
export default {
  plugins: [
    "./plugins/basic plugin.mjs",
    "./plugins/top-level-await#plugin.mjs",
    "./plugins/cached-plugin.mjs",
  ],
};
```

候補プラグインのdefault exportには、相互運用の検証に必要な最小限の情報とrendererを置いた。

```javascript
export default {
  name: "research-basic-plugin",
  version: "0.0.0",
  apiVersion: 1,
  renderers: {
    column(input) {
      return `basic:${input.title}:${input.body}`;
    },
  },
};
```

これらのプロパティ名、値、必須性およびrendererの入出力は、成立性を確認するための仮定である。

### 読み込み処理

設定ファイルのパスを絶対パスへ解決し、`node:url`の`pathToFileURL()`で`file:` URLへ変換してから、標準の動的`import()`へ渡した。プラグインパスは、起動時のカレントディレクトリではなく、設定ファイルがあるディレクトリを基準に絶対パスへ解決した。

Closure Compilerは、今回の`:node-script` releaseビルド内に記述した動的import式をそのまま処理できなかった。このため、fixtureでは`Function`コンストラクターで`import(specifier)`を呼び出す小さな境界を作成した。この回避方法も正式な実装方式として決定したものではない。

Shadow CLJSの`:node-test`ターゲットは、生成コードをNode.jsのVMで評価する際に動的import用のコールバックを指定しない。その環境では実際の`import()`を実行できなかったため、通常のNode.jsプロセスでreleaseビルドした`:node-script`を実行する結合検証を用意した。これはNode.js上のclono CLIで動的importを利用できないことを意味しない。

### 検証した候補

正常系には、次のモジュールを使用した。

- 通常のdefault exportを持つモジュール
- モジュール本体でtop-level awaitを使用するモジュール
- グローバルな評価回数を記録し、importキャッシュを確認できるモジュール

rendererの失敗系には、次のモジュールを使用した。

- 呼び出し時に例外を投げる
- 文字列ではなくJavaScriptオブジェクトを返す
- 文字列ではなくPromiseを返す

読み込みと検証の失敗系には、次の設定またはモジュールを使用した。

- 存在しない`.mjs`を参照する設定
- named exportだけを持ち、default exportを持たないモジュール
- 複数のプラグインが同じ`column` rendererを公開する設定

## 検証結果

### 設定ファイルを基準とする読み込み

起動時のカレントディレクトリをfixture外へ変更しても、設定ファイルの場所を基準に各プラグインを読み込めた。設定ファイルとプラグインのパスに空白と`#`が含まれる場合も、ファイルシステム上の絶対パスを`pathToFileURL()`へ渡すことで、`#`をURLフラグメントと誤認せず読み込めた。

設定ファイルとプラグインモジュールのtop-level awaitも、Node.jsの標準ES Moduleとして評価された。したがって、設定とローカルプラグインを`.mjs`として読み込む方式は、技術的に成立する。

候補モジュールは`Promise.all()`で読み込み、返却する配列を設定に列挙した順序で構築できた。ただし、この結果はプラグインが持つ副作用やtop-level awaitの完了が設定順に逐次実行されることを保証しない。正式な契約で処理順序を必要とする場合は、モジュールの読み込み順ではなく、読み込み後の検証およびrenderer適用順として定義する必要がある。

### Node.jsのimportキャッシュ

同じ`file:` URLを同じNode.jsプロセスで繰り返しimportした場合、モジュール本体は一度だけ評価された。default exportとrenderer関数の同一性も維持された。

この挙動は、通常のCLI実行中に同じプラグインを重複評価しない用途には適している。一方、同じプロセスでプラグインファイルを変更して再読み込みする開発用のホットリロードにはそのまま利用できない。初期のCLIにホットリロードの要件はないため、今回の調査ではキャッシュを無効化しない。

### ClojureScriptとの相互運用

ES Module namespace objectからdefault exportを取得し、`name`、`version`および`apiVersion`をClojureScriptから参照できた。プラグイン全体を`js->clj`で変換せず、JavaScriptオブジェクトのまま保持できた。

同様に、`renderers.column`をJavaScript関数のまま取得し、ClojureScriptから複数回呼び出せた。日本語を含む`title`と`body`をJavaScriptオブジェクトで渡し、同期的に文字列を受け取れた。凍結した入力オブジェクトを渡してもrendererは成功し、入力値は変更されなかった。

この結果から、初期のrenderer境界でJavaScriptオブジェクトを受け渡し、文字列を返す方式は技術的に成立する。入力を不変として扱うことや、具体的なプロパティを正式に保証することは、後続の仕様で決定する必要がある。

### rendererの同期実行と戻り値

同期的に文字列を返すrendererを正常な結果として識別できた。rendererが同期的に投げた例外は呼び出し境界で捕捉でき、元のJavaScript Errorとメッセージを保持できた。

JavaScriptオブジェクトを返すrendererは、文字列以外の戻り値として識別できた。Promiseを返すrendererも、その完了を待たずに非同期の戻り値として識別できた。このため、初期契約を同期関数へ限定し、Promiseを誤って出力文字列として扱わず拒否する構成が成立する。

fixtureの検査結果に用いたステータス名、理由および型名は、異なる挙動を自動検証するための仮の値である。利用者向けの診断コードまたは文言として採用したものではない。

### 読み込み、exportおよび競合の識別

存在しないプラグインファイルをimportした場合、Promiseはrejectされ、Node.jsの`ERR_MODULE_NOT_FOUND`と対象ファイルを含むメッセージを取得できた。

default exportを持たずnamed exportだけを持つモジュールは、ES Moduleとしての読み込み自体には成功した。その後の検査でdefault exportが`undefined`であることを確認し、読み込み失敗とは異なる不正なexportとして識別できた。

複数の候補プラグインが同じ`column` rendererを公開した場合、renderer名と、競合したプラグインの指定を設定順に収集できた。最初または最後のプラグインを暗黙に優先せず、変換前に競合として扱うために必要な情報を取得できる。

これらの結果から、少なくとも次の段階を分離できる。

```text
設定の読み込み
  → プラグインモジュールの読み込み
  → default exportと基本情報の検証
  → renderer登録の収集と競合検査
  → rendererの呼び出し
  → 戻り値の検証
```

各段階の失敗を区別できるため、問題がある場合にAST変換を始めず、部分的な出力を生成しない構成を検討できる。

## 評価

Node.jsの標準ES Moduleと動的`import()`を使用し、設定ファイルを基準とするローカル`.mjs`をclonoのプラグイン候補として読み込む方式は、技術的に成立する。専用のモジュールローダー、`require()`またはJavaScriptオブジェクト全体のデータ変換は、今回の用途には必要なかった。

ClojureScriptからJavaScriptの基本情報とrenderer関数を直接扱い、同期的な文字列を結果として受け取れる。読み込み、export、競合、呼び出し時の例外および戻り値の種類も、異なる境界で検出できる。この構成は、最初の拡張点としてコラムの出力を差し替える最小プラグイン契約を設計するための基盤候補となる。

一方、fixtureで使用した設定、default export、基本情報、renderer名、入力、戻り値および検査結果は、検証のための仮契約である。今回の結果だけをもって公開APIとして固定しない。

## 後続設計への示唆

今回の結果から、後続の仕様およびADRでは次の方針を候補にできる。

- プラグインは`clono.config.mjs`から、設定ファイルを基準とする相対パスで明示的に列挙する
- ファイルシステム上のパスは文字列連結でURLにせず、`pathToFileURL()`を使用する
- ローカルプラグインはES Moduleとして読み込み、default exportにプラグイン情報とrenderer登録を持たせる
- rendererの登録と競合検査を、原稿の解析およびAST変換より前に完了する
- 同じrendererを複数のプラグインが登録した場合は、暗黙の優先順位を設けずエラーとする
- 初期のrendererを同期関数へ限定し、文字列以外の戻り値とPromiseをエラーにする
- モジュール読み込み、export検証、競合検査、renderer例外および戻り値検証を、別の診断として扱う
- JavaScriptオブジェクトと関数を不要にClojureScriptのデータ構造へ変換しない
- プラグインを信頼されたローカルコードとして扱い、サンドボックスを初期契約に含めない

これらは正式な決定ではない。後続の[コラムrenderer契約に関する調査](plugin-renderer-contract.md)では、コラムrendererへ渡す候補データ、raw HTMLとMarkdownを組み合わせる戻り値、失敗時の変換停止、および設定を`clono transform`へ明示する候補を検証した。両調査の結果をもとに、仕様およびADRで正式な契約を決定する。

## 成立条件と未確認事項

今回の結果は、Node.jsのES Moduleとして読み込めるローカル`.mjs`と、Node.jsの通常プロセスで動作するreleaseビルドを前提とする。

次の事項は検証または決定していない。

- `plugins`を正式な`clono.config.mjs`の設定項目とするか
- プラグインパスをプロジェクトルート外へ解決できるか、シンボリックリンクを許可するか
- npmパッケージ、`file:` URL、絶対パス、CommonJSまたはTypeScriptを読み込み対象とするか
- default exportの正確な形、必須項目、未知の項目および値の検証規則
- プラグイン名の一意性、バージョン文字列、および`apiVersion`の互換性規則
- `column` rendererへ渡す正式なデータと、戻り値となるraw HTMLの安全条件
- rendererの呼び出し時に`this`を提供するか
- rendererが入力オブジェクトを変更した場合の検出または保証
- 読み込み、検証、競合および実行に対する診断コード、文言、順序、位置情報
- 複数の異常がある場合に、可能な限り収集する範囲
- 複数プラグインの明示的な合成または適用順序
- top-level awaitやモジュール評価のタイムアウト
- プラグインのホットリロードとES Moduleキャッシュの無効化
- プラグインの依存関係、配布、探索、インストールまたは更新
- サンドボックス、権限制限、署名または信頼性の検証
- `clono transform`でプロジェクト設定を明示する候補を正式採用するか
- `clono build`の設定読み込み、変換パイプラインおよび診断との統合
- Node.js 22系、WindowsおよびLinuxでの実行

## 再現方法

[検証用fixtureのREADME](fixtures/plugin-loading/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm run verify`でClojureScriptテストとreleaseビルドしたNode.jsスクリプトによる結合検証を実行できる。

## 再調査する条件

- Node.js、shadow-cljsまたはClosure Compilerの更新によって、動的`import()`の生成または実行方法が変わる場合
- CommonJS、npmパッケージ、TypeScriptまたはリモートモジュールを直接読み込む必要が生じた場合
- 非同期rendererまたは非同期の初期化処理が必要になった場合
- プラグインを同じNode.jsプロセスで再読み込みする必要が生じた場合
- プラグインの合成、依存関係または順序付きの副作用が必要になった場合
- 信頼されていない第三者のプラグインを安全に実行する必要が生じた場合
- Windows、LinuxまたはNode.js 22系で、パス解決またはES Module読み込みの差異が見つかった場合

## 参照資料

- [プロジェクト憲章](../project-charter.md)
- [0002: Node.js向けClojureScript開発基盤を採用する](../decisions/0002-adopt-nodejs-clojurescript-development-platform.md)
- [0003: Generic DirectivesとmdastによるMarkdown変換パイプラインを採用する](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [検証用fixture](fixtures/plugin-loading/)
- [コラムrenderer契約に関する調査](plugin-renderer-contract.md)
- [Node.js v24.x: ECMAScript modules](https://nodejs.org/docs/latest-v24.x/api/esm.html)
- [Node.js v24.x: URL](https://nodejs.org/docs/latest-v24.x/api/url.html)
