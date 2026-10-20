<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# 採番・参照・目次の調査

[手順書0005](../../docs/plans/0005-investigate-numbering-and-references.md)に基づき、旧要件のT03～T05について、既存機能で満たせる範囲とclonoで補う処理を調べる。

- 現在地：全手順の実施・内部確認完了。外部レビューと作業全体の完了状況は、対応するPRで確認する。
- [責務分担・成立条件・未確認事項・全体の見直し](results/verification.md#12-手順4責務分担と全体の見直し)
- [参照資料と公開パッケージの識別情報](sources.md)
- [候補・検証項目・期待値・環境とリスクの確認](results/verification.md)
- [要件の出発点](../legacy-preprocessor/results/requirements.md#12-要件に結び付けた後続の確認事項)

## 手順1で確認したこと

公式資料の閲覧、公開メタデータの照会、対象バージョンの配布コードの静的読解を行った。配布アーカイブのSHA-512をレジストリのintegrityと照合し、読解対象のローカルファイルがアーカイブ内の内容と一致することを確認した。

Markdown変換、CSSの実行、組版、PDF確認、依存の導入・更新は行っていない。資料から候補を挙げた段階であり、製品での採用判断や動作保証ではない。

日本語文書は`yomiyasu`で見直す。第三者の資料・コードは出典と読解結果を記録し、本文やコード全体を転載しない。

## 手順2の検証と再現方法

手順2の結果は[検証結果の第9節](results/verification.md#9-手順2通常出力での検証結果)に記載した。補助なしの構成、開始値・ID・表キャプションを補う構成、章順・目次階層・区切り文字・ID配置を変える構成、単一原稿の対照を含む8構成を組版した。

このディレクトリで次を実行する。`package.json`とlockfileは既存調査環境を基準とし、外部レビュー対応で直接使用する`@vivliostyle/jsdom`の宣言だけを追加した。既存パッケージの解決内容は維持し、製品の依存とは分離している。`npm ci`は依存の導入、`build.mjs`はローカル配信とブラウザー起動を行う。

### PythonとPopplerの準備

実測に使った版はPython 3.12.14、pypdf 6.10.0、pdfplumber 0.11.9、Pillow 12.3.0、Poppler 25.09.1である。Pythonの直接依存は[requirements.txt](requirements.txt)へ固定した。Python 3.12を用意した環境で、このディレクトリから次を実行する。

```sh
python3.12 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
source .venv/bin/activate
```

有効化後は、以下の`python3`が仮想環境の実行ファイルを指す。[venvの公式説明](https://docs.python.org/3.12/library/venv.html)も参照する。requirements.txtは直接依存の版を固定するもので、推移的依存やOS全体を固定するlockfileではない。

Popplerの`pdftotext`と`pdftoppm`は別途必要になる。Homebrewを利用できるmacOSでの導入例は次のとおりである。

```sh
brew install poppler
pdftotext -v
pdftoppm -v
```

[Homebrewの導入案内](https://formulae.brew.sh/formula/poppler)は現行版を導入するため、実測版25.09.1の再導入を保証する手順ではない。別の版や環境では、版を記録して検査と表示確認をやり直す。Python・Popplerとも、別環境での導入と同一結果は未検証である。

### 生成と検査

```sh
npm ci --ignore-scripts --no-audit --no-fund
node scripts/check-environment.mjs
node scripts/prepare.mjs
node scripts/build.mjs
python3 scripts/verify-pdfs.py --check-regressions
python3 scripts/render-pdfs.py
```

今回の調査は上記の版が導入済みのCodex同梱Pythonで実行し、`python3`をその実行ファイルへ読み替えた。仮想環境の導入例とは区別する。Node.js側の依存へPythonのライブラリは追加していない。

`prepare.mjs`は固定した検証原稿と参照一覧を再作成する。`build.mjs b-base`のように構成名を指定すれば、その構成だけを再生成できる。PDFは`results/pdf/`、中間Markdown・HTML・CLI設定・目次・個別の照合結果・ページ画像は`results/generated/`へ保存し、Git管理から除外する。

集計結果は[step2-observations.json](results/step2-observations.json)、環境と依存定義のハッシュは[environment.json](results/environment.json)、PDF閲覧時の確認は[viewing-observations.json](results/viewing-observations.json)に残す。検査用PythonはPDFから文字列とリンク注釈を調べる。閲覧操作と画像の目視確認は、その実行だけで自動的に再現したことにはならない。

環境JSONは調査時点の記録として保持する。外部レビュー対応後の`check-environment.mjs`は、既存依存の定義・解決内容とJSDOMの直接宣言を照合し、通常・`--page-counters`・`--headings`それぞれの結果を`environment-current.json`・`step2a-environment-current.json`・`step3-environment-current.json`へ保存する。

目次は、生成物から独立した期待項目と順序を使い、番号・本文・ページ番号を行単位で照合する。各行のPDF注釈はファイル名と見出しIDまで確認する。同名IDへの参照と単一原稿の参照も自動判定する。`--check-regressions`を付けると、基準構成から読み取った検査データのコピーに欠落・重複・順序変更・章番号の誤り・同ページ内の誤リンクを与え、5種類とも検出できることを確認する。保存済みのHTML・PDFは変更しない。

手順3の見出し差し替えは、下記の専用構成で検証した。

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

## 手順3：見出し差し替えの検証と再現方法

[検証結果の第11節](results/verification.md#11-手順3見出しの差し替えとの組み合わせ)に成立条件と不足を記載した。文字を残すH1～H3の装飾、本文文字を保持してH1を画像で見せる構成、文字を消す方法・目次・div置換の対照を含む16構成を検証した。標準出力の対照は手順2Aの5構成を使用する。

手順2・2Aの生成物を用意した後、このディレクトリで次を実行する。

```sh
node scripts/check-environment.mjs --headings
node scripts/build.mjs --headings
node scripts/inspect-headings.mjs
python3 scripts/verify-pdfs.py --headings --check-regressions
python3 scripts/render-pdfs.py --headings
```

`fixtures/heading-profiles.json`が構成一覧、`heading-overrides.css`が差し替え用CSS、`heading-images/`が自作SVGである。元原稿は共通で、VFM出力後のHTMLを調査用に加工する。画像構成の文字目次には、`scripts/toc-text.mjs`のCLIフックを使う。フック付きの構成だけは、関数を渡すために生成した`config.mjs`をCLIへ指定する。関数を使わない構成は従来のJSON設定を維持する。

集計・HTML構造・環境・閲覧結果をそれぞれ`results/step3-observations.json`・`step3-structure.json`・`step3-environment.json`・`step3-viewing-observations.json`に保存した。成立する10構成と不足を調べる6構成を区別し、対照で参照が欠けることも検査する。文字列が一致しても目次に画像が残る場合があるため、HTML内の画像・隠し文字用spanも確認する。画像や実クリックの目視確認は、コマンドを実行するだけで再現したことにはならない。

## 手順4：全体の整理

[検証結果の第12節](results/verification.md#12-手順4責務分担と全体の見直し)に、T03～T05と全検証項目の対応、既存機能に任せる処理と補助、条件付きの希望、残る確認とその時点をまとめた。第1～8節は調査前の候補、第9～11節は各手順の実測として保持している。構成を見直し、現時点ではリファクタリングを見送ると判断した。新しい実験やコード変更は行っていない。

## 資料の追跡と表示

資料調査は`sources.md`に記載したURLとパッケージ内のパスから追える。取得したアーカイブは一時ファイルで、継続参照先としては使わない。必要なら公式レジストリから再取得し、記録したintegrityで照合する。

コメントを書けない`package.json`の表示は、Copyright 2026 HASEBA Junya (7pairs)、Apache-2.0とする。lockfileはnpmが生成した内容を維持する。調査用JSONは生成元のコードで著作権表示とライセンスを付ける。実行ログは調査コードから生成した実行記録であり、依存のコード本文を収録するものではない。
