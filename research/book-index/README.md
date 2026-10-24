<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# 索引の既存機能調査

対象は[手順書0006](../../docs/plans/0006-investigate-book-index.md)のT08～T10。手順1～3・4Aは、実施・検証・独立レビュー・必要な修正と再レビュー・二人の確認を終え、完了した。4Bの検証計画も内容確認・レビュー済み。4BはB01で停止後、計画修正案の合意・独立レビュー・修正・再レビューを終えて文書に反映した。追加調査・試作と6構成の再実行は未着手。4B実施成果物の独立レビューは未実施で、5も未着手。標準APIの照合に関する制約と製品での扱いは手順5へ引き継ぐ。規格全体の精査と候補実装の適合判定は未実施。

- [参照資料](sources.md)：公式資料、対象版の公開コード、規格情報、外部実装例。
- [調査結果と検証計画](results/verification.md)：既存機能の対応範囲、照合処理の候補、合意した手順2・3・4A・4Bへの案内、未確認事項。
- [手順3の追加調査](results/step3-research.md)：漢字クラス、文字の扱い、Intlの指定範囲、計画具体化の根拠となった提案。
- [手順3の比較実験計画](results/step3-plan.md)：合意・レビュー済みの比較方法、入力と期待値、検査項目、環境確認と実施順序。
- [手順3の実験結果](results/step3.md)：R14組・K6組の実測、12件の検査、情報保持、規格との差異、負担と残る制約。
- [手順4Aの結果](results/step4a.md)：既存4PDFのページ対応、28リンクから24リンクへの集約候補、方式と負担。
- [手順4Bの実施結果](results/step4b.md)：B01の2回の組版、名前付き参照先の欠落、停止理由と未確認事項。
- [手順4Bの検証計画](results/step4b-plan.md)：停止後の合意・レビューを反映した追加調査・試作と6構成の再実行計画。次の着手範囲は保存済みB01での追加調査・試作。

手順1は資料調査のみ。手順2では、固定版の専用環境でAST補助、索引生成、4構成の組版とPDF検査を実施した。[手順2の結果](results/step2.md)から根拠を辿れる。

手順1の根拠は参照資料のURLと対象版から再確認できる。取得した第三者のコード・規格本文はリポジトリへ収録していない。公式サイトがメンテナンス中と分かった場合は、そのサイトの確認を保留し、対象と不足を記録して後日改めて確認する。

2026-10-23、JISCで本文を閲覧し、主要条項を確認した。ソート順序とその規格上の根拠を説明する方針とし、根拠を確定できた比較には一致・不一致を付ける。読み単体と読み→表記を別々に検証し、漢字クラス等の追加調査後に検証計画を具体化する。[追加確認と計画変更の記録](results/verification.md#11-jis本文閲覧と手順3の計画変更)を参照する。

## 手順2の再現

以下はこのディレクトリを作業場所にして実行する。Node.js 24.22.0、Python 3.12、Popplerを用意する。今回のPythonはCodex付属の3.12.14を使った。`python3.12`がPATHにない場合は、その実行ファイルのパスを指定する。

```sh
cd /Users/jun-haseba/GitHub/clono/research/book-index
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
node --test scripts/index.test.mjs scripts/collation.test.mjs
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

以下は停止時の実装を再現・確認する手順であり、修正後の計画に対応するコードは未実装。追加調査・試作は[検証計画の第6節](results/step4b-plan.md#6-再開前の追加調査試作)、保存先の分離と6構成の再実行は[第7節](results/step4b-plan.md#7-6構成の再実行と記録の保持)に従う。既存スクリプトは固定名の記録を上書きするため、追加調査や再実行でそのまま使わない。

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

## ファイルとライセンス表示

この調査で作成したコード・入力・結果はCopyright 2026 HASEBA Junya (7pairs)、SPDX-License-Identifier: Apache-2.0とする。第三者の実装コードや規格本文は収録していない。依存パッケージにはそれぞれのライセンスが適用される。

| ヘッダを持たないファイル | 表示と理由 |
|---|---|
| fixtures/conflict-a.md、conflict-b.md、missing.md | 上記の著作権・ライセンスを適用。診断の行番号を固定するためヘッダを省略 |
| fixtures/expected.json、package.json、package-lock.json | 上記の著作権・ライセンスを適用。JSONのため本表で表示 |
| fixtures/collation-cases.json、collation-expected.json | 上記の著作権・ライセンスを適用。JSONのため本表で表示。自作の比較入力と、計画から転記した期待値・根拠 |
| fixtures/page-aggregation-expected.json | 上記の著作権・ライセンスを適用。JSONのため本表で表示。固定ページ配置から手書きした集約の期待値 |
| fixtures/two-pass-expected.json | 上記の著作権・ライセンスを適用。JSONのため本表で表示。4B計画から固定した6構成の期待値 |
| results/step4b-results.json、step4b-stop.json | 上記の著作権・ライセンスを適用。調査コードが生成する実行・停止後の観測記録 |
| results/environment.json、step2-conversion.json、step2-pdf.json | 上記の著作権・ライセンスを適用。調査コードが生成する観測記録 |
| results/step3-comparisons.json | 上記の著作権・ライセンスを適用。調査コードが生成する環境・比較・判定の記録 |
| results/step4a-pages.json | 上記の著作権・ライセンスを適用。調査コードが生成する環境・ページ対応・集約候補の記録 |
| results/python-freeze.txt、results/logs/*.txt | 上記の著作権・ライセンスを適用。ツールが生成する環境・検証記録 |

生成PDFと中間ファイルもこの調査の自作入力・処理から生成するが、リポジトリには収録しない。利用者の原稿や生成した本一般へのライセンス適用を意味しない。
