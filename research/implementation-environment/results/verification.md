<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# 実装言語・実行環境の検証結果

実施日：2026-10-16（日本時間）。[手順書](../../../docs/plans/0002-choose-implementation-environment.md)の手順3。[検証計画とテスト項目](../../../docs/specs/implementation-environment-verification.md)に対応する。

検証を実施し、比較結果を整理した。独立レビュー、指摘対応と再レビュー、T10を含む二人の確認は完了した。実装言語・実行環境の正式採用は決めていない。

## 1. 今回分かったこと

ClojureScriptから既存のJavaScriptのMarkdown処理系を呼び、JavaScriptオブジェクトのASTを加工し、外部のJavaScriptファイルでH1の出力を変えられた。ESM・CommonJSの開発用／配布用ビルドと、両方式のREPLが動いた。

通常本文のページ下部脚注は、現行remarkで前処理し、Markdownへ戻してVFMへ渡す経路で成立した。脚注の正規表現置換や独自の脚注エンジンは追加していない。一方、コラム内を先にHTML化する今回の処理では、コラム内の脚注が末尾注になった。この制約を許容するかは未決定である。

現行remarkから直接HTMLへ出す標準構成では、脚注は末尾注になった。VFMの既存機能を活用する利点はあるが、Markdownの再出力とHTML化する部分を区別する負担もある。どちらかの正式採用を、今回の結果だけで決めない。

## 2. 実行環境

| 対象 | 確認結果 |
|---|---|
| OS・CPU | macOS 27.0.1（26A434）、arm64 |
| Node.js・npm | 24.22.0、11.19.1 |
| JDK | Eclipse Temurin 21.0.12+8-LTS |
| ビルド | shadow-cljs 3.5.7、ClojureScript 1.12.145 |
| Vivliostyle CLI | 11.3.3 |
| 組版側の表示 | CLIのversion出力はcore 2.45.1。viewerは2.45.1、依存ツリーの@vivliostyle/coreは2.45.2 |
| PDF確認 | Poppler 25.09.1（pdftotext・pdftoppm・pdffonts） |
| 埋め込みフォント | HiraMinProN-W3／W6など。pdffontsで埋め込みを確認 |
| 依存の再現 | 調査ディレクトリのpackage-lock.jsonで直接・間接依存を固定 |

Node.js・JDKのバージョンは計画どおりである。shadow-cljsのビルドとREPL、Vivliostyleのローカル配信には、キャッシュへの書き込みやローカルポートの許可が必要だった。許可後に実行できたため、これらを候補の技術的な不成立とは扱わない。

Windows・Linuxでの実行はしていない。実行対象をmacOSとする計画を維持した。パス区切り・大文字小文字・改行・ファイルURLのOS差は、資料調査の確認事項として残る。macOSの日本語・空白を含むパスで動いたことだけでは、他OSを保証しない。

## 3. テストの進め方と結果

| 段階 | 失敗を確認した内容 | その後の確認 |
|---|---|---|
| T01・T03 | スタブがnullを返し、渡したJSデータや結果が失われる。Promiseのrejectが伝わらない | echoとPromiseによる呼び出しを実装し、期待値とエラーの伝達を確認 |
| T04・T05・T07 | parse／collectの結果がなく、AST・章情報・原稿位置を得られない | AST処理、情報収集、参照解決を実装して確認 |
| T03・T06・T07の接続 | スタブが出力ファイルを返さず、二章分を生成できない | JS側の読み込み・出力とCLJS側の変換を接続して確認 |
| 既存機能の観測 | VFM・remarkの既存実装を故意に壊して失敗させてはいない | HTML・PDF・REPL・デバッグ・分離環境を直接観測 |

失敗原因は、構文エラーやモジュール未解決と、期待する振る舞いの欠如を区別した。開発中には、子配列を持たないノードのundefinedをarray-seqへ渡す不具合と、テストのimport漏れも修正した。これらの実装ミスを、事前の失敗確認の根拠には数えていない。

自動テストは10件。開発用と配布用の双方で10件成功、失敗・スキップ0件を確認した。PDF座標の別検査では12本・24ページを確認し、全ページを120dpiで画像化して目視した。

| 項目 | 結果と制約 |
|---|---|
| T01 | ESM／CommonJSでJSデータと同じCLI結果を取得。unified・remark-parseなどのESM依存も読み込めた |
| T02 | 両方式のREPLでREPL_OKを返した。CLJSとJS拡張のエラーを確認。source mapの制約は第4節に記載 |
| T03 | JSの同期／非同期成功、同期throw／rejectを確認。新しいCLIプロセスで拡張ファイルの中身を変更すると出力が変わり、本体のSHA-256は不変 |
| T04 | ディレクティブをASTで処理し、通常本文・コードを区別。コラムの強調・リスト・コードを保持 |
| T05 | 設定順z-first→a-secondで番号を付与。前方／後方参照と、行位置を持つ二件の索引情報を取得 |
| T06 | 一か所の章配列を逆転すると出力一覧とVivliostyleの入力一覧も逆転。日本語・空白のある作業先で拡張・画像・CSS・文書リンクを扱えた |
| T07 | 原稿・設定・拡張・画像・CSSを含む入力のSHA-256は不変。存在しない章参照は原稿位置付きで失敗。原稿内への出力は拒否。拡張失敗時は非0終了し、&&の後続処理は実行されない |
| T08 | 通常本文のM-MD＋VFMはdpub／gcpmでページ下部脚注が成立。M-HTMLとコラムの制約は第5節に記載 |
| T09 | 配布用JSと実行用npm依存だけを分離先へ置き、ソース・shadow-cljsなし、JAVA_HOME・CLASSPATHなし、Java起動を拒否するPATHで前処理が成功 |
| T10 | 処理と制約を説明し、開発者が必要な範囲でソースを理解できたことと、検証結果に問題ないことを確認。正式採用時に許容する制約は手順4で判断する |

T09はmacOS上の分離確認であり、JDK未導入の別マシンでの試験ではない。分離先は同じlockfileからnpm ci --omit=dev --ignore-scriptsで準備し、shadow-cljsやCLJSソースを含めなかった。Javaを呼べない状態でも前処理が成立した。

## 4. ビルド方式とデバッグ

| 比較 | ESM | CommonJS |
|---|---|---|
| 開発用・配布用 | 両方成立 | 両方成立 |
| npm連携 | ネイティブimportの入口を使用 | Node.js 24.22.0のrequireから今回のESM依存を読み込めた |
| JS拡張 | JSのimportで読み込み、CLJS関数へ渡せた | 同じJS関数呼び出しで同期／非同期を確認 |
| REPL | :runtime :nodeで接続して評価できた | Node向け出力を起動して接続・評価できた |
| 配布用source map | Node標準の--enable-source-mapsだけでは生成JSの位置が出た | 同左 |
| デバッグの補助 | source-map-support/registerを読み込むとcore.cljs:72へ対応した | 同左。開発用ではshadow側の補助でCLJS位置を確認できた |

source mapは生成されていたが、配布用のマップはsectionsを持つ形式だった。Node標準機能だけでは元の位置が出ず、shadow-cljsの依存として入るsource-map-support 0.5.21を事前読み込みして確認した。この観測から、すべてのNode・マップ形式について一般化はしない。デバッグ時の追加パッケージや起動方法は、正式採用時の負担として残る。

後続のMarkdown・PDF比較にはESMを暫定使用した。今回の依存と同じモジュール形式で、ビルド済み関数をJS側から呼びやすかったためである。CommonJSが不成立だったからではない。

ESMのガイドと実装の記述差は、今回Node用のREPLを動かすことで確認できた。独自の動的ローダーや、パッケージを変換する追加バンドラーは導入していない。

## 5. 脚注と組版の観測

基本入力はA5・余白18mm・本文10ptの二ページ。ページ高さは約595ptで、短い注の上端は本文領域の下三分の一にあることを座標と画像で確認した。

| 経路 | NOTE_ONE | NOTE_TWO | 判定 |
|---|---|---|---|
| V-BASE dpub | 1ページ・y約523pt | 2ページ・y約523pt | ページ下部脚注が成立 |
| M-MD dpub | 1ページ・y約523pt | 2ページ・y約523pt | 同上 |
| V-BASE gcpm | 1ページ・y約521pt | 2ページ・y約521pt | 指定した脚注CSSで成立 |
| M-MD gcpm | 1ページ・y約521pt | 2ページ・y約521pt | 同上 |
| V-BASE pandoc | 2ページ・y約130pt | 2ページ・y約146pt | 末尾注。対照として想定どおり |
| M-HTML標準 | 2ページ・y約158pt | 2ページ・y約184pt | 末尾注。必須のページ下部脚注は不成立 |

dpubは参照とaside、gcpmはspan.footnoteを出力した。短い注は一回ずつ表示され、強調・リンクと番号の対応も保持されていた。通常本文について、脚注化の自前処理は不要だった。

コラム内の注は、M-MDのdpub／gcpmともコラムの枠内、y約163ptに出た。コラム内部を先にHTMLへ変換した段階で、現行remarkが末尾注のHTMLを作り、後段のVFMはそれを通常の脚注定義として受け取れないためである。コラムのMarkdown自体は保持できるが、内部脚注のページ下部配置は今回の実装では満たせない。改善には処理の分け方の再検討が必要であり、汎用の脚注変換を追加していない。

長い注と同じ注への二回の参照も補助例として観察した。dpub／gcpmとも長い注は二ページに分かれ、文字欠け・重なりは見られなかった。dpubでは二回目の参照番号も表示された。一方、gcpmでは今回のCSSで二回目の参照番号が見えなかった。重複参照まで要件に含める場合は、別の確認・対応が必要になる。

二章分の本は両経路で三ページとなり、章1、改ページ後の本文、章2の順だった。章扉画像、本文画像、コラム、コード、前章へのリンクを画像でも確認した。M-MDはVFMの画像処理によってキャプションが加わるなど、M-HTMLとの見た目の差もあった。

Vivliostyleへ絶対ファイルパスをentryとして渡す最初の設定では404になった。生成物のルートをentryContextにし、entryを相対パスにしたところ組版できた。章順の定義は共有JSONの一か所に保ち、CLIを前処理の内部へ組み込んでいない。

## 6. コードの責務と理解を確認したいこと

| ファイル | 役割 |
|---|---|
| [core.cljs](../scripts/src/probe/core.cljs) | JSライブラリで解析し、JSのASTを走査する。章・索引情報の収集、参照・独自記法の置換、JS関数の呼び出しを担当 |
| [run-book.mjs](../scripts/run-book.mjs) | 入出力、JS拡張の読み込み、HTML／Markdownへの直列化、VFMへの受け渡しを担当 |
| [book-config.mjs](../scripts/book-config.mjs) | 共有する章配列から出力先を導出 |
| [verification.test.mjs](../scripts/verification.test.mjs) | 元の用途から期待値を確認する10件の自動テスト |
| [inspect-pdfs.py](../scripts/inspect-pdfs.py) | 実際のPDFからページ・座標を取り、短い脚注と改ページの条件を確認。画像も出力する |

CLJSとJSの間では、AST全体をCLJSのマップへ往復変換しなかった。JSのchildrenは配列なので、その走査や置換にはJS配列とarray-seqを使っている。子を持たないノードはchildrenがundefinedになるため、空配列へ補う。JS関数の返り値はPromiseへそろえ、同期throwもrejectとして扱う。

本体の処理とJS側の入出力の分担は、検証を小さく保つためのものである。このまま製品のアーキテクチャや公開APIへ固定する意図はない。AST操作の独自ラッパー、DSL、プラグイン管理基盤、汎用の脚注変換は追加していない。

処理の流れと制約を説明した後、開発者から必要な範囲でソースを理解できたとの確認を得た。Promiseを使う理由も共有し、ほかの検証結果にも問題ないことを確認した。これをT10の理解確認とし、正式採用時に許容する制約は手順4で判断する。

## 7. 依存に残るリスク

npm auditの確認時点では全体で15件（critical 2、high 3、moderate 9、low 1）、分離先の実行依存では10件（high 3、moderate 7）が報告された。関連する主な依存は次のとおり。

- Vivliostyle CLI側のhandlebars 4.7.9に、[コード注入のアドバイザリ](https://github.com/advisories/GHSA-xw65-4hp5-5hc7)などが報告された。開発・組版用の依存として扱い、前処理だけの分離先へは入れていない。
- VFM→remark-parse 8.0.3→trim 0.0.1に、[正規表現によるサービス不能のアドバイザリ](https://github.com/advisories/GHSA-w5p7-h5w8-2hfq)が報告された。こちらはVFMを利用する前処理の実行依存にも残る。

今回の入力は検証用に自作したものに限定した。これにより第三者の原稿へ安全に使えると保証するものではない。固定した候補を勝手に更新・ダウングレードしたり、audit fixを実行したりはしていない。正式採用時に、対象経路への影響と対応方針を判断する必要がある。

## 8. 再現方法と保存するもの

作業場所はresearch/implementation-environment/。package.json・lockfile・設定・入力・検証コードは保存する。大量の生成HTML・Markdown・PDF・画像・ビルドキャッシュ・分離先はGit管理から外す。

```sh
npm ci
env JAVA_HOME=/Users/jun-haseba/.sdkman/candidates/java/21.0.12-tem PATH=/Users/jun-haseba/.sdkman/candidates/java/21.0.12-tem/bin:$PATH ./node_modules/.bin/shadow-cljs compile probe-esm probe-cjs
env JAVA_HOME=/Users/jun-haseba/.sdkman/candidates/java/21.0.12-tem PATH=/Users/jun-haseba/.sdkman/candidates/java/21.0.12-tem/bin:$PATH ./node_modules/.bin/shadow-cljs release probe-esm probe-cjs
node scripts/prepare-isolation.mjs
```

分離先results/isolation/でnpm ci --omit=dev --ignore-scriptsを実行してから、元の作業場所で次を実行する。

```sh
node --test scripts/verification.test.mjs
node scripts/generate-footnotes.mjs
./node_modules/.bin/vivliostyle build -c scripts/vivliostyle.config.js
```

M-HTMLの本はPROBE_PIPELINE=remark-htmlで同じ組版設定を使用する。脚注のHTMLはresults/generated/footnotes/に生成されるため、それぞれをvivliostyle buildの入力にし、同じ名前のPDFをresults/pdf/へ出す。最後にpython3 scripts/inspect-pdfs.pyで全ページを画像化・座標検査する。今回の12本の名前はgenerate-footnotes.mjsの10例と、book-remark-html／book-remark-md-vfmである。

REPLは計画どおりwatchを起動してから、node scripts/observe-repl.mjsで両ターゲットへ接続した。配布用ビルドを最後に残すため、watch終了後にreleaseを再実行した。

デバッグの補助は、Node起動時に-r source-map-support/registerを指定して再現できる。意図的なCLJSエラーはESMのfail()、CommonJSのrequire('./results/build/probe.cjs').fail()で起こせる。JS拡張の失敗例はfixtures/book/title-fail.mjsを指定する。

この実行はmacOSのパスとインストール済みJDKに依存する。別環境では環境パスを置き換え、ツールのバージョンを記録する。手順4の採用判断、製品コード、CI、公開用のビルドは追加していない。

## 9. 独立レビューと指摘対応

会話履歴を引き継がないサブエージェントが、合意した検証計画と成果物を確認した。自動テスト10件とPDF座標検査12本・24ページを再実行して成功し、代表4ページを画像でも確認した。さらに、存在しない章参照を原稿の一時コピーへ追加し、CLIの非0終了、原稿位置と参照先の表示、後続コマンドの停止を確認した。

新規チェックアウト時にREPL観測スクリプトのログ保存先が存在せず、保存に失敗する指摘が1件あった。指摘を採用し、[observe-repl.mjs](../scripts/observe-repl.mjs)でループ前に保存先を再帰的に作成するよう修正した。保存先がない状態と既にある状態の両方で、両ターゲットのログ保存と終了コード0を確認した。この確認では子プロセスをスタブに置き換えており、実REPLの再検証とは区別する。構文チェックと差分チェックも成功した。同じレビュアーによる再レビューで、指摘の解消と追加指摘なしを確認した。

レビューでは依存の再インストール、ビルド・実REPL・PDF組版そのものの再実行、全24ページの再目視はしていない。ビルドとREPLは現物の出力・ログとコードで確認した。第4〜7節に記録した制約とリスクは残り、手順4の採用判断で扱う。
