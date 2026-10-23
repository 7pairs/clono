<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# 索引調査の参照資料

確認日：2026-10-22（日本時間）。[手順書](../../docs/plans/0006-investigate-book-index.md)・[調査結果](results/verification.md)。資料上の説明、コードの読解、今後の検証案を区別する。ここでの参照は採用や動作保証を意味しない。2026-10-23の本文閲覧については第5節に追記し、以前の取得記録は当時のまま残す。

## 1. Vivliostyleの公式資料と対象版

| ID | 資料 | 確認した内容 |
|---|---|---|
| V01 | [公式ドキュメントの入口](https://docs.vivliostyle.org/en/) | 2026-09-15更新の案内はCore 2.45.1、CLI 11.3.3、VFM 2.7.2を対象としている。ドキュメントの更新版と最新公開版は区別する |
| V02 | [Supported CSS Features](https://docs.vivliostyle.org/en/reference/supported-css-features/) | `target-counter()`・`target-counters()`・`target-text()`は`content`内で対応。段組みのプロパティも掲載。語の収集・読み順整列・ページ番号重複排除を行う索引専用機能の説明は、この一覧では見つからなかった |
| V03 | [CLI Config Reference](https://docs.vivliostyle.org/en/cli/config/)・[11.3.3の設定スキーマ](https://github.com/vivliostyle/vivliostyle-cli/blob/v11.3.3/src/config/schema.ts) | `toc`、`documentProcessor`、記事・目次・表紙の設定を確認。索引の収集・日本語照合・重複ページ集約を一括で指定する設定は確認できなかった |
| V04 | [VFMの構文](https://docs.vivliostyle.org/en/vfm/vfm/)・[パッケージの更新履歴](https://github.com/vivliostyle/vfm/blob/5bb52bb12c56f6695e51fc0737a4fdbc2dc9a5a4/packages/vfm/CHANGELOG.md) | 公開構文一覧と2.7.2までのパッケージ更新履歴を確認。索引用の語・読みの収集構文は確認できなかった。ルートの古いCHANGELOGだけで現在の機能を判断しない |
| V05 | [Core 2.45.1のcounters.ts](https://github.com/vivliostyle/vivliostyle.js/blob/v2.45.1/packages/core/src/vivliostyle/counters.ts) | URL・IDに対応するカウンターの取得と未解決参照の扱いを読解。`target-counter()`は参照先の値を返す処理であり、索引項目の整列や複数リンク間の重複排除を担う処理とは区別する |
| V06 | [CLI 11.4.0のリリース](https://github.com/vivliostyle/vivliostyle-cli/releases/tag/v11.4.0)・[更新履歴](https://github.com/vivliostyle/vivliostyle-cli/blob/v11.4.0/CHANGELOG.md) | 2026-10-19公開。npmのlatestも11.4.0だった。Core 2.45.2への更新、PDF後処理等の変更を確認。索引専用機能の追加は当該更新記録では見つからなかった |
| V07 | [Core更新履歴](https://github.com/vivliostyle/vivliostyle.js/blob/40142872f1ed9e9b52798798953250418b227a92/CHANGELOG.md) | 先頭の公開版は2.45.2。2.45.1との差はleader等の修正で、索引生成機能の追加ではなかった。全履歴の索引関連語も検索したが、不在の完全な証明とはしない |
| V08 | [目次とページ参照の公式例](https://vivliostyle.org/tutorials/create-table-of-contents/) | リンク先のページ番号をCSSで表示する例。索引一覧の自動収集を実証する例とは扱わない |

npmでは`npm view @vivliostyle/cli version --json`相当のメタデータ参照で11.4.0、`npm view @vivliostyle/vfm version --json`で2.7.2を確認した。GitHubのCLI最新リリースAPIでもv11.4.0、公開日時2026-10-19T13:54:17Zを確認した。インストールやバージョン変更はしていない。

## 2. 規格と照合処理

| ID | 資料 | 確認した内容・限界 |
|---|---|---|
| J01 | [日本規格協会：JIS X 4061:1996](https://webdesk.jsa.or.jp/books/W11M0090/index/?bunsyo_id=JIS+X+4061:1996) | 標題は「日本語文字列照合順番」。状態は有効、確認日は2022-10-20。概要には単純照合、読み・表記照合、代表読み照合の3方法がある。商品情報と概要を確認したが、規格全文は未読 |
| J02 | [JISC](https://www.jisc.go.jp/)・[JIS検索](https://www.jisc.go.jp/app/jis/general/GnrJISSearch.html) | ブラウザーでX4061を検索し、詳細画面で制定日1996-11-20、最新確認日2022-10-20を照合した。通常PDFはログインが必要と表示された。ログイン不要と案内される音声PDFのリンクも確認したが、クリック後に本文の表示・新規タブを確認できず、本文は未読。条項別の適合判定はしない |
| J03 | [ECMA-402 Collator Objects](https://tc39.es/ecma402/#collator-objects) | `Intl.Collator`の比較は実装依存。`sensitivity`はbase・accent・case・variant。日本語ロケールの指定だけでJIS全面準拠とは判断しない |
| J04 | [Node.js 24系の国際化資料](https://nodejs.org/docs/latest-v24.x/api/intl.html) | HTTP取得した資料の表題はv24.22.1。ICUを用い、標準配布はfull-icuという説明を確認した。実行予定の24.22.0とは版を区別し、実際の設定と版は実行環境で記録する |
| J05 | [ICU Collation Concepts](https://unicode-org.github.io/icu/userguide/collation/concepts.html#sorting-of-japanese-text-jis-x-4061) | JISとの関係と、平仮名・片仮名の区別に第4レベルを使う説明。ICU内部の能力とJavaScriptから指定できる範囲は同一としない |
| J06 | [CLDR 48の日本語照合データ](https://github.com/unicode-org/cldr/blob/release-48/common/collation/ja.xml)・[ライセンス](https://github.com/unicode-org/cldr/blob/release-48/LICENSE) | 清濁・長音/小書き/繰返し/通常の仮名・平仮名/片仮名についてJIS 4.4.10.2への参照がある。規格全文の代わりではなく、候補実装が意図する規則の一次資料として読む。Unicode License V3 |
| J07 | [Lingua::JA::Sort::JIS 0.10](https://metacpan.org/release/SADAHIRO/Lingua-JA-Sort-JIS-0.10/view/JIS.pod) | JIS X 4061-1996の実装を標榜。比較レベル・漢字クラス・読みと表記を順に比較する例を提供。Perlの別実行環境が必要で、古いUnicodeの扱いに注意書きがある。ライセンスはPerlと同条件。実行していない |
| J08 | [Go jisx4061 v0.1.0](https://github.com/shogo82148/jisx4061/blob/v0.1.0/x4061.go)・[ライセンス](https://github.com/shogo82148/jisx4061/blob/v0.1.0/LICENSE) | 単純照合・基本照合規則・拡張漢字クラスを採ると明記。Goの別実行環境が必要。MIT。全文規格や独立した期待値に代えて正解扱いはしない。実行していない |

非公式の規格転載ページも検索結果に出たが、本調査の規格への適合判断の根拠には採用していない。規格本文の入手・閲覧と、処理系定義事項の確認は残る。

## 3. 外部の索引実装例

| ID | 資料 | 読解した内容と利用上の区別 |
|---|---|---|
| E01 | [AyumuTakaiの索引サンプル](https://github.com/AyumuTakai/vivliostyle-index-sample/tree/525c91e548f8f8c7aa0ef4cb202e0bde8767fc11) | README、scripts.js、設定、LICENSEを確認。置換処理で語と読みを集めて索引を生成する。参照は読みをキーにまとめ、最初の語を見出しにするため、同じ読みの異なる語も一つの項目になる。同読み異語を分ける今回の要件とは異なり、この統合方法は継承しない。READMEは同一ページの重複に手動無効化を案内。仮名と英字を変換して文字列比較する方式で、JIS対応を保証しない。MIT。旧置換記法を製品へ継承する候補とはしない |
| E02 | [techbook-templateのgen-index.mjs](https://github.com/tomio2480/techbook-template/blob/749f8a8bfad8673b45a3dcc51b5c232ade793a06/scripts/gen-index.mjs) | README、生成コード、LICENSEを確認。語・読み付きアンカーを収集して一覧の骨組みを生成する。コードは読みの平仮名化後に符号位置で比較し、同じキーでは表記を比較する。JIS照合の代用にはしない。MIT |
| E03 | [Vivlio Starterの重複排除](https://github.com/Atelier-Mirai/vivlio-starter/blob/374df9730f3538d825e89937d1ea6b703193df64/lib/vivlio_starter/cli/build/backlink_deduplicator.rb)・[工程の接続](https://github.com/Atelier-Mirai/vivlio-starter/blob/374df9730f3538d825e89937d1ea6b703193df64/lib/vivlio_starter/cli/build/backlink_dedup_orchestrator.rb)・[PDFページ対応の抽出](https://github.com/Atelier-Mirai/vivlio-starter/blob/374df9730f3538d825e89937d1ea6b703193df64/lib/vivlio_starter/cli/build/pdf_page_map_extractor.rb)・[LICENSE](https://github.com/Atelier-Mirai/vivlio-starter/blob/374df9730f3538d825e89937d1ea6b703193df64/LICENSE) | 生成PDFのnamed destinationsから対応を取り、索引HTMLの同ページへのリンクを整理し、必要なら再組版する流れを確認。ページ対応を取得できない索引リンクも削除するが、その動作は今回継承せず、不足として検出する。Ruby・pdf/reader等を使う。MIT。規模の大きい本作りツール全体の採用や、その実測結果をclonoへ移す判断はしていない |

既存ツールの丸ごと採用、コードの部分再利用、考え方の参照は別に判断する。今回は参照と読解のみで、第三者のコードやデータは成果物に取り込んでいない。後続で取り込む場合は、対象ファイルのライセンス・著作権表示・依存を改めて確認する。

## 4. アクセス状況と再確認

Vivliostyleの公式入口・ドキュメント、JSAの商品情報、JISCの入口、Unicode/ICU、GitHubの公開コードは取得できた範囲で確認した。メンテナンス告知によって保留にしたサイトは、今回の範囲ではない。

一部のGitHub画面はWeb取得でcache missとなったため、同じ公開リポジトリの公式API・raw配信で対象を確認した。推測したドキュメントURLの取得エラーは、公式入口から正しいリンクを辿って解消した。これらをPHPの脆弱性やサイトのメンテナンスが原因とは推定しない。

JIS本文の未読はメンテナンスと判定したものではない。通常PDFにはログインが必要で、ログイン不要とされる音声PDFも今回のブラウザーでは本文を表示できなかった。アカウント登録・ログイン・購入は行っていない。後日、利用できる公式の閲覧経路を二人で確認し、規格に基づく期待値を確定する。今後メンテナンス告知が出たサイトは連続して取得を試みず、確認を保留する。

### 資料調査後の報告と保留方針

2026-10-22、開発者から、利用者登録で仮パスワード発行までは進んだがログイン画面に「メンテナンス中」と表示されたとの報告があった。二人で、JIS本文の確認と厳密な適合判定を復旧後へ保留することに合意した。PHPの利用や、メンテナンスの原因は未確認とする。上記の取得状況は、開発者の報告前にそらが確認した時点の記録として残す。

## 5. 2026-10-23のJIS本文閲覧

確認日：2026-10-23（日本時間）。開発者からメンテナンス終了と利用者登録の報告を受け、ログイン済みChromeでJISCのJIS検索からX4061を開き、本文ビューアーの全33ページ中、今回に関係する主要条項を閲覧した。J01・J02と第4節の閲覧不能・保留の記録は、2026-10-22時点の確認として維持する。

| ID | 資料 | 今回確認した範囲・限界 |
|---|---|---|
| J09 | [JISCのJIS検索](https://www.jisc.go.jp/app/jis/general/GnrJISSearch.html)から閲覧したJIS X 4061:1996本文 | 第1節の適用範囲、第2節の読み列等の定義、第3節の照合方法と規則の区別、第4節の基本照合規則・文字クラスと4.4.10の仮名の扱い、5.2の読み・表記照合、6.1・6.2の適合性と処理系定義事項などを確認した。全文の精査、漢字クラスの選択、各比較例の期待値の確定、候補実装の適合判定は未実施 |

5.2では読み列を比較し、同順位の場合に表記列を比較する手順を確認した。4.4.10では仮名の基底文字、長音・繰返しと照合属性の扱いを確認した。これらは追加調査と検証計画の根拠となる条項の所在を示すもので、`Intl.Collator`が同じ結果を返すと確認した記録ではない。

規格本文やビューアーの画像はリポジトリへ収録していない。今回の閲覧と、漢字クラス等を追加調査してから検証計画を確定する方針は、[検証資料の第11節](results/verification.md#11-jis本文閲覧と手順3の計画変更)と手順書に反映した。

## 6. 手順3の追加調査（2026-10-23）

J09の本文を引き続き閲覧し、4.4.7の数字、4.4.9のラテンアルファベット、4.4.11の漢字クラスを追加確認した。第1～6節の関連条項と以下の資料を照合した。第5節の未実施事項は追加調査前の記録として残す。[追加調査の結果](results/step3-research.md)は、静的調査の結論と未合意の検証案を分けている。比較実験や適合判定は未実施。

| ID | 資料 | 確認した内容・限界 |
|---|---|---|
| J10 | [ECMA-402 2025：Collator Objects](https://tc39.es/ecma402/2025/#collator-objects) | 第10節。比較の実装依存性、正準等価な文字列の同順位、関連するロケール拡張キーがco・kn・kfに限られること。規格の要求と、対象版での実測は区別する |
| J11 | [Node.js v24.22.0に同梱されるV8のjs-collator.cc](https://github.com/nodejs/node/blob/v24.22.0/deps/v8/src/objects/js-collator.cc) | 360行の関連拡張キー、477行の正規化有効化、500～515行の感度とICUレベル、記号の扱いを静的に確認。variantはTERTIARY。実行時の比較結果は未測定 |
| J12 | [Node.js v24.22.0の国際化資料](https://nodejs.org/download/release/v24.22.0/docs/api/intl.html)・[同タグのICU版表示](https://github.com/nodejs/node/blob/v24.22.0/deps/icu-small/source/common/unicode/uvernum.h) | ICUを使う構成と標準配布のfull-icu、ICU 78.3の版表示。標準配布の説明だけで実行環境を推定せず、手順2の実版記録と合わせて扱う |
| J13 | [UTS #35 Part 5：Collation、Version 48.2](https://www.unicode.org/reports/tr35/tr35-78/tr35-collation.html#Root_Collation_Data_File_Formats) | unihanが部首・残画数の順序を使う説明。実行環境のCLDR 48および参照データrelease-48とは版を区別し、実行時データの同一性の根拠にはしない |
| J14 | [Unicode公式配布のJIS0208.TXT](https://www.unicode.org/Public/MAPPINGS/OBSOLETE/EASTASIA/JIS/JIS0208.TXT) | JIS X 0208 (1990)からUnicodeへの対応表、Table version 2.0。OBSOLETE配下の歴史的資料で、ヘッダも可能な対応の一つと説明する。第2列のJISコードと第3列のUnicodeを静的に照合し、亜の0x3021（16区1点）・U+4E9C、一の0x306C（16区76点）・U+4E00、先頭5文字の対応、U+20BB7の未掲載を確認した。候補実装の出力から独立した入力・期待値案の補助とし、JIS本文や製品一般の文字対応の代わりにしない |

J05の第4レベルの説明、J06のrelease-48にあるprivate-kana・standard・unihanの規則も再確認した。J06のXMLを参照したことは、JISへの適合や組込みデータとの同一性の確認を意味しない。

再確認用に、取得した公開資料のSHA-256を記録する。取得内容は一時ディレクトリで参照し、第三者のソースやデータはリポジトリに収録していない。

| 資料 | SHA-256 |
|---|---|
| J11：v24.22.0のjs-collator.cc | `24fb8035073e8dab05107ce7f96bb191c26abf524babe5c983f26d687a5c2c77` |
| J06：release-48のcommon/collation/ja.xml | `a43b2ca5956e2ce84b20d943af9b5ce07563ec8a3beb4176b2b3d386fa11ab04` |
| J12：v24.22.0のuvernum.h | `13cd24940babf5c6c1016131c9917742fc6cf198228689904d88116bb7d04118` |
| J12：v24.22.0のintl.html | `e03d3118a7b3decc1fac6370210a52121f42f79d51e064ffee78adb2348ad4b0` |
| J14：JIS0208.TXT | `1c571870457f19c97720631fa83ee491549a96ba1436da1296786a67d8632e87` |

一部のWeb取得が失敗した資料は、同じ公式のraw配信または固定版ドキュメントから取得した。取得失敗をメンテナンスやPHPの利用と結び付けていない。今回、メンテナンス告知によって追加の確認を保留したサイトはない。
