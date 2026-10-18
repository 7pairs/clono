<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# コメントを書けないファイルの表示

[ライセンス方針](docs/specs/licensing.md)に基づき、次の管理対象ファイルの表示を記載する。

| 対象 | 著作権表示 | ライセンス |
|---|---|---|
| `package.json` | Copyright 2026 HASEBA Junya (7pairs) | Apache-2.0 |
| `.node-version` | Copyright 2026 HASEBA Junya (7pairs) | Apache-2.0 |

`package-lock.json`はnpmが生成する依存情報であり、表示のために直接編集しない。各依存のライセンスは、そのパッケージに含まれる表示に従う。開発依存の選定時の確認結果は、[実装環境の調査資料](research/implementation-environment/sources.md)を参照する。

`target/generated/clono/version.cljs`の表示は、[生成処理](scripts/generate-version.mjs)で管理する。第三者由来の内容を含むビルド成果物の表示・同梱方法は、実際の配布構成に応じて確認する。
