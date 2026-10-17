<!--
Copyright 2026 HASEBA Junya (7pairs)
SPDX-License-Identifier: Apache-2.0
-->

# ADR 0002：ClojureScriptとNode.jsによる実装環境を採用する

- 状態：合意済み（独立レビュー・二人の確認完了）
- 判断日：2026-10-17

## 1. 背景

clonoは、Markdown原稿を加工し、Vivliostyleで本を制作するための前処理CLIである。章を横断する情報の収集、独自記法の処理、通常のMarkdown要素の出力変更を、本ごとの設定とJavaScript拡張で扱える構成を目指す。

開発者はClojureを使っており、ClojureScriptを第一候補にしたいと考えている。clonoという名前にもClojureScriptが由来として含まれる。多少の不便は許容するが、特殊な連携処理によって理解・保守が難しくなる場合は採用を見直す。

利用者にはNode.jsだけを要求し、JDKやClojureScriptのビルド環境は要求しない。macOSを最初の動作確認対象とし、Windows・Linuxの実行確認は後続のCI整備で行う。

選定の根拠は、[公式資料の調査](../../research/implementation-environment/README.md)と[検証結果](../../research/implementation-environment/results/verification.md)に記録している。製品機能、公開する拡張API、正式なテスト環境やCIは今回確定しない。

## 2. 決定

本体は可能な限りClojureScriptで書き、Node.js上で実行する。極端に複雑になる、または実現できない箇所がある場合は、JavaScriptを使う理由と範囲を相談する。検証で用いたClojureScriptとJavaScriptの責務分担は、そのまま製品へ固定しない。

ビルドにはshadow-cljsを使い、ESMを出力する。利用者のJavaScript拡張はまず.mjsファイルとし、本体や拡張の再ビルドなしで読み込む。CommonJSは特別な要件が出た場合に再検討する。

Node.jsの対応範囲は`>=24.22.0 <25`、開発用の固定版はまず24.22.0とする。最低版は、必要な機能の導入版を特定した値ではなく、実行確認した版を出発点として選んだ。以降の24系を対応対象とすることと、各版を検証済みとすることは区別する。

Markdownは現行remark系でASTとして加工し、Markdownへ再出力してVFMでHTML化する。脚注はdpubを標準とし、コラム内部もMarkdownとしてVFMへ渡す。元原稿を保持し、内容と組版上の意味を保つ範囲で、再出力による表記の変化を許容する。

採用する構成とバージョンの基準、環境要件、制約は[実装環境の仕様](../specs/implementation-environment.md)にまとめる。

## 3. 採用理由と見送った案

### 3.1 ClojureScriptとshadow-cljs

ESM・CommonJSの開発用／配布用ビルド、JavaScriptデータの受け渡し、同期・Promiseを返す拡張の呼び出し、REPL、エラーの追跡を検証した。ビルド用ツールとソース、Javaを使えない分離先でも、前処理を実行できた。

ASTを扱う小さなコードを題材に、開発者が必要な範囲で処理を理解できることも確認した。ClojureScriptを見送るほどの理解・保守上の問題は見つからず、JDKとデバッグ補助を開発時に使う負担は許容できると判断した。代替言語による実装比較は行っていない。

### 3.2 ESM

ESMとCommonJSはどちらも成立した。使用する現行remark系の形式とそろい、ビルド済み関数をJavaScriptから呼びやすいESMを選んだ。両形式の製品対応を維持する必要は現時点でないため、CommonJSは採用対象に含めない。

### 3.3 remarkによる前処理とVFM

現行remark系では、ディレクティブをASTとして扱い、コード中の文字列と区別して変換できた。VFMのパーサーは旧世代なので、両者のパーサーを一つのprocessorへ混在させない経路を選ぶ。

現行remarkから直接HTMLへ出す標準構成では脚注が末尾注になり、必須のページ下部脚注を満たさなかった。Markdownへ戻してVFMへ渡す経路では、その既存機能を利用できたため、汎用の脚注変換を自作する案は採らない。

コラム内部を先にHTML化すると内部の注が末尾注になったが、囲みのHTMLとMarkdownの子要素を分けて再出力する追加検証では、ページ下部脚注が成立した。この結果を採用判断の根拠とする。章参照・索引・H1拡張との製品上の統合方法は、今後の設計で決める。

dpubとgcpmは両方成立した。検証したdpubは脚注化・番号表示用の独自CSSを追加せずに成立したため、標準に選んだ。gcpmを切り替える機能の提供は今回確定しない。

## 4. 許容する負担と依存リスク

JavaScriptのオブジェクト・配列、Promise、Node.jsのモジュール形式を理解して扱う必要がある。開発にはJDKを使い、配布用source mapの位置を追う際は補助パッケージも必要になる。これらは開発者が許容できる負担として合意した。

VFM配下のtrim 0.0.1には、特定の入力で処理が極端に遅くなる問題が残る。修正版0.0.3の動作互換性は確認できたが、今回の最小配布例では利用者側への適用が成立しなかった。自作原稿をローカルで扱う前提と、差し替えを維持する負担を踏まえ、当面0.0.1を条件付きで許容する。

開発環境だけを修正版にする案は採らず、調査用の依存も元へ戻した。上流の更新は、新しいremark-parseに対応したVFMの公開版を確認する。更新を待つことを理由に、開発自体を停止する方針にはしない。

残る8件のアドバイザリも、確認した呼び出し方・設定・実行場所を維持する前提で当面許容する。発生条件に一致しなかったことを安全性の保証へ広げない。詳細な根拠は[依存リスクの調査と結論](../../research/implementation-environment/results/verification.md#11-手順4の依存リスク確認報告の再取得)、現在の対応状態と見直す時点は[backlog](../backlog.md#依存の脆弱性への対応を見直す)を参照する。

## 5. 影響と限界

初期の対応範囲をNode.js 24系とESMに絞り、検証の組み合わせを抑える。macOSでの成功だけでWindows・Linuxの動作を保証しない。ほかのNode.js系列やCommonJSが必要になった場合は、根拠となる検証を追加して再検討する。

Markdownの再出力には原稿と生成物の差分が増える負担があり、VFMへの受け渡しにも制約がある。今回の検証例で成立したことを、任意のHTMLやすべてのMarkdown入力の保証にはしない。

採用は実装の基準を決めるものであり、調査用のコード配置・公開関数・コマンドを製品仕様へ固定しない。正式なテスト環境、CI、PRレビューツール、配布・リリース方法は後続で決める。第三者由来の内容と配布時の表示は、[ライセンス方針](../specs/licensing.md)に従って確認する。

## 6. 関連文書

- [選定の手順書](../plans/0002-choose-implementation-environment.md)
- [実装環境の仕様](../specs/implementation-environment.md)
- [検証計画とテスト項目](../specs/implementation-environment-verification.md)
- [検証結果](../../research/implementation-environment/results/verification.md)
- [参照資料・公開パッケージ情報](../../research/implementation-environment/sources.md)
