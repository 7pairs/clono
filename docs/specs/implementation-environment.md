<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# clonoの実装環境

- 状態：合意済み（独立レビュー・二人の確認完了）

## 1. 目的と範囲

本文書は、clonoの実装言語、実行環境、ビルド方法、Markdown処理の構成と制約を定める。選定理由と見送った案は[ADR 0002](../decisions/0002-choose-implementation-environment.md)、実行確認の根拠は[検証結果](../../research/implementation-environment/results/verification.md)を参照する。

利用者の原稿・設定・JavaScript拡張は、自分たちで用意した信頼するものをローカルで扱う前提とする。第三者入力の自動受付は対象にしない。

製品機能の詳細、独自記法、設定形式、公開する拡張API、PRレビューツール、配布・リリース方法は本書では確定しない。正式な開発コマンド、テスト、REPL、CIの構成は[開発環境の仕様](development-environment.md)を参照する。調査用のコードやコマンドも、そのまま製品へ固定しない。

## 2. 利用者と開発者の環境

### 2.1 利用者

Node.jsの対応範囲は`>=24.22.1 <25`とする。最低対応バージョンは、セキュリティ対応として24.22.0から24.22.1へ引き上げた。24.22.1では、ローカルのmacOS環境で製品・開発基盤の動作を確認した。実行環境・結果・レビュー状況は[更新の手順書](../plans/0007-update-nodejs-for-security.md)の手順3を参照する。対応範囲内の各バージョンを検証済みとは扱わない。最低対応バージョンは、必要な機能の導入バージョンを特定したものではない。

利用者にはビルド済みのJavaScriptを提供し、JDK・shadow-cljs・ClojureScriptのトランスパイル環境を要求しない。JavaScript拡張も.mjsファイルを用意すれば利用できる構成とする。実行に必要なnpm依存の具体的な配布・インストール方法は後続で決める。

macOSを最初の動作確認対象とする。Windows・Linuxへの対応を目指すが、今回の実行確認には含めていない。3OSのCI設定は実装済みであり、GitHub Actions上での実行結果は[手順書0007](../plans/0007-update-nodejs-for-security.md)に対応するPRで確認する。CIの成功を、将来追加するファイル操作などのOS差まで検証済みという意味にはしない。

### 2.2 開発者

| 対象 | 採用時の基準と確認状況 |
|---|---|
| Node.js | 開発用の固定版は24.22.1 |
| npm | 開発用の固定版は11.21.1。プロジェクトの依存はlockfileで固定する |
| JDK | 21を開発時の基準とする。検証ではEclipse Temurin 21.0.12を使用した |
| shadow-cljs | 3.5.7 |
| ClojureScript | shadow-cljsが使用する1.12.145。個別に上書きしない |

JDKはビルド・REPLなどの開発に使う。利用者の実行環境へ持ち込まない。開発用・配布用の両ビルドを確認する構成を基本とする。

npmの採用条件と残る問題は、[開発環境の仕様](development-environment.md#npmの採用条件と残る問題)を参照する。過去の調査で使用したnpm 11.19.1は、実施当時の記録として保持する。

対応範囲内の更新版へ開発環境を更新する場合は、必要な検証を行う。開発用の固定版を更新することと、最低対応版を引き上げることは別に判断する。

## 3. 実装とJavaScript連携

本体は可能な限りClojureScriptで実装する。極端に複雑になる、または実現できない場合は、JavaScriptを使う理由と範囲を相談する。検証でJavaScriptに置いた入出力や処理の接続を、そのまま製品の責務分担にしない。

shadow-cljsでNode.js向けのESMを生成する。検証で成立した設定は`:target :esm`、`:runtime :node`、npm依存に対する`:js-provider :import`である。製品のモジュール分割、出力ファイル名、公開関数は別途設計する。

本ごとのJavaScript拡張はまずESMの.mjsを使い、実行時に読み込む。本体や拡張の再ビルドを差し替えの前提にしない。同期の返り値とPromiseの両方を扱い、同期throwとPromiseのrejectの原因を追える構成とする。拡張APIの引数や戻り値の形、読み込むパスの指定方法は今回確定しない。

CommonJSの製品対応は今回含めず、必要な要件が出た場合に再検討する。調査用のCommonJSビルドは比較・再現の材料として残す。

### デバッグ

Node.js向けESMでREPLの接続・評価が成立した。配布用ビルドではsource mapを生成したが、Node.jsの`--enable-source-maps`だけでは元のClojureScript位置へ戻れず、`source-map-support/register`を事前に読み込むと追跡できた。検証した補助パッケージは0.5.21である。

この補助を開発時に使う負担は許容する。製品での組み込み方、常時有効にするか、ソースやmapの配布範囲は今回確定しない。利用者にもJDKを要求する対策は採らない。

## 4. MarkdownとVivliostyleの構成

### 4.1 処理経路

Markdown原稿を現行remark系で解析し、AST上で独自記法や通常の要素を加工する。その後Markdownへ再出力し、VFMでHTMLへ変換する。

| 役割 | 採用時のバージョンの基準 |
|---|---|
| 処理基盤 | unified 11.0.5 |
| Markdown解析 | remark-parse 11.0.0 |
| ディレクティブ解析 | remark-directive 4.0.0 |
| GFM・脚注の解析と再出力 | remark-gfm 4.0.1 |
| Markdown再出力 | remark-stringify 11.0.0 |
| 後段のHTML化 | @vivliostyle/vfm 2.7.2 |
| 組版の接続確認 | @vivliostyle/cli 11.3.3 |

表は採用判断と実装開始の基準であり、将来の更新を禁止するものではない。VFMが持つunified 9・remark-parse 8などの間接依存はVFM自身の依存として扱い、前処理側の現行remark系と強制統一しない。比較用に使用したremark-rehype・rehype-stringifyの現行版は、この経路の必須依存として確定しない。

元原稿を変更せず、変換結果を別の場所へ出力する。ASTからの再出力で箇条書きの記号や空白などが変わることは許容するが、内容や組版上の意味の保持を確認する。中間Markdownの保存を製品の必須機能にするかは、入出力の設計で決める。

clonoはVivliostyleのビルド前に独立したCLIとして実行する。組版はシェルやpackage.jsonなどで連続実行し、clono内部へVivliostyleのビルドを組み込むことを前提にしない。章順と対象ファイルはclono側の一つの定義からVivliostyle側へ共有し、二重定義を避ける。具体的な設定形式は今回確定しない。

### 4.2 コラムとページ下部脚注

脚注はPandoc風の参照・定義からページ下部に配置する。標準の出力モードはVFMのdpubとする。gcpmも検証では成立したが、製品での切り替え機能は今回確定しない。

コラム内部を先にHTML化せず、開始・終了のHTMLとMarkdownの子要素を分けて再出力し、脚注をVFMへ渡す。検証ではこの方法で、強調・リスト・コードと、コラム内定義・コラム外の後方定義を保持し、通常本文と混在した短い脚注を参照元ページの下部へ出力できた。

コード内にコラムの閉じ記法と同じ「:::」を書く検証例では、外側の区切りを「::::」にする必要があった。記法の扱いは独自記法の仕様検討で引き継ぐ。

コラムの入れ子は対象にせず、同じ脚注への複数参照も当面の要件に含めない。同文の脚注でも別々に定義する。長い脚注の完全な分割規則や、任意のHTMLと共存することは今回保証しない。

調査用の本全体の生成処理は従来方式のままで、追加のコラム処理は専用の入口で検証した。章参照・索引・H1拡張と新しいコラム処理を組み合わせる製品実装は、後続で設計・検証する。

## 5. 依存リスクと見直し

VFM配下のtrimは当面0.0.1を使う。開発時だけ0.0.3へ差し替える構成は採らず、開発と配布の依存をそろえる。処理時間増大の問題は未解消である。修正版の互換性確認と配布実験の結果は、[検証結果の第14節](../../research/implementation-environment/results/verification.md#14-手順4の依存リスク確認trim-003での検証)に残す。

残る8件のアドバイザリも、確認した呼び出し方・設定・実行場所を維持する前提で当面許容する。発生条件と利用経路の根拠は[第12節](../../research/implementation-environment/results/verification.md#12-手順4の依存リスク確認問題ごとの発生条件)を参照する。安全性を一般化せず、第三者入力を自動で受け付ける用途には、この判断をそのまま適用しない。

依存リスクの現在の対応状態、後回しにした理由、見直す時点は[backlog](../backlog.md#依存の脆弱性への対応を見直す)で管理する。VFM・依存の更新や利用条件の変更時には、その条件に従って再評価する。

第三者由来の内容と配布時の表示は[ライセンス方針](licensing.md)に従う。調査時点のライセンス情報は[参照資料](../../research/implementation-environment/sources.md)に残し、実際の配布物の構成と同梱方法が決まった時点でも必要な条件を確認する。

## 6. 検証資料と後続で決めること

調査用の依存、設定、入力、コードは[research/implementation-environment/](../../research/implementation-environment/README.md)に置く。[検証結果](../../research/implementation-environment/results/verification.md)のコマンドは、この調査環境を再現するためのものであり、正式な製品の開発コマンドではない。

正式な開発・テスト基盤とCI設定は[開発環境の仕様](development-environment.md)、現在のCLIは[CLIの仕様](cli.md)に定める。デバッグの補助方法も開発環境の仕様を参照する。調査用の組版スクリプトは製品機能として扱わない。

PRレビューツール、製品の設定と拡張API、ソース・mapを含む配布構成、公開・リリース手順は後続で決める。

決定事項は[開発フロー](../development-workflow.md#111-採用した実装環境)と[README](../../README.md#開発について)へ反映済みである。実施内容と確認結果は、[選定の手順書](../plans/0002-choose-implementation-environment.md)の手順5に記録している。
