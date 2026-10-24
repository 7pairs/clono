<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# 索引の既存機能調査

対象は[手順書0006](../../docs/plans/0006-investigate-book-index.md)のT08～T10。手順1～3・4A・4B・5の全手順の実施・内部確認完了。手順5の独立レビューは指摘なしとなり、リファクタリング見送りを含む二人の確認を終えた。外部レビューと作業全体の完了状況は対応するPRで確認する。読み→表記を採用候補とし、確認済みのJISとの差異は許容する。ページ集約の今回の製品実装は保留。製品の詳細仕様と、規格全体の精査・適合判定は未実施。

- [手順5の全体整理と責務分担](results/step5.md)：要件ごとの到達点、合意した照合と同一性、集約の実装保留、構造の見直し、後続の判断地点、独立レビューと二人の完了確認。
- [参照資料](sources.md)：公式資料、対象版の公開コード、規格情報、外部実装例。
- [調査結果と検証計画](results/verification.md)：既存機能の対応範囲、照合処理の候補、合意した手順2・3・4A・4Bへの案内、未確認事項。
- [手順3の追加調査](results/step3-research.md)：漢字クラス、文字の扱い、Intlの指定範囲、計画具体化の根拠となった提案。
- [手順3の比較実験計画](results/step3-plan.md)：合意・レビュー済みの比較方法、入力と期待値、検査項目、環境確認と実施順序。
- [手順3の実験結果](results/step3.md)：R14組・K6組の実測、12件の検査、情報保持、規格との差異、負担と残る制約。
- [手順4Aの結果](results/step4a.md)：既存4PDFのページ対応、28リンクから24リンクへの集約候補、方式と負担。
- [手順4Bの実施結果](results/step4b.md)：B01の2回の組版、名前付き参照先の欠落、停止理由と未確認事項。
- [手順4Bの追加調査・試作結果](results/step4b-followup.md)：保存済みB01の全7箇所のページ確認、8検査、方式の負担と限界、独立レビューと二人の方針確認。
- [手順4Bの6構成再実行結果](results/step4b-rerun.md)：計12回の組版、79リンク・84文脈の自動検査、最終28ページの目視と負担、独立レビューと二人の完了確認。
- [手順4Bの検証計画](results/step4b-plan.md)：追加調査のレビューと合意を反映した6構成の再実行計画。保存先、接続部分の検査、12回の組版と28ページの目視確認を具体化。

手順1は資料調査のみ。手順2では、固定版の専用環境でAST補助、索引生成、4構成の組版とPDF検査を実施した。[手順2の結果](results/step2.md)から根拠を辿れる。

手順1の根拠は参照資料のURLと対象版から再確認できる。取得した第三者のコード・規格本文はリポジトリへ収録していない。公式サイトがメンテナンス中と分かった場合は、そのサイトの確認を保留し、対象と不足を記録して後日改めて確認する。

2026-10-23、JISCで本文を閲覧し、主要条項を確認した。ソート順序とその規格上の根拠を説明する方針とし、根拠を確定できた比較には一致・不一致を付ける。その後、追加調査と読み単体・読み→表記の比較実験を実施した。手順5では、読み→表記を採用候補とし、既知の差異の許容を確認した。[追加確認と計画変更の記録](results/verification.md#11-jis本文閲覧と手順3の計画変更)を参照する。

## 手順2の再現

以下のコマンドはリポジトリのルートから実行する。先頭の`cd`でこのディレクトリへ移動し、以降の作業を行う。Node.js 24.22.0、Python 3.12、Popplerを用意する。今回のPythonはCodex付属の3.12.14を使った。`python3.12`がPATHにない場合は、その実行ファイルのパスを指定する。

```sh
cd research/book-index
python3.12 -m venv .venv
.venv/bin/python -m pip install -r results/python-freeze.txt
npm ci --ignore-scripts --no-audit --no-fund
node --test scripts/index.test.mjs
node scripts/build.mjs
.venv/bin/python scripts/verify-pdfs.py
.venv/bin/python scripts/verify-pdfs.test.py
.venv/bin/python scripts/render-pdfs.py
```

直接依存の基準はrequirements.txt、今回の推移依存を含む実版はresults/python-freeze.txtに記録している。Node依存は前回の調査と同じlockfileを使う。上記のnpm ciではインストールスクリプトとauditを実行しない。組版にはCLIが利用できるChromeが必要で、初回にダウンロードが必要になる場合がある。CLIのローカルサーバーとブラウザーを起動できる環境で実行する。

PDFは`results/pdf/`、再出力Markdown・HTML・収集結果・設定・PNGは`results/generated/<構成名>/`、ログは`results/logs/`へ出力する。構成名はbase-2、swapped-2、base-3、base-4。目視用の一覧画像は各構成のcontact.png、索引の画像はimages/page-4.png。

期待値はfixtures/expected.jsonに手書きし、収集処理やPDF抽出結果から生成しない。同一ページの飛び先の置換・交換はverify-pdfs.test.pyで検査する。PDFをメモリー上で読み取り、名前付き参照先の座標だけを変えて抽出から判定まで実行する。PDF・結果ファイルは変更しない。検査の成功後も、PNGを開いて表示を確認する。

## 手順3の再現

手順2で用意したNode.js 24.22.0と固定済みの依存環境を使い、このディレクトリで実行する。追加依存やPython、組版処理は不要。

```sh
node --test scripts/index.test.mjs scripts/collation.test.mjs scripts/compare-collation.test.mjs
node scripts/compare-collation.mjs
```

比較結果はresults/step3-comparisons.jsonへ保存する。実行前にNode版と日本語ロケールへの解決を確認し、不一致なら差異と未開始の状態を保存して終了する。入力・独立した期待値・環境・判定の扱いは[実験結果](results/step3.md)を参照する。手順2のindex.mjs・既存テスト・生成物は変更していない。

## 手順4Aの再現

手順4Aの再現は、手順2の既存PDFと専用Python環境を用意し、以下を実行する。手順4A自体ではHTML・PDFを再生成しない。入力と根拠、4Bへ進む前の確認は[4Aの結果](results/step4a.md)を参照する。

```sh
.venv/bin/python scripts/page-aggregation.test.py
.venv/bin/python scripts/aggregate-pages.py
```

## 手順4Bの再現と停止後の確認

以下は停止時の実装を再現・確認する手順であり、修正後の計画に対応するコードは未実装。追加調査・試作は[検証計画の第6節](results/step4b-plan.md#6-再開前の追加調査試作)、保存先の分離と6構成の再実行は[第7節](results/step4b-plan.md#7-6構成の再実行計画)に従う。既存スクリプトは固定名の記録を上書きするため、追加調査や再実行でそのまま使わない。

手順2の固定環境で、以下を実行する。組版にはローカルサーバーとChromeの起動権限が必要になる。

```sh
node --test scripts/two-pass.test.mjs
.venv/bin/python scripts/two-pass.test.py
.venv/bin/python scripts/page-aggregation.test.py
.venv/bin/python scripts/build-two-pass.py
```

build-two-pass.pyは全6構成を順に実行するが、今回の環境ではB01の追加組版後に名前付き参照先の欠落で失敗終了する。B02以降へ進めたり、成功するまで再組版したりしない。実行開始時に前回の結果を未完了記録で置き換え、失敗時はfailed-not-finalとして保存する。現在の失敗結果は、全構成の成功記録ではない。

その失敗結果とB01の2PDFがある状態で、読取り専用の診断を実行できる。

```sh
.venv/bin/python scripts/inspect-two-pass-stop.py
pdftoppm -scale-to 1000 -png results/pdf/step4b-B01-final.pdf results/generated/step4b/B01/page
```

PDFはresults/pdf/step4b-B01-first.pdfとstep4b-B01-final.pdf、入力・生成HTML・構成別設定はresults/generated/step4b/B01/に置く。期待値はfixtures/two-pass-expected.jsonに計画から固定し、集約処理やPDF抽出結果から作成しない。結果の限界と相談事項は[4Bの実施結果](results/step4b.md)を参照する。

## 手順4Bの追加調査・試作の再現

手順2で用意した固定Python環境と、停止時のB01の2PDF・生成HTML・記録、固定版のViewer・CLIパッケージを使う。新たな組版は行わない。

```sh
.venv/bin/python scripts/body-context.test.py
.venv/bin/python scripts/page-aggregation.test.py
.venv/bin/python scripts/probe-body-context.py
```

結果はresults/step4b-followup/body-context.jsonへ保存する。開始時にこの試作の前回結果を未完了状態へ置き換え、失敗時はfailed-not-finalとする。停止時のPDF・JSON・ログ・生成HTML・CSSは読取りだけを行い、ハッシュを照合する。初回・最終PDFの全7文脈を本文全ページから探し、独立した固定ページとページ不変を照合する。本文Bの文脈の重なりは許容し、同じIDに開始候補が複数あれば診断して停止する。

文字や位置情報への負の対照と参照先の座標交換は、実PDFから読み取った情報をメモリー上で変更する。PDFは書き換えない。ソースの根拠は、インストール済みの固定版に同梱されたソースマップから再確認する。全体の結果と限界は[追加調査・試作結果](results/step4b-followup.md)を参照する。再実行計画はレビュー・文書反映済みで、実装と6構成の組版はコミットと着手依頼を受けて進む。停止時のスクリプトは再実行計画への対応前であり、そのまま6構成の再実行に使わない。再実行用の入口は次節を参照する。

## 手順4Bの6構成再実行の再現

既存の固定環境と停止時・追加調査の成果物を用意し、このディレクトリから実行する。組版にはCLIのローカルサーバーとChromeの起動権限が必要になる。

```sh
.venv/bin/python scripts/rerun-inspection.test.py
.venv/bin/python scripts/rerun-state.test.py
node --test scripts/two-pass.test.mjs
.venv/bin/python scripts/page-aggregation.test.py
.venv/bin/python scripts/body-context.test.py
.venv/bin/python scripts/build-two-pass-rerun.py
.venv/bin/python scripts/render-two-pass-rerun.py
```

実行記録はresults/step4b-rerun/result.json、ログと検査記録は同じディレクトリ、PDFはresults/pdf/step4b-rerun/、生成物とPNGはresults/generated/step4b-rerun/構成ID/へ出力する。再現実行は再実行用の前回結果を置き換えるが、停止時と追加調査の成果物は上書きせずハッシュを照合する。入力読取り前に未完了状態を書き、失敗時は診断と実施済み範囲を残す。全6構成の自動検査に成功した場合だけverified-automaticとする。

各構成の初回と追加組版は1回ずつで、正常系は計12回。本文文脈による全7箇所の検査と、残したリンクの名前付き参照先の検査を接続する。省いた名前付き参照先の欠落だけでは失敗にしない。表示番号・本文文脈・参照先・ノンブル・ページ数・列配置に不一致があれば停止し、組版を反復しない。

画像化は自動検査成功とPDFのハッシュを確認してから行う。全28ページを目視し、確認結果を独立レビューの状態と区別して記録する。今回の目視記録はresults/step4b-rerun/visual-review.jsonに残したもので、再現実行で新しく作ったPDFの目視を代行しない。[再実行結果](results/step4b-rerun.md)から各記録を参照する。

## PRレビュー対応後の検証

[PR #11のレビュー](https://github.com/7pairs/clono/pull/11)を受け、再現手順のパス、Pythonの文字コード指定、比較実験の失敗時の状態保存を修正した。補助モジュールとテストも含め、テキストファイルの読み書きにはUTF-8を明示する。調査の結論と固定期待値は変更していない。対応と検証結果は[レビュー対応の記録](results/verification.md#27-prレビュー対応と関連箇所の検証)を参照する。

追加指摘への対応として、`verify-pdfs.py`は期待値の読取り前に`step2-pdf.json`を`running-not-final`へ置き換え、全検査成功後だけ`verified`を保存する。途中で失敗した場合は未完了状態が残る。`aggregate-pages.py`は、状態が明示された結果では`verified`だけを受け入れる。状態のない過去形式の保存結果には、従来のPDFハッシュ・参照等の検査を適用する。詳細は[追加指摘の対応記録](results/verification.md#28-pdf検査失敗時の成功記録の無効化)を参照する。

保存済みの結果JSONに記録したコードのハッシュは、修正前の実装を示す。今回の修正に合わせて過去のハッシュや時間を更新しない。当時のコードは、修正前コミット`c00d607`のGit履歴から確認できる。現在のコードで実験を再実行する場合は、必要な入力・保存済み生成物をそろえた別の作業場所を使い、結果を当時の記録と区別する。`audit-collation.py`はコードのハッシュも照合するため、現在のコードと過去の比較結果を混在させると失敗する。

以下はこのディレクトリから実行する。組版や保存済み結果の更新を伴わず、通常環境と非UTF-8環境でテストする。今回の検証環境はmacOS・Python 3.12.14・Node.js 24.22.0。Windows等の対応を確認したものではない。

```sh
node --test scripts/*.test.mjs
for test_file in scripts/*.test.py; do
  .venv/bin/python "$test_file" || exit 1
  LC_ALL=C PYTHONUTF8=0 .venv/bin/python "$test_file" || exit 1
done
```

非UTF-8環境の起動テストは、両方の組版スクリプトを補助モジュールの読込みから起動し、入力JSONの読取り、日本語の子プロセス出力、失敗診断の保存を確認する。組版開始前に意図的に停止するため、非UTF-8環境での新しいPDF生成まで検証したものではない。

## ファイルとライセンス表示

この調査で作成したコード・入力・結果はCopyright 2026 HASEBA Junya (7pairs)、SPDX-License-Identifier: Apache-2.0とする。第三者の実装コードや規格本文は収録していない。依存パッケージにはそれぞれのライセンスが適用される。

| ヘッダを持たないファイル | 表示と理由 |
|---|---|
| fixtures/conflict-a.md、conflict-b.md、missing.md | 上記の著作権・ライセンスを適用。診断の行番号を固定するためヘッダを省略 |
| fixtures/expected.json、package.json、package-lock.json | 上記の著作権・ライセンスを適用。JSONのため本表で表示 |
| fixtures/collation-cases.json、collation-expected.json | 上記の著作権・ライセンスを適用。JSONのため本表で表示。自作の比較入力と、計画から転記した期待値・根拠 |
| fixtures/page-aggregation-expected.json | 上記の著作権・ライセンスを適用。JSONのため本表で表示。固定ページ配置から手書きした集約の期待値 |
| fixtures/two-pass-expected.json | 上記の著作権・ライセンスを適用。JSONのため本表で表示。4B計画から固定した6構成の期待値 |
| results/step4b-rerun/*.json、results/step4b-rerun/*.txt | 上記の著作権・ライセンスを適用。再実行の観測・画像化・目視・ツールの検査記録 |
| results/step4b-followup/body-context.json、results/step4b-followup/*.txt | 上記の著作権・ライセンスを適用。試作が生成する観測結果とツールの検証記録 |
| results/step4b-results.json、step4b-stop.json | 上記の著作権・ライセンスを適用。調査コードが生成する実行・停止後の観測記録 |
| results/environment.json、step2-conversion.json、step2-pdf.json | 上記の著作権・ライセンスを適用。調査コードが生成する観測記録 |
| results/step3-comparisons.json | 上記の著作権・ライセンスを適用。調査コードが生成する環境・比較・判定の記録 |
| results/step4a-pages.json | 上記の著作権・ライセンスを適用。調査コードが生成する環境・ページ対応・集約候補の記録 |
| results/python-freeze.txt、results/logs/*.txt | 上記の著作権・ライセンスを適用。ツールが生成する環境・検証記録 |

生成PDFと中間ファイルもこの調査の自作入力・処理から生成するが、リポジトリには収録しない。利用者の原稿や生成した本一般へのライセンス適用を意味しない。
