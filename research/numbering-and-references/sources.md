<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# 採番・参照・目次の参照資料

確認日：2026-10-20（日本時間）。[調査の入口](README.md)、[結果と検証項目](results/verification.md)、[手順書0005](../../docs/plans/0005-investigate-numbering-and-references.md)を参照する。

## 1. 公式ドキュメント

| ID | 資料 | 確認内容と適用上の注意 |
|---|---|---|
| S01 | [Vivliostyle Supported CSS Features](https://docs.vivliostyle.org/en/reference/supported-css-features/) | `target-counter()`・`target-counters()`・`target-text()`を`content`値で利用する機能として掲載。版固定のページではなく、Core 2.45.2についてはS08の配布コードとも照合した |
| S02 | [CSS Generated Content Level 3：Cross References](https://www.w3.org/TR/css-content-3/#cross-references) | `target-counter()`は指定カウンター、`target-text()`は参照先の本文や疑似要素のテキストを取得する規定。ドラフトであり、仕様本文の同一文書への制限を複数原稿対応の根拠に使わない。Vivliostyleの実装と後続検証を分ける |
| S03 | [CSS Lists and Counters Level 3](https://www.w3.org/TR/css-lists-3/#counters)／[CSS 2.1：Automatic counters and numbering](https://www.w3.org/TR/CSS2/generate.html#counters) | カウンターの生成・増分・スコープ・出力。階層ごとに別のカウンターを持つ例、表示しない要素の扱いを確認。複数文書間の自動継続を保証する資料ではない |
| S04 | [Vivliostyle CLI 11.3.3の設定資料](https://github.com/vivliostyle/vivliostyle-cli/blob/v11.3.3/docs/config.md)／[同版の目次作成資料](https://github.com/vivliostyle/vivliostyle-cli/blob/v11.3.3/docs/toc-page.md) | `toc.sectionDepth`、`transformDocumentList`、`transformSectionList`、`compose`、`rel: contents`。`sectionDepth`の既定値は0、見出しの掲載階層を1～6で指定する。H1と文書項目の扱いはS07でも確認した |
| S05 | [公式チュートリアル：目次の作成](https://vivliostyle.org/ja/tutorials/create-table-of-contents/) | 目次のリンク先ページ番号をCSSの`target-counter(attr(href), page)`で表示する例。対象版でのページ番号・PDFリンクの成立は後続検証に残す |
| S06 | [VFM仕様](https://docs.vivliostyle.org/en/vfm/vfm/) | コード・画像のキャプション、IDの移動、Raw HTMLとMarkdown、見出し・section、表、相対リンクの拡張子変換。ページ内の「Table of contents」は仕様ページ自身の目次であり、本全体の目次生成APIの説明と読み替えない。版固定ではないためS09と照合した |

`docs.vivliostyle.org/`と旧VFM資料のURLは、Web取得では英語版への案内のみだったため、`/en/`以下を参照した。一部のGitHub raw URLは取得できず、取得できたタグ付き資料と公開パッケージを根拠にした。取得失敗を機能不存在の根拠にはしていない。

## 2. 対象バージョンの配布コード

以下は公式npmレジストリの配布アーカイブを読み取った結果である。インストールやパッケージ内コードの実行は行っていない。

### S07 CLI 11.3.3：目次の抽出と出力

- [公開メタデータ](https://registry.npmjs.org/@vivliostyle%2fcli/11.3.3)
- [配布アーカイブ](https://registry.npmjs.org/@vivliostyle/cli/-/cli-11.3.3.tgz)
- 確認したパス：`package.json`、`dist/server-96BNRrbl.js`、そのsource map、`dist/schema-jMUYOVzB.js`。

`getStructuredSectionFromHtml`はH1～H6を文書順に集め、引用内を除外する。見出し自身または親要素のIDからリンク先を作り、`innerHTML`をsanitizeした`headingHtml`と、`textContent`由来の`headingText`を持つ。CSSの実行結果をこの抽出段階で取得する処理ではない。

既定の`transformSectionList`は`headingHtml`を目次へ使う。`transformDocumentList`はsectionの最上位が一つのH1である場合に文書項目との重複を避ける分岐を持つ。画像のみの見出しや非表示文字を含む見出しを、目次でも意図どおりに扱えるかは未検証である。

source map内の元ソース名は`../src/processor/html.tsx`。`sectionDepth`による絞り込み、目次生成のフック、空のnavへの挿入を静的に確認した。フックは本調査用の候補であり、clonoの公開APIとして採用していない。

### S08 Core 2.45.2：参照値の解決

- [公開メタデータ](https://registry.npmjs.org/@vivliostyle%2fcore/2.45.2)
- [配布アーカイブ](https://registry.npmjs.org/@vivliostyle/core/-/core-2.45.2.tgz)
- 確認したパス：`package.json`、`lib/vivliostyle.js.map`、`lib/vivliostyle/counters.d.ts`。

source mapの`../src/vivliostyle/css-cascade.ts`に`target-counter`・`target-counters`・`target-text`の処理、`counters.ts`に`getTargetCounterVal`・`getTargetTextVal`、複数文書のURLを変換したID、未解決参照を後から解決する処理を確認した。

`extractPseudoElementText`は本文を疑似要素と分けて取り出す。これは番号と本文を分けて参照する候補の根拠になる。一方、カウンターを増やす要素、参照先IDを置く要素、非表示文字や画像だけの見出し、前方・別原稿への参照が同時に成立するかは動作未確認である。

CLIの目次は組版前のHTMLを読み、Coreの`target-text()`は組版した要素のテキストを扱う。両者を同じ取得方法とは扱わない。

### S09 VFM 2.7.2：ID・キャプション・HTML

- [公開メタデータ](https://registry.npmjs.org/@vivliostyle%2fvfm/2.7.2)
- [配布アーカイブ](https://registry.npmjs.org/@vivliostyle/vfm/-/vfm-2.7.2.tgz)
- 確認したパス：`package.json`、`lib/index.js`、`lib/revive-parse.js`、`lib/revive-rehype.js`、`lib/plugins/`内の`attr.js`・`slug.js`・`figure.js`・`code.js`・`table.js`・`toc.js`・`rewrite-relative-href-extensions.js`。

| 処理 | 静的に確認した内容 |
|---|---|
| 見出しID | 属性解析後にslug処理があり、明示IDを優先する。sluggerは変換するtreeごとに初期化する。原稿間でIDが一意になることは保証しない |
| 図 | 単独画像のキャプションを`figure`・`figcaption`として構成する。`assignIdToFigcaption`は既定でfalse、trueなら画像のIDをキャプションへ移す |
| コード | 言語の後のコロンによるタイトルや`title`メタデータを解釈する。キャプション付きは`figure`・`figcaption`・`pre`・`code`となり、同オプションでcodeのIDをキャプションへ移せる |
| 表 | GFM表のHTML化とセルの配置指定を扱う。確認した表handlerと設定に、表キャプションの専用生成処理は見つからなかった。通常HTMLの`caption`等を使う候補を残す |
| Raw HTML | HTMLとして解析する後段処理がある。生HTMLの見出しと、Markdownの見出し用slug処理が同じ経路とは限らないため、差し替え後のID保持を検証する |
| リンク | `rewriteRelativeHrefExtensions`は既定で無効。相対hrefの拡張子を変更する候補だが、参照先の存在や採番を確認する処理ではない |

図・表・コードの採番や本全体の参照解決を、キャプション生成だけで満たせるとは判断しない。`figure`は図とコードの双方で使うため、種類を区別するCSSまたは調査用の識別情報が必要になる。

## 3. 取得内容の識別

`npm view <package>@<version> version gitHead dist.integrity dist.tarball --json`で公開情報を照会した。VFMとCLIの回答には`gitHead`がなかったため、未確認のコミットIDで公開物を識別しない。

| 公開物 | レジストリのintegrityと取得アーカイブのSHA-512が一致した値 |
|---|---|
| VFM 2.7.2 | `sha512-VHHhriZQGSis9z4Jd+E4IqqWlm+lC0IgeVSliVYDK/IfgIwyyeUKAKidc4En94U0Uf1BtuicvEu1Gv3s94KNJA==` |
| CLI 11.3.3 | `sha512-s+vAsyqJgBMIk3LBAJFunHd1OzglIU5ufAbgRgP1rEeLumjyOlVmeCnWCUpDAu6DVM+C6wwJ8eNRs8Nc9rfVbg==` |
| Core 2.45.2 | `sha512-PIvmTSLWHYANbIVAtJrb5Uio5cTm1NGK67UuxdifMv9XgYSkOd5LTuYlUgUICuubDLra9CUp6wEJ+gUDFBxiyA==` |

アーカイブをPython標準ライブラリで読み、ローカルの`research/implementation-environment/node_modules/@vivliostyle/`以下にある対象コードとバイト照合した。下表のほか、VFMのS09記載ファイル、CLIのschema・source map、各package.jsonも一致を確認した。配布コードは実行していない。

| パッケージ内の代表パス | SHA-256 |
|---|---|
| VFM `lib/index.js` | `3ccf0a5e629591aff33bf825642def8ed16e41d7b1441483b749418930f7e8a2` |
| VFM `lib/plugins/slug.js` | `17a989d20411ca1da462262834dedd49279d62ffce33aa314c5c793ecaee2af1` |
| VFM `lib/plugins/figure.js` | `4d25db327f51ff980b418a1652c41cdb54decf98f4ec22abc99e15873deb0223` |
| VFM `lib/plugins/code.js` | `875b7421d666d9b2bf351b2ed89e4cc4170c7a9265a9d90ef5f03bcda4b033c2` |
| VFM `lib/plugins/table.js` | `e3cc84b9e96f87b3869d92d033ca17953a026b7b9777c611ef5a73c81911dc03` |
| CLI `dist/server-96BNRrbl.js` | `28ae0bae2e39d632f8ed2fc7c3a9dfbe82f02ea084c2f74820f12d20331c21d3` |
| Core `lib/vivliostyle.js.map` | `379cfb9421cd246e88148d63548fe8337e4ed544f6dccb0bc8153c02adfe4938` |

この照合は参照したコードの識別のためであり、ローカル依存全体の監査や、挙動の実証ではない。アーカイブの一時保存先には依存せず、再確認時は上記URLとintegrityを使用する。

## 4. 手順2で確認した実行版との違い

CLI 11.3.3がPDFを生成するときは、Viewer 2.45.1に内蔵されたコードを使った。生成PDFのCreatorはVivliostyle.js 2.45.1を示し、Viewerの`lib/index.html`にもcore+viewer 2.45.1の表示があった。独立したCoreパッケージ2.45.2がlockfileに存在することだけでは、実行版が2.45.2とは判断できない。

`node_modules/@vivliostyle/viewer/lib/js/vivliostyle-viewer.js.map`の`../../../core/src/vivliostyle/counters.ts`と`css-cascade.ts`を確認し、参照値とtarget-textの処理を読み直した。S08はCore 2.45.2の静的読解結果として維持し、今回の実測はViewer内蔵版の結果として[第9節](results/verification.md#9-手順2通常出力での検証結果)に記録した。bundleと参照処理のハッシュは[環境記録](results/environment.json)を参照する。

## 5. 手順2Aで確認したページ側カウンターの資料と実装

### S10 公式チュートリアル：カウンタと柱のスタイル

- [公式チュートリアル](https://vivliostyle.org/ja/tutorials/configure-counters-running-heads/)
- 確認日：2026-10-20（日本時間）。

同資料は、`@page :first`で本全体の先頭に章カウンターを初期化し、`@page :nth(1)`で各原稿の最初のページに対して加算する方法を紹介している。手順1・2ではこの候補を検証していなかった。手順2Aでは本文・付録それぞれに名前付きページと別のカウンターを使い、前後付け・目次を加算から除外する調査用CSSへ具体化した。チュートリアルの例だけで今回の全要件が成立すると判断したものではない。

実行版のViewer 2.45.1のsource mapから、次の処理を確認した。

| ソースと処理 | 確認内容 |
|---|---|
| `css-page.ts`の`IsNthPageAction`・ページセレクター解析 | `:nth(An+B)`は`layoutPositionAtPageStart.page`を使う。名前付きページの条件と併用でき、`:nth(An+B of <page-type>)`とは分岐を分けている |
| `css-page.ts`の`IsFirstPageAction`、`ops.ts`の`page-number` | `:first`は本全体のページ位置を評価する。原稿ごとの`:nth(1)`と区別する |
| `css-cascade.ts`の`buildCounterText` | ページ側で管理するカウンターを、生成内容の値へ反映する |
| `counters.ts`の`getTargetCounterVal`・`getTargetPageCounters`・`adjustCountersForCrossScope` | 参照先要素のページ側の値を取得し、文書側のカウンターとの関係を扱う |

Viewer bundleは手順2と同一である。参照したソースのハッシュと確認項目は[手順2Aの環境記録](results/step2a-environment.json)、実測と限界は[検証結果の第10節](results/verification.md#10-手順2aページ側のカウンター方式の追加検証)へ記録した。第三者コードは転載していない。

## 6. 手順3で確認した目次フックと文字の取得経路

確認日：2026-10-20（日本時間）。S04・S07～S09と、実際に使うViewer内蔵版の処理を読み直した。

CLI 11.3.3の`getStructuredSectionFromHtml`は、CSSの表示状態にかかわらず元HTMLの見出しから`headingText`を取得する。一方、既定の`transformSectionList`はsanitize済みの`headingHtml`を目次へ入れる。このため、画像と参照用文字を持つ見出しを既定出力で複製すると、その構造も目次に残る。

採用版の`generateTocListElement`は、Node.jsで`transformSectionList(nodeList)(propsList)`を呼ぶ。調査用フックは既存の情報からHASTのtextノードを作り、リンク・階層・子項目を維持する。`headingText`をRaw HTMLとして出力せず、DOMPurifyの設定やフックも変更しない。この呼び出し方を、手順1で合意した依存リスクの確認範囲と照合した。

[タグ付きの目次作成資料](https://raw.githubusercontent.com/vivliostyle/vivliostyle-cli/v11.3.3/docs/toc-page.md)も参照した。GitHubの表示ページは取得できなかったが、同じタグのraw資料は取得できた。フックの引数と返す構造の根拠にはS07の配布コードを使った。

Viewer内蔵版の`getTargetTextVal`・`getTargetPageText`は組版時の対象要素から文字を取り出す。`display: none`で文字を消す場合と、ボックスを残してクリップする場合の差は、静的な読解だけで決めず、[第11節](results/verification.md#11-手順3見出しの差し替えとの組み合わせ)に実測した結果を記録した。画像のaltやSVGの表示文字が、見出し本文の保持を代替するとは判断しない。

## 7. Python検査依存の更新で確認した資料

確認日：2026-10-21（日本時間）。PR #10の追加指摘を受け、直接依存の更新先を確認した。

| 資料 | 確認内容 |
|---|---|
| [pypdfの公式アドバイザリ](https://github.com/py-pdf/pypdf/security/advisories/GHSA-5jq2-8x83-x246) | 長い間接オブジェクトヘッダーを持つ不正なPDFで処理が長時間化する問題。6.18.0未満が影響範囲で、6.18.0で修正された |
| [pypdfの変更履歴](https://pypdf.readthedocs.io/en/stable/meta/CHANGELOG.html) | 6.18.0以降にもストリーム解析や循環構造などのセキュリティ修正があるため、更新先を6.21.1とした |
| [Pillowの公式13.0.0リリースノート](https://pillow.readthedocs.io/en/stable/releasenotes/13.0.0.html) | 指摘されたGHSA-8g5g-8vmr-9782を含む修正を確認し、13.0.0へ更新した |
| [Pillowのアドバイザリ](https://github.com/advisories/GHSA-8g5g-8vmr-9782) | 12.3.0以下が影響範囲、13.0.0が修正版。対象はJPEGデコードであり、今回のPoppler生成PNGの処理と区別する |
| [pypdfのPyPIメタデータ](https://pypi.org/pypi/pypdf/6.21.1/json)・[PillowのPyPIメタデータ](https://pypi.org/pypi/Pillow/13.0.0/json) | 配布版の存在とPython 3.12への対応を確認した。取得時点で両版の`vulnerabilities`配列は空だったが、全依存の安全性を保証する結果とは扱わない |

旧版と更新版のメタデータのURL・取得内容のハッシュ、実際の導入版、再検証結果は[更新後の記録](results/python-security-update.json)へ保存した。旧版のPyPI登録情報は、別IDや別名を含むため件数を固有の脆弱性数と解釈せず、公式の修正情報と併せて確認した。
