<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# 実装言語・実行環境の検証結果

実施日：2026-10-16〜17（日本時間）。[手順書](../../../docs/plans/0002-choose-implementation-environment.md)の手順3〜4。[検証計画とテスト項目](../../../docs/specs/implementation-environment-verification.md)に対応する。

検証を実施し、比較結果を整理した。独立レビュー、指摘対応と再レビュー、T10を含む二人の確認は完了した。採用方針は[ADR 0002](../../../docs/decisions/0002-choose-implementation-environment.md)と[実装環境の仕様](../../../docs/specs/implementation-environment.md)に記録し、両文書の独立レビューと二人の確認も完了した。

第1〜9節は完了済みの手順3の記録である。手順4で実施したコラム内脚注の追加検証は第10節に記録し、その成果物の独立レビューと二人の確認も完了した。

第11〜14節は、その後の依存リスクの調査・検証を各実施時点で記録したものである。合意した方針と依存の復元、独立レビュー・二人の確認の完了は第15節を参照する。手順4の文書化と最終確認も完了した。各節に残る未決定・未確認の記述は、その実施時点の状況を示す。

目的に応じて、次の節から確認できる。

- 採用方針と依存リスクの扱い：[第15節](#15-手順4の合意した方針と依存の復元)。
- 最初の検証：[第1節からの結果](#1-今回分かったこと)と[第8節の再現方法](#8-再現方法と保存するもの)。
- コラム内脚注の追加検証：[第10節](#10-手順4の追加検証コラム内脚注)。
- 依存リスクの調査と更新実験：[第11節からの調査](#11-手順4の依存リスク確認報告の再取得)と[第14節のtrim更新・配布実験](#14-手順4の依存リスク確認trim-003での検証)。

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

## 10. 手順4の追加検証：コラム内脚注

実施日：2026-10-16。[追加検証計画](../../../docs/specs/implementation-environment-verification.md#10-手順4の追加検証コラム内脚注)に従い、手順3と同じmacOS・Node.js 24.22.0・依存で実行した。本節は手順3の結果とは分けて記録する。追加検証成果物の独立レビューと開発者の理解確認は完了した。正式な採用方針は未決定である。

### 10.1 結果

コラムを囲むHTMLと内部のMarkdownを分けて再出力すると、VFM 2.7.2は内部もMarkdownとして解析し、dpub・gcpmの両方でページ下部脚注を出力した。コラム内定義、コラム外の後方定義、二ページにわたる通常本文とコラムの混在で成立した。強調・リスト・コード、脚注の強調とリンク、コラム前後の本文も保持された。

| 題材 | dpub | gcpm |
|---|---|---|
| コラム単体 | 1ページ、注の上端y約523pt | 1ページ、y約521pt |
| 通常本文とコラムの混在 | 2ページ。各ページに本文注・コラム注を一つずつ、y約509／523pt | 2ページ。同じ対応でy約505／521pt |
| コラム外の後方定義 | 参照と同じ1ページ、y約523pt | 参照と同じ1ページ、y約521pt |
| コード内の記法と実際の脚注 | 記法は文字として残り、実際の注だけy約523pt | 記法は文字として残り、実際の注だけy約521pt |
| 通常本文の回帰確認 | 元の二ページで各注がy約523pt | 元の二ページで各注がy約521pt |
| 従来方式の対照 | コラム内の末尾注、y約163pt | コラム内の末尾注、y約163pt |

A5・余白18mm・本文10ptと既存のモード別CSSを維持した。12本・16ページの座標検査が成功し、120dpiで全ページを画像化して目視した。参照番号と注番号の対応、囲みの範囲、欠落・意図しない重複・重なり・文字欠けがないことを確認した。入力4ファイルのSHA-256も生成前後で一致した。

### 10.2 必要な処理と制約

[core.cljs](../scripts/src/probe/core.cljs)にprepare-columnsを追加した。コラムのcontainerDirectiveを、開始HTMLノード・既存の子ノード・終了HTMLノードの並びに置き換える。親のchildrenへその並びを展開するため、子要素を再帰的に処理する。コラム以外のノードは保持し、脚注定義や参照をHTMLへ変換しない。

Markdownの直列化には既存のremark-stringify、脚注のHTML化には既存のVFMを使う。新しい依存、パーサーの改造、脚注の正規表現置換、汎用の脚注変換は追加していない。開始・終了HTMLの間に空行を入れる直列化結果を含め、今回固定したライブラリ構成で成立したという観測であり、任意のMarkdown処理系や独自HTMLで同じ結果を保証しない。

コード内に閉じ記法の「:::」そのものを書く入力では、外側のコラムの区切りを「::::」にした。同じ長さにすると最初のremark-directiveの解析時点でコラムが閉じ、後続の脚注もコードとして解釈された。変換処理でこの入力を補正せず、区切りを長くした入力でコードの保持を検証した。この注意はページ下部脚注化とは別の、記法上の制約として残る。

この追加検証は専用の入口で行い、従来のrun-book.mjsとtransformのコラムHTML化は変更していない。そのため既存の本の生成が自動で新方式に切り替わることはない。新方式と章参照・索引・H1拡張を組み合わせた製品処理の構成は、今後の設計で判断する。コラムの入れ子、長い脚注、同じ脚注への複数参照の完全対応も検証対象外である。

### 10.3 テストと回帰確認

prepare-columnsを入力を返すだけのスタブにした状態で、期待する開始HTMLノードがないためテストが失敗することを確認した。その後に処理を実装し、ASTとコード保持の2件、4入力×2モードのHTML確認8件が成功した。最初の保存先パスの指定ミスはテストの失敗確認に数えていない。

追加の10件は開発用・配布用ビルドで成功した。最終の配布用ビルドでは既存10件も合わせて20件成功、失敗・スキップ0件だった。両ビルドは警告0件。実行依存・起動方法は変えていないため、追加検証に伴う分離先の再構築は行っていない。既存10件に含まれる従来方式の分離実行は成功したが、これは新しいコラム処理を分離先で実行した確認ではない。

### 10.4 保存対象と再現方法

入力はfixtures/footnotes/column-followup/のsingle.md・mixed.md・external.md・code.md。処理と起動は[column-followup.mjs](../scripts/column-followup.mjs)、テストは[column-followup.test.mjs](../scripts/column-followup.test.mjs)、PDF生成は[build-column-pdfs.mjs](../scripts/build-column-pdfs.mjs)、座標検査と画像化は[inspect-column-pdfs.py](../scripts/inspect-column-pdfs.py)に保存した。core.cljsとshadow-cljs.ednには処理とESMの公開関数を追加した。

作業場所はresearch/implementation-environment/。手順3の依存とビルド環境を準備し、配布用ビルドを更新してから実行する。

```sh
env JAVA_HOME=/Users/jun-haseba/.sdkman/candidates/java/21.0.12-tem PATH=/Users/jun-haseba/.sdkman/candidates/java/21.0.12-tem/bin:$PATH ./node_modules/.bin/shadow-cljs release probe-esm probe-cjs
node --test scripts/column-followup.test.mjs
node --test scripts/verification.test.mjs
node scripts/column-followup.mjs
node scripts/build-column-pdfs.mjs
python3 scripts/inspect-column-pdfs.py
```

既存テストの分離先は第8節の方法で準備する。PDF生成にはローカル配信とブラウザー起動の許可が必要だった。追加結果のMarkdown・HTML・座標JSONはresults/generated/column-followup/、PDFとページ画像はresults/pdf/column-followup/へ出す。手順3の出力とは別の場所に保存し、生成物はGit管理から外している。

今回の範囲では小さなAST変換で成立したため、別案や検証範囲の拡大は行わなかった。独立レビューと開発者の理解確認を終え、手順4の採用判断の材料がそろった。

### 10.5 独立レビューと二人の確認

会話履歴を引き継がない新しいサブエージェントが、合意計画、ASTとコードの保持、HTMLの注構造、PDFの番号対応と囲み、再現方法、回帰確認の範囲、制約とライセンス表示を確認した。重大な問題・修正推奨・任意の改善はいずれもなし。指摘による修正がないため、成果物の再レビューは実施していない。

追加10件と既存10件の全20テスト、PDF12本・16ページの座標検査が再実行でも成功した。保存済み8組のMarkdown・HTMLは現在の変換結果と一致した。混在4ページを個別画像、全16ページを一覧画像で目視し、欠落・番号不整合・囲みの逸脱・重なりがないことを確認した。差分チェックも成功した。

レビューでは依存の再インストール、CLJSの再ビルド、PDFの再組版、隔離環境の再構築は行っていない。開発用・配布用ビルドの成功履歴自体を独立して再現した確認とは区別する。製品への統合や新方式の隔離実行も未検証のままである。

結果と処理の説明後、開発者から問題がないことと、おおむね内容を理解できたとの確認を得た。独立レビュー結果の共有後も理解に問題ないことを確認し、追加検証の実施・検証・独立レビュー・二人の確認を完了した。正式な採用方針と許容する制約は、手順4の残る作業として判断する。

## 11. 手順4の依存リスク確認：報告の再取得

確認日：2026-10-17（日本時間）。固定済みのpackage-lock.jsonを使い、npm auditの報告を全依存と前処理の実行依存に分けて取り直した。この段階は報告の取得と比較までとし、アドバイザリ本文・対象コードによる発生条件や到達可能性の調査、許容可否の判断、依存の変更は行っていない。

### 11.1 件数と前回との差

| 対象 | critical | high | moderate | low | 合計 | 重複を除いた元のアドバイザリ |
|---|---|---|---|---|---|---|---|
| 全依存 | 2 | 3 | 9 | 1 | 15 | 9 |
| 前処理の実行依存（omit=dev） | 0 | 3 | 7 | 0 | 10 | 3 |

合計はnpm auditが報告する脆弱なパッケージの件数であり、個別の問題の数ではない。同じ問題の影響を受ける依存元も数えられる。元のアドバイザリはvia内のsourceを重複除去して数えた。

前回保存した全依存のJSONと比較し、vulnerabilitiesの全レコードが一致した。対象パッケージ、重要度、元のアドバイザリ、報告された修正候補に変更はなかった。実行依存の件数も第7節の記録と一致した。前回の実行依存JSON自体との詳細比較は行っていない。

依存総数は前回・今回とも857だが、prod／devの内訳は異なる。前回の全依存JSONはCLIをdevDependenciesへ移す前の分類を含み、今回は現在の分類で取得した。脆弱性レコードの増減とは区別する。

### 11.2 報告対象の整理

元のアドバイザリが直接付くパッケージは、次の6種類だった。重要度は今回のnpm auditの表示を記録したものであり、clonoでの影響を判定したものではない。

| 区分 | パッケージ・解決済みバージョン | 重要度 | 元のアドバイザリ数 |
|---|---|---|---|
| 実行依存にも残る | trim 0.0.1 | high | 1 |
| 実行依存にも残る | prismjs 1.27.0 | moderate | 1 |
| 実行依存にも残る | valibot 1.2.0 | moderate | 1 |
| 開発・組版用に限る | handlebars 4.7.9 | critical | 3 |
| 開発・組版用に限る | dompurify 3.4.13 | low | 2 |
| 開発・組版用に限る | press-ready配下のuuid 8.3.2 | moderate | 1 |

実行依存で報告された10パッケージは、trim、remark-parse、prismjs、refractor、valibot、@vivliostyle/vfm、@vivliostyle/vfm-internal-utils、@vivliostyle/remark-footnote-dpub、@vivliostyle/remark-footnote-gcpm、@vivliostyle/remark-footnote-pandocである。開発・組版用を含めると、@vivliostyle/cli、handlebars、dompurify、press-ready、uuidの5パッケージが加わる。

これは依存区分の整理であり、各問題の発生条件に該当する処理をclonoが実際に呼ぶかは未確認である。開発・組版用の問題も調査対象に残す。

### 11.3 再現方法と保存先

作業場所はresearch/implementation-environment/。次の二つを実行し、JSONにerrorがなく、監査結果を取得できたことを確認した。終了コード1は脆弱性の報告によるものだった。初回のサンドボックス内実行はレジストリの名前解決に失敗したため、通信の許可を得て再実行した。

```sh
npm audit --json > results/logs/audit-2026-10-17-all.json
npm audit --omit=dev --json > results/logs/audit-2026-10-17-runtime.json
```

実行前後でpackage.jsonとpackage-lock.jsonのSHA-256が一致した。npm install、npm update、npm audit fixは実行していない。JSONは既存の除外対象であるresults/logs/へ保存し、本節に比較結果を残した。前回の全依存JSONは今回の作業時点で/private/tmp/clono-environment-audit.jsonに存在したが、リポジトリの保存対象ではない。

報告の再取得は完了した。次に、元の9件のアドバイザリをもとに発生条件と利用経路への影響を調べ、対応の選択肢を整理する。今回の件数だけでは、採用可否や安全性を判断しない。

## 12. 手順4の依存リスク確認：問題ごとの発生条件

確認日：2026-10-17（日本時間）。第11節の9件について、GitHub Advisory Databaseの説明とlockfileで固定したインストール済みコードを照合した。本節は発生条件と利用経路の整理までとし、更新・回避・許容の方針は未決定である。

### 12.1 前処理の実行依存に残る3件

| 問題・参照元 | 発生に必要な条件 | 固定済み構成との照合 |
|---|---|---|
| trim：正規表現によるサービス不能。[GHSA-w5p7-h5w8-2hfq](https://github.com/advisories/GHSA-w5p7-h5w8-2hfq) | trimへ正規表現の計算量を増やす文字列が渡る | VFM→remark-parse 8.0.3→trim 0.0.1の経路があり、段落・リスト・強調・引用・インデント除去で原稿由来の文字列を渡す。追加検証の経路でも通常入力で10回の呼び出しを観測した |
| PrismJS：DOM Clobberingからのスクリプト実行。[GHSA-x7hr-w5r2-h6wg](https://github.com/advisories/GHSA-x7hr-w5r2-h6wg) | ブラウザーへ細工したHTMLを置き、Prismのdocument.currentScript参照などを利用する | VFMはNode.js上でrefractor.highlightを呼ぶ。refractorはprism-coreを読み、Node.jsではdocumentのない分岐から戻る。通常入力の観測でもdocumentはundefinedで、生成HTMLにPrismのscriptは追加されなかった |
| Valibot：検証エラーの整形時のTypeError。[GHSA-5qjj-4xww-7phc](https://github.com/advisories/GHSA-5qjj-4xww-7phc) | recordでtoStringなどのキーを含む入力を検証し、生じたissuesをflattenへ渡す | VFM・脚注プラグイン・内部utils・CLIの配布JSを検索し、Valibotのflatten呼び出しは見つからなかった。CLIにはrecordがあるが、その存在だけでは必要な組み合わせを満たさない |

trimは問題のある関数への到達を確認した。ただし、問題を引き起こす具体的な入力が今回のAST再出力を通過するか、どの長さでどの程度遅くなるかは未検証である。ASTからMarkdownへ戻すことだけで問題入力を排除できるとは扱わない。長大入力で停止を再現する実験は行っていない。信頼する自作原稿だけを扱う前提も含め、影響と対応方針を次の段階で判断する。

PrismJSの判断はNode.js上の前処理に限定する。生成HTMLへ利用者がPrismスクリプトを追加する場合や、ブラウザー向けに処理を移す場合は再評価する。任意の入力HTMLやJS拡張の安全性を保証するものではない。

Valibotの問題はエラー処理の例外であり、アドバイザリではグローバルなprototype pollutionと区別されている。今後、本体や拡張でrecordとflattenを組み合わせる場合は再評価する。

### 12.2 開発・組版用に限られる6件

| 問題・参照元 | 発生に必要な条件 | 固定済み構成との照合 |
|---|---|---|
| Handlebars：事前コンパイル結果のHTML埋め込み。[GHSA-xw65-4hp5-5hc7](https://github.com/advisories/GHSA-xw65-4hp5-5hc7) | 細工したテンプレートをprecompileし、そのJS出力をHTMLのscript要素へ直接埋め込む | CLI内の呼び出しは雛形生成用のcompile(text, {noEscape:true})であり、precompile結果のscript埋め込みは確認できない。今回のbuildでは雛形生成を行わない |
| Handlebars：ASTの型混同によるコード注入。[GHSA-8r5x-fm3f-whwj](https://github.com/advisories/GHSA-8r5x-fm3f-whwj) | 信頼できないASTオブジェクトをcompileまたはprecompileへ渡す | CLIの雛形生成はファイルのBufferをtoStringした値、または組み込み文字列をcompileへ渡す。ASTオブジェクトを渡す経路は確認できない。Markdown ASTをHandlebarsへ渡す処理もない |
| Handlebars：own property検査を使ったコード注入。[GHSA-p8wg-vrv2-v86f](https://github.com/advisories/GHSA-p8wg-vrv2-v86f) | 制御されたテンプレートを、allowProtoMethodsByDefault:trueなどprototypeメソッドへのアクセスが可能な条件で描画する | CLIの雛形生成はcompileへnoEscapeのみを指定し、このprototypeアクセス許可は確認できない。雛形生成自体も今回のbuild経路には含まれない。noEscapeだけで本件の条件を満たすとは扱わない |
| DOMPurify：除去フック後のイベント属性の残存。[GHSA-p98j-92pf-mc4p](https://github.com/advisories/GHSA-p98j-92pf-mc4p) | DOMノードをIN_PLACEで処理し、afterSanitize系フックでノードを除去する | CLIはJSDOMで作ったDOMPurifyへ見出しのinnerHTML文字列を渡す。IN_PLACE指定や該当フックの登録は確認できない |
| DOMPurify：除去されたrawtextルートの再解析。[GHSA-6688-9rhm-gjv2](https://github.com/advisories/GHSA-6688-9rhm-gjv2) | DOMノードをIN_PLACEで処理し、除去されたルートを返却・直列化してHTMLとして再解析する | 同じsanitize呼び出しは文字列入力でIN_PLACEを指定しない。アドバイザリのDOMノード入力と返却経路に一致しない |
| uuid：出力バッファーの境界検査不足。[GHSA-w5hq-g745-h8pq](https://github.com/advisories/GHSA-w5hq-g745-h8pq) | v3・v5・v6へ、小さすぎる外部出力バッファーや範囲外offsetを渡す | audit対象はpress-ready配下のuuid 8.3.2。press-readyはghostScript処理でv4()を引数なしで呼び、外部バッファーを渡さない。CLI自身のuuid 14.0.0は今回のaudit対象ではない。検証用PDF設定ではpress-readyのpreflightも指定していない |

各行は発生条件と確認した呼び出し方の比較であり、パッケージ全体やCLIのすべての使い方についての安全判定ではない。雛形生成、テンプレートの設定、DOMPurifyの設定、press-readyの利用APIを変える場合は前提を見直す。

### 12.3 照合したコードと再現方法

依存のソースはresearch/implementation-environment/node_modules/内の、現在のlockfileから解決したものを確認した。

| 経路 | 主な照合先 |
|---|---|
| VFM→trim | @vivliostyle/vfm/node_modules/remark-parse/lib/tokenize/、lib/util/remove-indentation.js、trim/index.js |
| VFM→PrismJS | @vivliostyle/vfm/lib/plugins/code.js、refractor/core.js、prismjs/components/prism-core.jsのdocumentなしの分岐 |
| Valibot | @vivliostyle/vfm/lib/、@vivliostyle/vfm-internal-utils/lib/、@vivliostyle/remark-footnote-*/lib/、@vivliostyle/cli/dist/のrecord・parse・safeParse・flattenの呼び出し |
| Handlebars | @vivliostyle/cli/dist/scaffold-DlBNHRiW.jsのformatとファイル読み込み、create-BzjKElfg.jsのsetupConfigFile |
| DOMPurify | @vivliostyle/cli/dist/server-96BNRrbl.jsのhtmlPurify初期化と見出しのsanitize |
| uuid | press-ready/lib/ghostScript.js、CLIのpreflight処理と検証用組版設定 |

[observe-dependency-paths.mjs](../scripts/observe-dependency-paths.mjs)は、通常の小さなMarkdownをコラム追加検証の経路へ渡し、trimの呼び出し回数、読み込んだPrismモジュール、documentと出力scriptの有無を確認する。trimの観測用ラッパーはそのNodeプロセスのメモリー内だけに置き、依存ファイルは変更しない。攻撃用テンプレートや長大な正規表現入力は実行していない。

手順3の依存と配布用ビルドを用意し、調査ディレクトリから次を実行する。出力は既存の除外対象へ保存した。

```sh
node scripts/observe-dependency-paths.mjs > results/logs/dependency-paths-2026-10-17.json
```

この観測ではtrimが10回呼ばれ、PrismJSはcomponents/prism-core.jsだけが読み込まれた。documentはundefinedで、生成HTMLにscript要素はなかった。これ以外の行はコードの静的な照合による整理であり、攻撃を再現して発生しないことを実証した確認ではない。第三者の拡張や将来の製品コードは対象外である。package.jsonとpackage-lock.jsonのSHA-256も前後で一致した。

発生条件の整理は完了した。trimは利用する関数への到達を確認し、残る8件は今回確認した入口ではアドバイザリの追加条件に一致しなかった。更新・回避・許容の選択肢の比較と、独立レビュー・二人の確認は残っている。依存の更新、差し替え、回避処理の追加は行っていない。

## 13. 手順4の依存リスク確認：上流更新の選択肢

確認日：2026-10-17（日本時間）。VFM・remark-parse・trimの公開レジストリ情報と、VFMのリリース、mainの固定コミット、関連Issue・PRを確認した。インストールや依存更新、更新候補の動作検証は行っていない。

### 13.1 公開版と依存関係

| 確認対象 | 公開情報から分かったこと |
|---|---|
| VFM | latestは検証済みと同じ2.7.2。公開バージョン一覧にも、それより新しい版はなかった。2.7.2の公開日は2026-07-15（UTC）。next・alphaタグも旧1.0.0-alpha系で、今回の移行版ではない |
| VFM 2.7.2の依存 | remark-parseを^8.0.2で要求する |
| remark-parse 8系 | 公開された8.0.0〜8.0.3はいずれもtrim 0.0.1を固定している。8系の更新だけで問題を解消する公開版は見つからなかった |
| remark-parseの新世代 | latestは11.0.0。公開メタデータの直接依存にtrimはなく、micromark系の解析基盤を使う。ただし、これを旧VFMへそのまま適用できるとは扱わない |
| trim | latestは1.0.1。アドバイザリ上の最初の修正版0.0.3も公開済み。VFMの依存側が要求する0.0.1は自動では置き換わらない |

参照したレジストリは、[VFM](https://registry.npmjs.org/@vivliostyle%2fvfm)、[remark-parse](https://registry.npmjs.org/remark-parse)、[trim](https://registry.npmjs.org/trim)。修正版の判定は[trimのアドバイザリ](https://github.com/advisories/GHSA-w5p7-h5w8-2hfq)を参照した。レジストリ情報の取得だけを行い、配布コードは実行していない。

### 13.2 未リリースの対応状況

mainはコミット5889f989800170db52d5266ce4e386ab1fb935e4を固定して確認した。コミット時刻は2026-10-17 01:11:07 UTC。packages/vfm/package.jsonはversion 2.7.2、remark-parse ^8.0.2のままで、pnpm-lock.yamlもremark-parse 8.0.3とtrim 0.0.1を解決していた。ルートpackage.jsonにもこれを変更するoverridesは見つからなかった。[固定コミットのpackage.json](https://github.com/vivliostyle/vfm/blob/5889f989800170db52d5266ce4e386ab1fb935e4/packages/vfm/package.json)、[lockfile](https://github.com/vivliostyle/vfm/blob/5889f989800170db52d5266ce4e386ab1fb935e4/pnpm-lock.yaml)。

上流には[移行計画Issue #275](https://github.com/vivliostyle/vfm/issues/275)と、mainへ追加された[移行資料](https://github.com/vivliostyle/vfm/blob/5889f989800170db52d5266ce4e386ab1fb935e4/docs/dev/remark13-migration.md)がある。資料でいう「remark 13以降」への移行先は、remark-parse 11・unified 11などの組み合わせである。現行packagesと移行用packages-nextを併存させ、互換性を確認してからmajor更新として公開する方針が示されている。確認したmainのツリーにはpackages-next配下のファイルはまだなかった。

移行では旧tokenizer拡張、ASTからHTMLへのハンドラー、脚注・ルビ・属性などの構文処理の変更が必要とされている。[破壊点の整理 #279](https://github.com/vivliostyle/vfm/issues/279)、[構文再実装 #285](https://github.com/vivliostyle/vfm/issues/285)、[互換検証 #293](https://github.com/vivliostyle/vfm/issues/293)を確認した。したがって、新しいremark-parseの依存からtrimが消えたことだけを根拠に、VFM内部のパーサーを更新可能とは判断しない。

過去の[trimとremark-parseの更新PR #219](https://github.com/vivliostyle/vfm/pull/219)は、remark-parse 11に対応していない旨のコメントで、2025-12-14に未マージのまま閉じられていた。[旧移行PR #227](https://github.com/vivliostyle/vfm/pull/227)も、2026-06-13に未マージで閉じられていた。PRのclosed状態だけで実装済みと判断せず、merged状態と現在のコードを照合した。

### 13.3 現時点の選択肢

上流の公開版へ通常更新するだけでtrimを解消する候補は、今回確認した範囲では見つからなかった。未リリースの移行計画はあるが、採用して検証できる完成済みの公開更新版とは区別する。公開時期も未確認であり、それを待つことを前提にしない。

次に検討できるのは、現在のVFM構成を維持しつつtrimの修正版を限定的に使う方法と、その互換性・配布後の適用確認である。修正版の選定、差し替え方法、検証の範囲はまだ合意していない。上流へのIssue・PR投稿も行っていない。

### 13.4 取得資料と確認の範囲

公開メタデータ、GitHub APIの検索結果、固定コミットのmanifest・lockfile・移行資料をresults/logs/へ保存した。検索はVFMリポジトリ内のtrimとremark-parseを対象とし、結果はそれぞれ5件・48件、incomplete_resultsはfalseだった。関連するIssue・PRを確認したが、非公開のセキュリティ対応や別ブランチすべてを調査したものではない。

Web経由のraw main/package.jsonは古い2.3.0の内容を返し、一部Issueの取得も失敗したため、現在の判断には使わなかった。レジストリとGitHub APIを直接取得し、mainのSHAを固定した資料で補った。npm viewは応答が完了せず中断し、必要な公開メタデータはレジストリから取得した。依存更新は行わず、package.jsonとpackage-lock.jsonのSHA-256は第11節の作業前と一致した。

## 14. 手順4の依存リスク確認：trim 0.0.3での検証

実施日：2026-10-17（日本時間）。依存を更新した状態で検証する依頼を受け、修正版trim 0.0.3を限定的に適用した。動作互換性と、利用者側のインストールでも修正版が使われるかを分けて確認した。独立レビューと二人の確認、正式な採用判断は未了である。

### 14.1 変更した依存とaudit

調査用package.jsonに、remark-parse@8.0.3のtrimを0.0.3へ指定するoverridesを追加した。npm install --ignore-scriptsを実行した結果、lockfile上のパッケージ変更はtrim 0.0.1から0.0.3への一件だけだった。差分はversion・resolved・integrityで、ほかのパッケージのバージョンや依存宣言は変わっていない。npm audit fixは実行していない。

| 対象 | 更新前 | 更新後 |
|---|---|---|
| 全依存の報告 | 15件：critical 2、high 3、moderate 9、low 1 | 13件：critical 2、high 0、moderate 10、low 1 |
| 実行依存の報告 | 10件：high 3、moderate 7 | 8件：high 0、moderate 8 |
| 重複を除いた元のアドバイザリ | 全依存9件、実行依存3件 | 全依存8件、実行依存2件 |

trimとremark-parseの報告が消え、VFMの重要度はhighからmoderateへ変わった。残るアドバイザリを解消したわけではない。更新後JSONはresults/logs/audit-trim-0.0.3-all.jsonとaudit-trim-0.0.3-runtime.jsonに保存した。

trim 0.0.3のソースでは、通常の文字列にString.prototype.trimを使う。従来の正規表現をそのまま使う処理ではないことも確認した。ただし今回、攻撃用の長大入力で性能差を測る実験はしていない。

### 14.2 動作と生成物の互換性

shadow-cljsの開発用・配布用ビルドを両方実行し、警告0件で成功した。両方で既存10件とコラム追加10件の計20テストが成功した。配布用の最終確認では分離先も更新後のpackage.json・lockfile・ビルドで作り直し、npm ci --omit=dev --ignore-scriptsを実行した。

分離先にtrim 0.0.3が入ることを確認し、Java起動を拒否するPATH、JAVA_HOME・CLASSPATHなし、shadow-cljs・CLJSソースなしで前処理が成功した。専用のコラム処理の入口も分離先へコピーし、配布用CLJSからページ下部脚注のHTMLを出力できた。従来方式だけの隔離確認とは区別して、今回の新方式も確認した。

更新前のpackage.json・lockfile、コラムの生成Markdown・HTML・PDF・画像、既存脚注のHTML・PDF・画像はresults/generated/trim-update/before/へ保存した。更新後、コラム側のMarkdown・HTML20ファイルと既存脚注HTML10ファイルを比較し、全30ファイルがバイト単位で一致した。

既存の12本・24ページと、コラム追加の12本・16ページを再組版し、両方の座標検査が成功した。120dpiで描画した全40ページは、更新前の画像と画素単位で一致した。代表として混在例のdpubの1ページ目とgcpmの2ページ目を個別画像でも確認し、番号・囲み・脚注の配置に問題は見られなかった。今回の入力では、更新による出力の変化を認めなかった。

### 14.3 配布を模した依存解決

[verify-trim-distribution.mjs](../scripts/verify-trim-distribution.mjs)で、公開を伴わない最小パッケージを作り、npm packしたtarballを別のプロジェクトへインストールした。依存はremark-parse 8.0.3、そのtrimだけを0.0.3へ指定するoverridesとした。これは依存解決の実験であり、clonoの正式な配布物ではない。

| 条件 | remark-parseから解決したtrim |
|---|---|
| パッケージ作成元でoverridesを使う | 0.0.3 |
| overridesを記載した通常のtarballを利用者側へインストール | 0.0.1 |
| 0.0.3を記録したnpm-shrinkwrap.jsonも含むtarballをインストール | 0.0.1 |

最後のケースでは、tarballにnpm-shrinkwrap.jsonが実際に含まれ、その中のtrimが0.0.3であることまで確認した。今回の最小例とnpm 11.19.1では、それだけで利用者側へ修正版を適用することはできなかった。依存元remark-parseの宣言はtrim 0.0.1のままである。shrinkwrap全般が機能しないと一般化せず、このパッケージの作り方では要件を満たさなかった結果として残す。

最初のshrinkwrap実験はfiles指定の不備でshrinkwrapをtarballへ含めていなかった。内容を点検して不備を修正し、同梱をassertした再実行でも0.0.1となった。最初の失敗だけを方式の評価根拠には使っていない。最終スクリプトは、作成元0.0.3、両インストール先0.0.1という観測と、tarball内容を確認して結果を保存する。

観測JSONはresults/generated/trim-update/distribution-observations.jsonに保存した。生成したパッケージとインストール先も同じ除外対象内に置き、外部への公開は行っていない。

### 14.4 再現方法と結論

調査ディレクトリで更新後のlockfileから依存を準備し、開発用・配布用ビルドごとに20テストを実行する。手順3と同じJDK・Node.jsを使う。

```sh
npm ci --ignore-scripts
node --test scripts/verification.test.mjs scripts/column-followup.test.mjs
node scripts/column-followup.mjs
node scripts/generate-footnotes.mjs
node scripts/build-trim-update-pdfs.mjs
python3 scripts/inspect-pdfs.py
python3 scripts/inspect-column-pdfs.py
node scripts/verify-trim-distribution.mjs
```

ビルドは第8節と第10節のコマンドを使う。既存テストの分離先はprepare-isolation.mjs実行後、分離先でnpm ci --omit=dev --ignore-scriptsを実行して更新する。新しいコラムの分離確認には、同じ分離先へcolumn-followup.mjsをコピーし、そこからconvertを呼ぶ。

[build-trim-update-pdfs.mjs](../scripts/build-trim-update-pdfs.mjs)は既存脚注10例・二章の本2例・コラム追加12例を組版する。比較用の更新前出力はローカル保存であり、再比較する場合は旧構成で生成して別保存してから更新する。PDF生成にはローカル配信とブラウザー起動、配布実験にはnpm通信の許可が必要だった。

修正版への限定更新は、今回の入力・環境では機能と出力の互換性を保ち、trim由来のaudit報告を解消した。一方、利用者側でも修正版が使われる配布方法は未解決である。調査用overridesを正式な配布対策として採用済みとは扱わない。別の配布・固定方法を検討するか、利用範囲を限定して当面許容するかは、この結果をもとに相談する。追加の配布方式や回避処理は実装していない。

## 15. 手順4の合意した方針と依存の復元

確認日：2026-10-17（日本時間）。調査と検証の結果を説明し、開発者の理解を確認したうえで、次の方針に合意した。依存リスクに関する成果物の独立レビューと二人の確認も完了した。ADR・採用環境の仕様への反映と両文書の独立レビュー・二人の確認も終え、手順4を完了した。

### 15.1 採用方針

| 対象 | 合意した方針 |
|---|---|
| 実装言語 | ClojureScriptを基本とし、本体は可能な限りClojureScriptで書く。極端に複雑になる、または実現できない場合はJavaScriptの使用を相談する |
| ビルドと拡張 | shadow-cljsでESMを出力する。利用者のJavaScript拡張はまず.mjsとし、CommonJSは特別な要件が出た場合に再検討する |
| Node.js | 対応範囲は>=24.22.0 <25、開発用の固定版はまず24.22.0とする。最低版は技術的な必要条件を特定した値ではなく、実行確認した版を出発点として選んだ |
| 開発・利用環境 | 開発用JDKとsource mapの補助を許容し、利用者はNode.jsだけで実行できる形を維持する。macOSを最初の動作確認対象とし、Windows・Linuxは後続のCI整備で確認する |
| Markdown処理 | 現行remarkでAST処理し、Markdownへ再出力してVFMでHTML化する。元原稿を保持し、内容と組版上の意味を保てる範囲で、再出力による表記の変化を許容する |
| コラムと脚注 | 追加検証の結果を採用判断の根拠とし、コラム内部を先にHTML化せずMarkdownとしてVFMへ渡す。脚注はdpubを標準とする。gcpmの切り替え機能は今回確定しない |
| 利用範囲 | 自分たちで用意する原稿・設定・拡張をローカルで扱う。第三者入力を自動受付する仕組みは現時点の対象にしない |

個々のライブラリは検証で固定した版を判断の根拠とする。すべての入力や他OSの動作を保証したものではなく、製品機能や公開APIも本資料だけで確定しない。

### 15.2 依存リスクの扱い

trimは当面0.0.1を使い、新しいremark-parseに対応したVFMの公開更新を待つ方針とした。関数への到達は確認できており、処理が極端に遅くなるリスクは残る。自作原稿をローカルで変換する利用条件と、修正版を配布先へ適用するための保守負担を踏まえ、条件付きで許容する。修正版0.0.3の互換性確認は、将来の判断材料として残す。

開発時だけ修正版を使う方針は採用しない。開発と配布で依存をそろえ、修正版での検証結果を0.0.1の安全性の根拠にしない。利用者側に修正版を含める追加調査は、今回は続けない。

残る8件も、第12節で確認した呼び出し方・設定・実行場所を維持する前提で、当面許容する。発生条件に一致しなかったことを、パッケージ全体が安全であるという判断へ広げない。追加の依存差し替えは行わない。

trimの扱いは、VFMの更新時、第三者入力を扱う用途へ広げる時、実際に変換が極端に遅くなった時、一般向け配布の準備時に見直す。残る8件も、対象API・設定・実行場所を変える時や依存更新時に再評価する。現在の対応状態と見直し条件は[後続課題](../../../docs/backlog.md#依存の脆弱性への対応を見直す)で管理する。

### 15.3 復元と確認

調査用package.jsonからtrimのoverridesを削除し、検証前に保存したlockfileを戻した。package.jsonとpackage-lock.jsonは両方とも第14節の更新前の内容とバイト単位で一致した。npm ci --ignore-scriptsで依存を入れ直し、VFMのパーサーが使うtrimが0.0.1であることを確認した。分離先も同じ依存情報で作り直した。

既存10件とコラム追加10件の計20テストが成功し、失敗・スキップは0件だった。既に0.0.1で確認済みの生成結果と、第14節の更新前後で一致した比較結果を保持し、今回の復元ではPDFの再組版は行っていない。

第14節の0.0.3構成を再現する場合は、現在の依存へそのままnpm ciするだけでは更新状態にならない。別の作業先でpackage.jsonへ次の検証用設定を追加し、npm install --ignore-scriptsで解決してから、第14節のビルド・テストを実行する。

```json
"overrides": {
  "remark-parse@8.0.3": { "trim": "0.0.3" }
}
```

作業時の0.0.3のpackage.jsonとlockfileも、除外対象のresults/generated/trim-update/updated-package.jsonとupdated-package-lock.jsonへ保存した。現在の調査用依存は0.0.1であり、この検証用設定を正式採用したものではない。

### 15.4 独立レビューと二人の確認

会話履歴を引き継がない新しいサブエージェントが、依存リスクの調査から更新・配布実験、結論、依存の復元までの未コミット成果物6ファイルを確認した。9件すべての公式アドバイザリと固定済みコードを照合し、発生条件が一致しないという判断を安全性の保証へ広げていないこと、当面許容する合意と記録が整合していることを確認した。

auditの4種類のJSON、旧新lockの変更範囲、現在の依存ファイルと更新前保存版のバイト一致、shrinkwrap同梱tarball内のtrim 0.0.3と利用者側lockの0.0.1を確認した。上流の固定コミットと公開メタデータ、未マージPRの保存資料、backlogの見直し条件も照合した。

現構成で20テストを再実行し、全件成功、失敗・スキップ0件だった。通常入力の経路観測もtrim 10回、Prism coreのみ、documentなしで記録と一致した。保存済みMarkdown・HTML30ファイルと描画PNG40枚は更新前とバイト一致し、代表2ページも個別画像で確認した。

依存の再インストール、CLJSの再ビルド、PDFの再組版、配布実験の再実行、攻撃入力の実行は行っていない。更新状態での実行成功自体は独立して再現せず、現構成の再テストと保存成果物・実験コードの照合で確認した。

重大な問題・修正推奨・任意の改善はいずれもなし。指摘による修正がないため、再レビューは実施していない。独立レビュー結果を共有し、開発者から問題ないとの確認を得た。依存リスクの調査・検証・結論・復元について、独立レビューと二人の確認を完了した。ADRと採用環境の仕様の作成・確認は、後続の第15.5節に記録する。

### 15.5 ADR・仕様の文書化と最終確認

[ADR 0002](../../../docs/decisions/0002-choose-implementation-environment.md)に判断理由と代替案を、[実装環境の仕様](../../../docs/specs/implementation-environment.md)に採用する構成と制約を記録した。日本語をyomiyasuで推敲し、固定した依存・設定との整合、ローカルリンク・見出し参照21件、著作権・ライセンス表示、末尾改行を確認した。

会話履歴を引き継がない新しいサブエージェントが、両文書を合意内容・調査結果・依存情報と照合した。Node.jsの対応範囲と実行確認した版、開発時と利用時の要件、採用した依存と比較だけに使った依存を区別できていることを確認した。未検証のOSや製品への統合を保証していないこと、依存リスクとbacklogの扱い、ローカルリンク21件も確認した。

重大な問題・修正推奨・任意の改善はいずれもなし。指摘による修正がないため、再レビューは実施していない。文書と既存資料の照合によるレビューであり、依存更新、ビルド、テスト、組版、外部情報の再取得は行っていない。

開発者が両文書の内容に問題ないことを確認した。独立レビュー結果の共有後、状態と手順書の更新依頼を受け、手順4の実施・検証・独立レビュー・二人の確認を完了した。手順5以降には着手していない。
