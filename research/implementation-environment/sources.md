<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# 参照資料とパッケージ情報

確認日：2026-10-16（日本時間）。[調査本文](README.md)と[手順書](../../docs/plans/0002-choose-implementation-environment.md)を参照する。

## 1. 公開パッケージの確認結果

`npm view <package> version engines license time.modified --json`で、確認時点の公開メタデータを照会した。下表の版は調査対象であり、採用済み・実行確認済みではない。

| パッケージ | 版 | ライセンス表示 | engines.node | レジストリのtime.modified（UTC） |
|---|---|---|---|---|
| shadow-cljs（npm側） | 3.5.7 | ISC | >=6.0.0 | 2026-10-12T15:44:25.328Z |
| @vivliostyle/cli | 11.3.3 | AGPL-3.0 | >=22.12.0 | 2026-09-12T16:38:04.852Z |
| @vivliostyle/vfm | 2.7.2 | Apache-2.0 | >=22 | 2026-07-15T06:48:40.735Z |
| unified | 11.0.5 | MIT | 未指定 | 2024-06-19T12:33:43.698Z |
| remark-parse | 11.0.0 | MIT | 未指定 | 2023-11-20T10:43:28.469Z |
| remark-directive | 4.0.0 | MIT | 未指定 | 2025-02-27T15:15:21.175Z |
| remark-rehype | 11.1.2 | MIT | 未指定 | 2025-04-02T13:46:59.559Z |
| remark-stringify | 11.0.0 | MIT | 未指定 | 2023-11-20T10:43:34.389Z |
| rehype-stringify | 10.0.1 | MIT | 未指定 | 2024-09-27T15:08:52.979Z |
| remark-gfm | 4.0.1 | MIT | 未指定 | 2025-02-10T12:43:11.063Z |
| markdown-it | 15.0.3 | MIT | 未指定 | 2026-10-16T01:47:54.862Z |

`time.modified`はパッケージ情報の更新時刻であり、各バージョンの公開日時やリポジトリの最終コミット日時ではない。古い値だけで保守終了とは判断しない。`engines`未指定も全Node.jsバージョン対応を意味しない。shadow-cljsの古いNode下限表示を、今回使うnpm依存全体の動作保証として扱わない。

各版のメタデータはnpm公式レジストリの次のページで確認できる。

- [shadow-cljs 3.5.7](https://registry.npmjs.org/shadow-cljs/3.5.7)
- [Vivliostyle CLI 11.3.3](https://registry.npmjs.org/@vivliostyle%2fcli/11.3.3)
- [VFM 2.7.2](https://registry.npmjs.org/@vivliostyle%2fvfm/2.7.2)
- [unified 11.0.5](https://registry.npmjs.org/unified/11.0.5)
- [remark-parse 11.0.0](https://registry.npmjs.org/remark-parse/11.0.0)
- [remark-directive 4.0.0](https://registry.npmjs.org/remark-directive/4.0.0)
- [remark-rehype 11.1.2](https://registry.npmjs.org/remark-rehype/11.1.2)
- [remark-stringify 11.0.0](https://registry.npmjs.org/remark-stringify/11.0.0)
- [rehype-stringify 10.0.1](https://registry.npmjs.org/rehype-stringify/10.0.1)
- [remark-gfm 4.0.1](https://registry.npmjs.org/remark-gfm/4.0.1)
- [markdown-it 15.0.3](https://registry.npmjs.org/markdown-it/15.0.3)

## 2. 資料別の確認箇所

### S01 ClojureScriptのビルド・言語差・ライセンス

- [Quick Start](https://clojurescript.org/guides/quick-start)：Node.js向けのビルドと実行、最適化の説明。
- [Compiler Options](https://clojurescript.org/reference/compiler-options)：`:target`、最適化、source mapなど。
- [Differences from Clojure](https://clojurescript.org/about/differences)：データ構造、実行基盤、JavaScriptオブジェクトのホスト連携。
- [Externs](https://clojurescript.org/guides/externs)：外部APIの名前の保持と推論・型ヒント。
- [Source maps](https://clojurescript.org/reference/source-maps)：元ソースとの対応。
- [License](https://clojurescript.org/community/license)：EPL-1.0。
- [変更履歴](https://github.com/clojure/clojurescript/blob/master/changes.md)：1.12.145にasync/await対応の追加がある。

公式サイトのトップは1.12.134を現行版として表示していた一方、shadow-cljs 3.5.7の依存定義と変更履歴では1.12.145を確認した。トップの表示だけで最新版を確定せず、検証時は使うビルドツールとの組み合わせを固定する。

### S02 shadow-cljs

- [User's Guide](https://shadow-cljs.github.io/docs/UsersGuide.html)：第2節のJDK要件、第8節のESM、第10節のNode向け出力、第13節のnpm連携、第14節の配布用ビルドとExterns。
- [3.5.7に対応するproject.clj](https://github.com/thheller/shadow-cljs/blob/f2fbb3a9ba7be3f91ff386039020a1632646e458/project.clj)：JDK 21向け、ClojureScript 1.12.145、JVM側のEPL-1.0表示。
- [同じコミットのESMターゲット](https://github.com/thheller/shadow-cljs/blob/f2fbb3a9ba7be3f91ff386039020a1632646e458/src/main/shadow/build/targets/esm.clj)：`:runtime :node`、Node向け開発クライアント、Node設定の分岐。

コミット`f2fbb3a9ba7be3f91ff386039020a1632646e458`はnpm 3.5.7の`gitHead`から取得した。npm側のISC表示とJVM側のEPL表示を区別する。npm公開メタデータは2026年10月にも更新されているが、個別機能の動作を保証する根拠とはしない。

### S03 Node.js

- [リリース状況](https://nodejs.org/en/about/previous-releases)：24系・22系がLTS、26系がCurrent。
- [配布一覧](https://nodejs.org/dist/index.json)：24.22.0（2026-10-14）、22.23.3（2026-09-23）、26.12.0（2026-10-14）とOS別の配布物。
- [ESM](https://nodejs.org/api/esm.html)：動的import、CommonJSとの連携、同期ESMに対するrequireの制約。
- [URL](https://nodejs.org/api/url.html#urlpathtofileurlpath-options)：パスをファイルURLへ変換する標準機能。
- [File system](https://nodejs.org/docs/latest-v24.x/api/fs.html#promises-api)：Node側のファイル操作とPromise API。

版を指定しないAPIページは確認時に26系の内容だった。検証対象の24系・22系で同じ機能が使えるかは、対象版の資料と実行で確認する。OS別配布物の存在はclonoのOS対応実績ではない。

### S04 現行remark系とAST

- [unified](https://github.com/unifiedjs/unified)：解析・変換・直列化の分離、Promiseを返すprocess、同期処理と非同期変換の関係。
- [mdast](https://github.com/syntax-tree/mdast)：Markdown ASTの見出し、コード、強調、リストなどのノード。
- [remark-directive 4.0.0のREADME](https://github.com/remarkjs/remark-directive/blob/4.0.0/readme.md)：三種類のディレクティブと、意味付けを別プラグインで行うこと。
- [同版の実装](https://github.com/remarkjs/remark-directive/blob/4.0.0/lib/index.js)：micromarkExtensions・fromMarkdownExtensions・toMarkdownExtensionsを登録する方式。
- [mdast-util-directive](https://github.com/syntax-tree/mdast-util-directive)：ディレクティブのASTと変換。
- [micromark-extension-directive](https://github.com/micromark/micromark-extension-directive)：構文拡張。
- [remark-rehype 11.1.2](https://github.com/remarkjs/remark-rehype/blob/11.1.2/readme.md)：HTML ASTへの変換、handlers、raw HTMLの扱い。
- [remark-stringify](https://github.com/remarkjs/remark/blob/main/packages/remark-stringify/readme.md)：ASTからMarkdownへの直列化。
- [markdown-it](https://github.com/markdown-it/markdown-it)：トークンとプラグイン・レンダリング拡張の代替候補。

remark-directive 4.0.0のREADMEには旧メジャー版の例やNode.js 16という説明が残っていた。必要なNode.js下限はそこだけから確定しない。4.0.0の公開メタデータはESM、unified ^11.0.0、mdast-util-directive ^3.0.0、micromark-extension-directive ^4.0.0を指定している。

unified系は複数の管理者による公式リポジトリと保守方針を持つ。2023〜2025年の公開メタデータの更新時刻も確認したが、個々の候補の保守速度や将来の継続を保証しない。

### S05 VFMの公開版

- [VFM仕様](https://docs.vivliostyle.org/vfm/vfm/)：CommonMark・GFMとの関係、ルビ・脚注などの記法。
- [公式リポジトリ](https://github.com/vivliostyle/vfm)：利用例、unified processorとしての利用。
- [2.7.2の公開メタデータ](https://registry.npmjs.org/@vivliostyle%2fvfm/2.7.2)：依存、Node要件、ライセンス、配布アーカイブ。
- [2.7.2の配布アーカイブ](https://registry.npmjs.org/@vivliostyle/vfm/-/vfm-2.7.2.tgz)：package.json、lib/index.js、lib/revive-parse.jsを読み取り確認。

アーカイブの`dist.integrity`と取得内容のSHA-512は、次の値で一致した。

```text
sha512-VHHhriZQGSis9z4Jd+E4IqqWlm+lC0IgeVSliVYDK/IfgIwyyeUKAKidc4En94U0Uf1BtuicvEu1Gv3s94KNJA==
```

lib/index.jsに`editPlugins`とmdastPlugins・mdastToHastHandlers・hastPluginsの編集口、remarkParseを使用するパイプライン、stringify内のprocessSyncを確認した。editPluginsの説明では、既存リストの先頭・末尾への追加が安定した編集方法とされている。内部の並び順へ依存する設計は注意が必要である。

package.jsonはunified ^9.2.0、remark-parse ^8.0.2、remark-rehype ^8.1.0を指定する。remark-directive 4の登録方式と世代が異なるため、VFMと現行remark系を単純に混在できるとは扱わない。VFM 2.7.2を基準にし、開発ブランチの機能を公開版の機能と混同しない。

### S06 Vivliostyle CLI

- [11.3.3の設定資料](https://github.com/vivliostyle/vivliostyle-cli/blob/v11.3.3/docs/config.md)：JavaScript設定、entry、entryContext、workspaceDir、copyAsset、documentProcessor。
- [11.3.3の公開メタデータ](https://registry.npmjs.org/@vivliostyle%2fcli/11.3.3)：Node.js >=22.12.0、VFM 2.7.2への依存、AGPL-3.0表示。

設定の入口があることと、clono側の構成ファイルを実際に共有できることは区別する。APIの型や設定資料に基づく候補であり、今回Vivliostyleを起動して接続確認はしていない。
