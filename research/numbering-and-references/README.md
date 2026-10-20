<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# 採番・参照・目次の調査

[手順書0005](../../docs/plans/0005-investigate-numbering-and-references.md)に基づき、旧要件のT03～T05について、既存機能で満たせる範囲とclonoで補う処理を調べる。

- 現在地：手順2Aの実施・検証・独立レビュー・二人の確認は完了。手順3ではページ側の採番方式を使う。手順3・4は未着手。
- [参照資料と公開パッケージの識別情報](sources.md)
- [候補・検証項目・期待値・環境とリスクの確認](results/verification.md)
- [要件の出発点](../legacy-preprocessor/results/requirements.md#12-要件に結び付けた後続の確認事項)

## 手順1で確認したこと

公式資料の閲覧、公開メタデータの照会、対象バージョンの配布コードの静的読解を行った。配布アーカイブのSHA-512をレジストリのintegrityと照合し、読解対象のローカルファイルがアーカイブ内の内容と一致することを確認した。

Markdown変換、CSSの実行、組版、PDF確認、依存の導入・更新は行っていない。資料から候補を挙げた段階であり、製品での採用判断や動作保証ではない。

日本語文書は`yomiyasu`で見直す。第三者の資料・コードは出典と読解結果を記録し、本文やコード全体を転載しない。

## 手順2の検証と再現方法

手順2の結果は[検証結果の第9節](results/verification.md#9-手順2通常出力での検証結果)に記載した。補助なしの構成、開始値・ID・表キャプションを補う構成、章順・目次階層・区切り文字・ID配置を変える構成、単一原稿の対照を含む8構成を組版した。

このディレクトリで次を実行する。`package.json`とlockfileは既存調査環境から同じ組を引き継ぎ、製品の依存とは分離している。`npm ci`は依存の導入、`build.mjs`はローカル配信とブラウザー起動を行う。

```sh
npm ci --ignore-scripts --no-audit --no-fund
node scripts/check-environment.mjs
node scripts/prepare.mjs
node scripts/build.mjs
python3 scripts/verify-pdfs.py --check-regressions
python3 scripts/render-pdfs.py
```

Python側には`pypdf`・`pdfplumber`・Pillow、外部コマンドにはPopplerの`pdftotext`・`pdftoppm`を使う。今回のPython実行環境はCodexの同梱ランタイムであり、`python3`をその実行ファイルへ読み替えて実行した。Node.js側の依存へPythonのライブラリは追加していない。

`prepare.mjs`は固定した検証原稿と参照一覧を再作成する。`build.mjs b-base`のように構成名を指定すれば、その構成だけを再生成できる。PDFは`results/pdf/`、中間Markdown・HTML・CLI設定・目次・個別の照合結果・ページ画像は`results/generated/`へ保存し、Git管理から除外する。

集計結果は[step2-observations.json](results/step2-observations.json)、環境と依存定義のハッシュは[environment.json](results/environment.json)、PDF閲覧時の確認は[viewing-observations.json](results/viewing-observations.json)に残す。検査用PythonはPDFから文字列とリンク注釈を調べる。閲覧操作と画像の目視確認は、その実行だけで自動的に再現したことにはならない。

目次は、生成物から独立した期待項目と順序を使い、番号・本文・ページ番号を行単位で照合する。各行のPDF注釈はファイル名と見出しIDまで確認する。同名IDへの参照と単一原稿の参照も自動判定する。`--check-regressions`を付けると、基準構成から読み取った検査データのコピーに欠落・重複・順序変更・章番号の誤り・同ページ内の誤リンクを与え、5種類とも検出できることを確認する。保存済みのHTML・PDFは変更しない。

手順3の見出し差し替え例はまだ作成・検証していない。

## 手順2A：ページ側カウンターの検証と再現方法

[検証結果の第10節](results/verification.md#10-手順2aページ側のカウンター方式の追加検証)に結果を記載した。今回の1原稿1章の構成では、ページ側で本文と付録を別々に数えることにより、原稿順から算出した章開始値の注入を省けた。他のID・キャプションの補助は維持している。

上記の手順2の生成物を用意した後、このディレクトリで次を実行する。既存の結果を比較用に残し、`p-`で始まる5構成を別に生成する。

```sh
node scripts/check-environment.mjs --page-counters
node scripts/build.mjs --page-counters
python3 scripts/verify-pdfs.py --page-counters --check-regressions
python3 scripts/render-pdfs.py --page-counters
```

入力は`fixtures/page-counter-profiles.json`と`fixtures/page-counters.css`で、原稿は手順2と共通である。`build.mjs --page-counters p-base`のように構成を絞ることもできる。原稿末尾には、そのページ自身の章カウンターを確認する検査表示を追加する。目次・リンクの検査に加え、生成HTML・CSSに原稿ごとの章開始値がないこと、章先頭より後のページでも章の値を維持することを自動判定する。

集計は[step2a-observations.json](results/step2a-observations.json)、環境と既存PDFのハッシュ確認は[step2a-environment.json](results/step2a-environment.json)、最終版の実クリック確認は[step2a-viewing-observations.json](results/step2a-viewing-observations.json)に保存した。PDF・中間生成物は従来と同じ保存先のGit管理外に置く。結果の内容確認と独立レビューを終え、手順3ではページ側の採番方式を使うことに合意した。

## 資料の追跡と表示

資料調査は`sources.md`に記載したURLとパッケージ内のパスから追える。取得したアーカイブは一時ファイルで、継続参照先としては使わない。必要なら公式レジストリから再取得し、記録したintegrityで照合する。

コメントを書けない`package.json`の表示は、Copyright 2026 HASEBA Junya (7pairs)、Apache-2.0とする。lockfileはnpmが生成した内容を維持する。調査用JSONは生成元のコードで著作権表示とライセンスを付ける。実行ログは調査コードから生成した実行記録であり、依存のコード本文を収録するものではない。
