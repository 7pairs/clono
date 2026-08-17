# Vivliostyleとclonoの責務整理

- 状態: 調査中
- 作成日: 2026-08-15
- 最終更新日: 2026-08-17

## 目的

Thunder Clawの書籍制作に必要な機能について、現在のVivliostyleで実現できる範囲を調査し、Vivliostyleと`clono`の責務を整理する。

この文書は現在の責務判断を示す正本とする。各判断の根拠、技術検証、未確認事項は、表からリンクする個別の調査記録へ残す。

「制作上の必要性」はThunder Clawの制作工程でその結果が必要かを表し、`clono`での実装が必要であることを意味しない。Vivliostyleで要件を満たせる機能は、[プロジェクト憲章](project-charter.md)に従ってVivliostyleへ委譲する。

## 表の見方

### 制作上の必要性

- 必須: Thunder Clawの次回作で必要になる
- 可能性あり: 次回作で必要になる可能性がある
- 現時点では不要: 現在把握している次回作の要件には含まれない

### 調査状態

- 未調査: Vivliostyleの対応状況と責務をまだ調査していない
- 調査中: 一部の要件を確認したが、責務判断の確定に必要な調査が残っている
- 調査済み: 公式情報と必要な技術検証に基づいて責務を判断した

## 表現機能

| 機能 | 制作上の必要性 | Vivliostyleの対応 | `clono`の責務 | 調査状態 | 詳細 |
| --- | --- | --- | --- | --- | --- |
| 空行 | 可能性あり | 未調査 | 未決定 | 未調査 | — |
| 強制改行 | 現時点では不要 | 未調査 | 未決定 | 未調査 | — |
| 強制改ページ | 可能性あり | 未調査 | 未決定 | 未調査 | — |
| 段落の右寄せ | 必須 | 未調査 | 未決定 | 未調査 | — |
| 定義リスト | 可能性あり | 未調査 | 未決定 | 未調査 | — |
| コラムなどの囲み枠 | 必須 | 未調査 | 未決定 | 未調査 | — |

## 文書情報を扱う機能

| 機能 | 制作上の必要性 | Vivliostyleの対応 | `clono`の責務 | 調査状態 | 詳細 |
| --- | --- | --- | --- | --- | --- |
| 脚注 | 必須 | VFMの`dpub`モードで基本要件に対応できる。コラム内の脚注は未確認 | 暫定的に、脚注の参照と定義、その内容を壊さず後段へ渡す。独自記法やHTMLへの変換は実装しない | 調査中 | [調査記録](research/vivliostyle-footnotes.md) |
| 見出し、画像、表、コードリストへの参照用IDの付与 | 必須 | 見出しはVFMの明示的なIDに対応できる。画像はIDを持つ`figure`構造をVFMが生成できる。表は、原稿に記述したIDとclassを持つ`figure`および`figcaption`を保持し、その内側のMarkdown表を`table`へ変換できる。コードリストは未調査 | 見出しIDはVFMの記法を保持する。番号付き画像はIDを持つ`figure`へ変換する。番号付き表の著者向け記法は、IDとclassを持つ`figure`、Markdown表、タイトル参照用IDを持つ`figcaption`へ変換する。未定義・重複IDの診断を担う候補とする。コードリストは未決定 | 調査中 | [見出し](research/vivliostyle-heading-references.md)、[画像](research/vivliostyle-figure-references.md)、[表](research/vivliostyle-table-references.md) |
| 見出し、画像、表、コードリストへの連番付与 | 必須 | 見出し、画像、表はCSSカウンターで章ごとの番号を生成できる。コードリストは未調査 | 見出し番号、図番号、表番号を計算せず、VivliostyleとテーマCSSへ委譲する。コードリストは未決定 | 調査中 | [見出し](research/vivliostyle-heading-references.md)、[画像](research/vivliostyle-figure-references.md)、[表](research/vivliostyle-table-references.md) |
| 参照用IDを使った見出し、画像、表、コードリストの番号参照 | 必須 | 見出し、画像、表は`target-counter()`で同一・別原稿ファイルの番号を参照できる。コードリストは未調査 | 著者向け記法を参照用の`a`要素へ変換し、参照を診断する。番号生成はVivliostyleへ委譲する。コードリストは未決定 | 調査中 | [見出し](research/vivliostyle-heading-references.md)、[画像](research/vivliostyle-figure-references.md)、[表](research/vivliostyle-table-references.md) |
| 参照用IDを使った見出し、画像、表、コードリストの番号とタイトルの参照 | 必須 | 見出し、画像、表は`target-counter()`と`target-text()`で同一・別原稿ファイルの番号とタイトルを参照できる。表ではタイトル取得用にキャプションを別のIDで参照する。コードリストは未調査 | 著者向け記法を参照用の`a`要素へ変換し、参照を診断する。表では表本体とキャプションへの参照を生成する。番号とタイトルの生成はVivliostyleへ委譲する。コードリストは未決定 | 調査中 | [見出し](research/vivliostyle-heading-references.md)、[画像](research/vivliostyle-figure-references.md)、[表](research/vivliostyle-table-references.md) |
| 表へのキャプションの付与 | 必須 | Markdown表を含む`figure`と`figcaption`をVFMが保持し、CSSで表番号を付けたキャプションを表の下へ表示できる | 番号付き表の著者向け記法を、IDを持つ`figure`、Markdown表、タイトル参照用IDを持つ`figcaption`へ変換する | 調査済み | [調査記録](research/vivliostyle-table-references.md) |
| 目次の生成 | 必須 | 未調査 | 未決定 | 未調査 | — |
| 索引へ掲載するキーワードの指定と索引の生成 | 必須 | 未調査 | 未決定 | 未調査 | — |

## 更新方針

- 調査単位ごとに個別の調査記録を`docs/research/`へ追加する
- 技術検証を行った場合は、再現に必要な最小限の入力、設定、依存関係、検証手順を`docs/research/fixtures/`へ保存し、個別の調査記録から参照する
- 調査結果を反映する際は、この文書の対応する行、調査状態、詳細へのリンクを同じPull Requestで更新する
- Vivliostyleの更新や要件の変更によって判断が変わった場合は、個別の調査記録とこの文書を同時に更新する
- すべての機能を`clono`かVivliostyleのどちらか一方へ割り当てることを目的としない。両者の組み合わせで要件を満たす場合は、それぞれの責務を明記する
