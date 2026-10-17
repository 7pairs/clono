<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# clono

clonoは、Vivliostyleで技術同人誌を制作するために、不足する機能を補うツールである。開発者と、開発者が所属する技術同人誌サークル「Thunder Claw」の用途を優先して開発する。

## 開発について

開発の進め方と関連文書は、[開発フロー](docs/development-workflow.md)を参照する。

本体は可能な限りClojureScriptで実装し、Node.js上で実行する。shadow-cljsでESMを生成し、本ごとのJavaScript拡張は.mjsファイルで扱う方針である。環境要件と制約は[実装環境の仕様](docs/specs/implementation-environment.md)、採用理由は[ADR 0002](docs/decisions/0002-choose-implementation-environment.md)にまとめている。

製品の利用手順と正式な開発・検証コマンドは未整備である。実装環境の調査・検証を再現する場合は、[検証資料](research/implementation-environment/README.md)と、そこから参照する検証結果を確認する。

## ライセンス

Copyright 2026 HASEBA Junya (7pairs)

clonoはApache License 2.0（`Apache-2.0`）で配布する。正式な条件は[LICENSE](LICENSE)、適用範囲と例外・表示ルールは[ライセンス方針](docs/specs/licensing.md)を参照する。

第三者由来の内容は、それぞれの権利とライセンス条件に従う。利用者の原稿や生成した本には、clonoを使ったという理由でApache-2.0を適用せず、本の配布・頒布形式も制限しない。
