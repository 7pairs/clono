<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# 索引の既存機能調査

対象は[手順書0006](../../docs/plans/0006-investigate-book-index.md)のT08～T10。手順1・2は完了。2026-10-23に手順3の追加調査と、比較実験計画の具体化・合意・独立レビュー・修正・再レビュー・文書反映を実施した。比較実験と手順4A以降は未着手。規格全体の精査と候補実装の適合判定は未実施。

- [参照資料](sources.md)：公式資料、対象版の公開コード、規格情報、外部実装例。
- [調査結果と検証計画](results/verification.md)：既存機能の対応範囲、照合処理の候補、合意した手順2・3・4A・4Bへの案内、未確認事項。
- [手順3の追加調査](results/step3-research.md)：漢字クラス、文字の扱い、Intlの指定範囲、計画具体化の根拠となった提案。
- [手順3の比較実験計画](results/step3-plan.md)：合意・レビュー済みの比較方法、入力と期待値、検査項目、環境確認と実施順序。実験は未着手。

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

## ファイルとライセンス表示

この調査で作成したコード・入力・結果はCopyright 2026 HASEBA Junya (7pairs)、SPDX-License-Identifier: Apache-2.0とする。第三者の実装コードや規格本文は収録していない。依存パッケージにはそれぞれのライセンスが適用される。

| ヘッダを持たないファイル | 表示と理由 |
|---|---|
| fixtures/conflict-a.md、conflict-b.md、missing.md | 上記の著作権・ライセンスを適用。診断の行番号を固定するためヘッダを省略 |
| fixtures/expected.json、package.json、package-lock.json | 上記の著作権・ライセンスを適用。JSONのため本表で表示 |
| results/environment.json、step2-conversion.json、step2-pdf.json | 上記の著作権・ライセンスを適用。調査コードが生成する観測記録 |
| results/python-freeze.txt、results/logs/*.txt | 上記の著作権・ライセンスを適用。ツールが生成する環境・検証記録 |

生成PDFと中間ファイルもこの調査の自作入力・処理から生成するが、リポジトリには収録しない。利用者の原稿や生成した本一般へのライセンス適用を意味しない。
