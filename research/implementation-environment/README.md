<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# 実装言語・実行環境の調査

確認日：2026-10-16（日本時間）。

[選定の手順書](../../docs/plans/0002-choose-implementation-environment.md)の手順1として、公式資料、公開パッケージのメタデータと実装を調査した。本資料の候補は未採用であり、動作確認済みでもない。調査成果物の独立レビューと二人の確認は完了した。結果は手順書第8節に記録している。

## 1. 調査の目的と方法

ClojureScriptを第一候補に、Markdownの前処理CLIをNode.jsで動かし、利用者がJavaScriptファイルで出力を差し替えられる構成を調べた。開発者が理解・保守できることを重視し、既存ツールで満たせる処理は再実装しない。

調査の根拠と対象バージョンは[参照資料とパッケージ情報](sources.md)にまとめた。公式サイトの説明、公開済みバージョンの実装、今後の設計案を区別する。実装を読んだことは、実行による検証の代わりにはしない。

手順2で作成した[検証計画とテスト項目](../../docs/specs/implementation-environment-verification.md)では、手順1後に必須条件として確認したページ下部脚注も扱う。計画の状態と、実行済みの検証結果は区別する。

今回行ったのは、Web資料の閲覧、`npm view`によるメタデータの照会、公開ソースとVFMの配布アーカイブの読み取りである。依存パッケージのインストール、ビルド、CLIの試作、OS別の実行は行っていない。`fixtures/`・`scripts/`・`results/`も作成していない。

## 2. 検証へ進める候補

現時点でClojureScriptを候補から外す重大な問題は見つかっていない。ただし、利用者の環境要件と保守の負担は、次の候補を実行して確認する必要がある。

| 対象 | 最初に検証する案 | 理由と残る確認 |
|---|---|---|
| 実行環境 | Node.js 24.22.0 | 確認時点の24系LTS。Vivliostyle CLI 11.3.3の要件も満たすが、clonoの最低対応バージョンは未決定 |
| 本体のビルド | shadow-cljs 3.5.7 | npm依存、Node向け出力、REPLを扱える。開発にはJDK 21以上が必要 |
| JSとの接続 | shadow-cljsのESM出力と、Node向けCommonJS出力を小さく比較 | ESMの依存や利用者の拡張を、過度な連携コードなしで読み込める方を選ぶ |
| Markdown処理 | unified 11・remark-parse 11・remark-directive 4 | ASTと三種類のディレクティブを扱う候補。VFMとの記法の違いを確認する必要がある |
| HTML出力 | remark-rehype 11・rehype-stringify 10 | 標準要素の変換やHTML ASTの加工を利用できる |
| Markdown出力 | remark-stringify 11 | Markdownへ戻す比較候補。VFM独自記法の保持を保証するものではない |
| VFMの利用 | VFM 2.7.2を別の比較対象にする | 出版向けの機能と拡張口があるが、上記remark系とパーサーの世代が違う |

ここでいう版番号は調査時点の候補であり、組み合わせの動作を確認した結果ではない。手順2で検証用の版と方法を合意する。

## 3. ClojureScriptとビルド方法

### 3.1 開発環境と利用環境

[公式Quick Start](https://clojurescript.org/guides/quick-start)には、`cljs.main`でNode.js向けのJavaScriptを出力し、`node`で実行する手順がある。ビルドを開発側で済ませて配布する構成なら、利用者側へJDKを要求しない見通しがある。ただし、配布物に必要なファイルやnpm依存を含め、開発環境から分離して動かす確認が必要である。

[shadow-cljsのガイド](https://shadow-cljs.github.io/docs/UsersGuide.html)では、npm経由の起動、開発用の`compile`・`watch`と配布用の`release`を区別している。開発出力は配布に使わない。Node用の`:node-script`・`:node-library`と、ESM用の`:esm`が候補になる。単一の出力ファイルという説明だけで、外部npm依存まで不要になるとは判断しない。

| ビルド方法 | 利点 | 負担・今回の扱い |
|---|---|---|
| shadow-cljs | npm連携、複数の出力形式、REPLなどが一つのツールにまとまる | 独自の設定と最適化時の連携を理解する必要がある。最初の検証候補 |
| ClojureScript標準の`cljs.main` | 言語本体の公式手順を基準にでき、最適化段階を選べる | npm/ESM依存と拡張読み込みをどう組み立てるか、追加確認が必要。比較・代替候補 |

標準Quick StartはNodeでは`:simple`や`:none`でも十分と説明している。一方、shadow-cljsの配布用ビルドにはClosure Compilerの最適化が関わる。両者を同じビルド方法として扱わない。

### 3.2 ESMとCommonJS

[Node.jsの公式資料](https://nodejs.org/api/esm.html#interoperability-with-commonjs)によると、CommonJSからも動的`import()`でESMを読み込める。`require()`によるESMの読み込みは、モジュール側が同期的であることなどの条件がある。ESM専用パッケージを見つけても、CommonJSでは一切使えないとは判断しない。

検証する案は次の二つとする。

- ESM出力を使い、依存や拡張をESMとして読み込む。shadow-cljsの`:js-provider :import`も候補とし、必要な起動処理とNodeでの開発方法を確認する。
- Node向けCommonJS出力を使い、拡張などを動的に読み込む。依存の`require()`で動く範囲と、非同期の読み込みが必要な範囲を確認する。

shadow-cljsガイドのESM節には、開発用`:runtime`はブラウザーのみという記述が残る。一方、[3.5.7に対応する実装](https://github.com/thheller/shadow-cljs/blob/f2fbb3a9ba7be3f91ff386039020a1632646e458/src/main/shadow/build/targets/esm.clj)には、`:runtime :node`、Node向けの設定、`node-esm`の開発クライアントがある。ガイドの説明だけでNodeのREPLが利用不可とは判断せず、3.5.7の実動作を確かめる。

利用者の拡張パスはビルド後に決まるため、ソース中の固定した依存と分けて扱う。絶対パス、ファイルURL、拡張子、`package.json`の`type`による違いを確認する。WindowsのパスをESMへ渡す場合は、[Node.jsの`pathToFileURL`](https://nodejs.org/api/url.html#urlpathtofileurlpath-options)など標準機能を使う案を検討する。

### 3.3 Clojure経験者が確認する点

[Clojureとの違い](https://clojurescript.org/about/differences)では、実行基盤やホスト連携の違いを確認できる。今回の用途では次に絞って理解する。

- JavaのファイルAPIではなく、Node.jsのファイル・パスAPIを使うこと。
- JavaScriptのオブジェクト・配列と、ClojureScriptのマップ・ベクターを区別すること。
- JavaScript側の関数やメソッドを呼び、必要な場合に`this`を維持すること。
- 同期例外とPromiseの失敗を、それぞれCLIの失敗へつなげること。

ASTや拡張APIにはJavaScript側のデータ構造を保ち、本体の判断処理に必要な情報だけをClojureScript側へ渡す案を比較する。AST全体の相互変換や独自ラッパーを当然の前提にせず、コード量と理解の負担を確認する。

shadow-cljs 3.5.7の依存定義はClojureScript 1.12.145を指定している。1.12.145の[変更履歴](https://github.com/clojure/clojurescript/blob/master/changes.md)にはasync/await対応の追加もある。Promiseを扱うためだけに別ライブラリを追加する判断はせず、標準機能の範囲と分かりやすさを検証時に確認する。

### 3.4 最適化とデバッグ

[Externsの公式ガイド](https://clojurescript.org/guides/externs)は、最適化で外部APIのプロパティ名が変わる問題と、推論や型ヒントによる対処を説明している。拡張用に渡すオブジェクトやASTのキーが配布用ビルドでも保たれるかを確認する。

[source mapの公式説明](https://clojurescript.org/reference/source-maps)とshadow-cljsのデバッグ方法を根拠に、開発用・配布用の両方でエラーを元のClojureScriptへ追えるかを検証する。JavaScript拡張で発生したエラーについても、元ファイル・理由・スタックを失わないことを確かめる。

型ヒントや連携コードが必要という事実だけで不採用にはしない。必要量と変更のしやすさを実物で確認し、開発者が理解できるかを判断する。

## 4. Markdown・独自記法・出力の比較

### 4.1 現行のremark系

[mdast](https://github.com/syntax-tree/mdast)は見出し、本文、コード、強調、リストなどを異なるノードで表す。[remark-directive](https://github.com/remarkjs/remark-directive/blob/4.0.0/readme.md)は、インライン、単独ブロック、コンテナーのディレクティブを扱う。構文を認識する機能であり、章参照・改ページ・コラムとしての意味や出力は別の処理で与える。

これらは要求に合う候補だが、コードブロックや通常本文の誤変換を防げるか、コラム内の強調・リストが保持されるかは未実行である。記法はまだ確定しない。

[unified](https://github.com/unifiedjs/unified)は解析・変換・出力を分けて呼べる。先に複数ファイルを解析して情報を収集し、その結果を使って各ファイルを変換する構成が考えられる。ただし、一冊の管理や章番号の決定をライブラリが自動で提供するわけではない。収集・参照の最小例を検証する。

### 4.2 H1の差し替えと出力形式

[remark-rehype](https://github.com/remarkjs/remark-rehype/blob/11.1.2/readme.md)にはノード用の`handlers`があり、変換後のHTML ASTを加工する方法もある。独自ディレクティブに限定せず、通常のMarkdownのH1から章扉の構造を作る検証ができる見通しがある。見出しを非表示で残す必要性と、目次・章参照への影響は実例で確認する。

| 出力案 | 利点 | 確認する制約 |
|---|---|---|
| HTML | 構造をASTで組み立てやすい。Vivliostyleが直接入力にできる | VFMで提供される出版向けの変換をどこまで使うか。文書の外枠・CSS・メタデータも必要 |
| Markdown | 後段のVFMに変換を任せる構成を検討できる | 再出力で元の表記が変わり得る。HTMLブロック内のMarkdownや、VFM独自記法が同じ意味で保持されるか |

[remark-stringify](https://github.com/remarkjs/remark/blob/main/packages/remark-stringify/readme.md)はASTをMarkdownへ直列化する。未対応のVFM記法をそのまま往復できる保証にはならない。本文をHTMLの開始・終了タグで囲むだけで、内部のMarkdownも期待どおり解釈されるとは判断しない。

### 4.3 VFMの拡張と世代の違い

[VFMの仕様](https://docs.vivliostyle.org/vfm/vfm/)はCommonMark・GFMを基礎に、ルビ、脚注など本向けの記法を提供している。remark系を独自に構成しただけでVFMと互換になるわけではない。

公開済みVFM 2.7.2のメタデータと配布実装を確認したところ、ESMとして公開され、依存は`unified ^9.2.0`・`remark-parse ^8.0.2`・`remark-rehype ^8.1.0`である。一方、remark-directive 4.0.0はunified 11とmicromark系の拡張を使う。VFMの既存パーサーへ最新のremark-directiveを追加するだけで構文が認識されるとは判断できない。[根拠と確認方法](sources.md#s05-vfmの公開版)

VFM 2.7.2には`editPlugins`があり、Markdown AST用・HTML AST用のプラグインと変換ハンドラーを編集できる。標準要素の出力差し替えにも利用できる見通しがある。ただし、パーサーの世代差はこの入口だけでは解消しない。内部のプラグイン順序へ深く依存する場合は、更新時の保守負担も確認する。

また、VFMの`stringify()`は内部で同期処理を行う。非同期拡張を必要とする場合は`VFM()`で得られるprocessorを使うなど、同期APIと区別して検討する。変更方法の保証と実際の組み合わせは未検証である。

比較の方針は、まず現行remark系で要求の最小例を確認し、VFMが提供する機能との差を明確にすることとする。VFM内部のパーサーを丸ごと差し替えたり、VFM相当の機能を再実装したりする案は、今回当然に進める作業に含めない。必要になったら範囲と負担を相談する。

### 4.4 今回深掘りしない候補

[markdown-it](https://github.com/markdown-it/markdown-it)も拡張可能なパーサーで、JavaScriptのレンダリング規則を変更できる。調査時の15.0.3はESM/CommonJS両方の入口を持つ。一方、処理の中心はトークン列であり、今回希望するAST・ディレクティブとの対応には別の検討が必要になる。現行remark系に重大な問題が出ない限り、今回の最初の検証対象には加えない。

## 5. Vivliostyleとの接続・OS・費用

### 5.1 設定と生成物

[Vivliostyle CLI 11.3.3の設定資料](https://github.com/vivliostyle/vivliostyle-cli/blob/v11.3.3/docs/config.md)にはJavaScript設定、`entry`、`entryContext`、`workspaceDir`、`copyAsset`がある。clonoの構成ファイルから生成先の一覧を導き、Vivliostyle側の`entry`として使う構成は実現の候補になる。ただし、設定を読み込む形式と評価のタイミングは検証が必要であり、共有APIは未決定である。

同資料には`documentProcessor`の拡張もあるが、今回は独立した前処理CLIという合意を維持する。VFMの[利用例](https://github.com/vivliostyle/vfm#usage-with-vivliostyle-command)にも、HTMLを先に作りVivliostyleへ渡す例がある。

画像やCSSのコピーと、原稿から出力先への相対パスの変換は別の問題である。`copyAsset`だけですべて解決すると扱わず、clono出力先、Vivliostyleの作業先、画像や文書リンクの基準位置を確認する。

### 5.2 OSとNode.jsの前提

[Node.jsの配布一覧](https://nodejs.org/dist/index.json)には、24.22.0についてmacOS・Windows・Linuxの配布物がある。確認時点では24系と22系がLTS、26系がCurrentである。これはclonoや全依存のOS対応を保証するものではない。

Vivliostyle CLI 11.3.3の`engines.node`は`>=22.12.0`、VFM 2.7.2は`>=22`である。「Vivliostyleを使っているからどのNode.jsでもよい」とはせず、clonoと併用する版をそろえる必要がある。

Node.js 24.22.0を最初の検証候補とする。22系まで対応するか、26系を検証するかは後続で決める。今回、macOS・Windows・Linuxのいずれでも候補構成を実行していない。OS間ではパス区切り、ファイルURL、大文字小文字、改行、CLIの起動方法も確認対象になる。

### 5.3 ライセンスと費用

主要候補のライセンス表示は[sources.md](sources.md)に記録した。ClojureScriptとshadow-cljsのJVM側はEPL-1.0、shadow-cljsのnpm側はISC、remark系はMIT、VFMはApache-2.0である。Vivliostyle CLIの表示はAGPL-3.0であり、今回の外部ビルドコマンドとして扱うことと、コードをclonoへ取り込むことを区別する。

今回確認した候補に、利用に必須の有料サービスや有料IDEは見つかっていない。JDKの配布元と利用条件は環境を決めるときに確認する。配布物に含まれるClojureScriptランタイム、直接・間接のnpm依存、必要な表示は、実際の構成を決める際に[ライセンス方針](../../docs/specs/licensing.md)に従って確認する。メタデータの一覧だけで再配布条件の確認完了とはしない。

## 6. 手順2で具体化する確認事項

以下は手順1で分かった未確認事項であり、確定したテスト項目や実装仕様ではない。

| 確認事項 | 小さな検証で確かめたいこと |
|---|---|
| ビルドとESM | 開発用・配布用それぞれで依存が読み込めるか。NodeのREPLとsource mapが使えるか |
| 利用者の環境 | JDKやshadow-cljsの起動を必要とせず、ビルド済みの成果物と必要なnpm依存で動くか |
| JavaScript拡張 | 本体を再ビルドせず拡張ファイルを読み替えられるか。同期・非同期の成功と失敗を扱えるか |
| ASTとの連携 | JSオブジェクトを扱うコードを理解できるか。配布用最適化でもキーと関数呼び出しが保たれるか |
| 三種類の独自記法 | 通常本文・インラインコード・コードブロックを誤変換せず、コラム内部のMarkdownを保持できるか |
| H1の差し替え | 通常の見出しから章扉を作り、元の見出し情報を章参照や目次へ利用できるか |
| ファイル横断 | 設定順に複数ファイルから情報を集め、後ろの章の情報も前の章へ反映できるか |
| VFMとの違い | ルビ・脚注など必要な代表例の解釈と出力がどう違うか。独自記法と共存できるか |
| 設定の共有 | ファイルと順序の定義を一か所に保ち、Vivliostyleの入力へつなげられるか |
| 生成先とパス | 原稿を変更せず、別の生成先でも画像・CSS・文書リンクを参照できるか |
| エラーと理解 | 入力位置、拡張元ファイル、理由を確認できるか。連携コードの修正箇所を開発者が理解できるか |

各検証の入力、期待結果、ツール・版、コマンド、実行OS、テストの順序を手順2で具体化し、独立レビューと二人の合意を経てからコードを書く。パーサーの世代差を解消するために大きな独自実装が必要と分かった場合は、検証の範囲を広げる前に相談する。

## 7. 資料の再確認方法

公式資料を[sources.md](sources.md)のリンクから確認する。公開パッケージの版を固定して、次のようにメタデータを照会できる。

```sh
npm view shadow-cljs@3.5.7 version engines license gitHead --json
npm view @vivliostyle/cli@11.3.3 version engines license dependencies.@vivliostyle/vfm --json
npm view @vivliostyle/vfm@2.7.2 version engines license dependencies dist --json
npm view remark-directive@4.0.0 version type license dependencies --json
```

VFM 2.7.2はnpm公式レジストリの配布アーカイブを読み、`package.json`・`lib/index.js`・`lib/revive-parse.js`を確認した。取得内容のSHA-512がレジストリの`dist.integrity`と一致することも確認した。アーカイブの場所と識別値はsources.mdに残した。中のJavaScriptは実行していない。

ガイドや開発ブランチは更新されるため、記載内容が変わった場合は、今回の調査結果と再確認した結果を区別する。
