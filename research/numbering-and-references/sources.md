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
