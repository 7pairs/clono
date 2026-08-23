# Vivliostyleとclonoの責務整理

- 状態: 調査済み
- 作成日: 2026-08-15
- 最終更新日: 2026-08-23

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
| 空行 | 可能性あり | 通常のMarkdown空行は可視要素として保持されない。空のHTML要素はVFMが保持し、CSSで一行相当の空白を表示できる | 著者向け記法を空白用の構造へ変換する候補とする。空白の寸法と表示はテーマCSSへ委譲する | 調査済み | [調査記録](research/vivliostyle-basic-presentation.md) |
| 強制改行 | 現時点では不要 | 標準Markdownのバックスラッシュによる強制改行を`br`へ変換できる | 標準記法を壊さずVFMへ渡す。独自記法や変換は実装しない | 調査済み | [調査記録](research/vivliostyle-basic-presentation.md) |
| 強制改ページ | 可能性あり | 空のHTML要素をVFMが保持し、CSSの`break-before: page`で後続の見出しまたは通常段落を次ページから開始できる | 著者向け記法を改ページ用の空要素へ変換し、改ページとして機能する最小限の基盤CSSを同梱する。物理的なページ分割はVivliostyleへ委譲する | 調査済み | [調査記録](research/vivliostyle-basic-presentation.md) |
| 段落の右寄せ | 必須 | 複数のMarkdown段落を含むHTMLコンテナをVFMが保持し、CSSの`text-align: right`でまとめて右寄せできる | 著者向け記法を、複数段落を保持できる右寄せ用コンテナへ変換する。右寄せとして機能する最小限の基盤CSSを同梱し、その他の紙面デザインは利用者のテーマCSSへ委譲する | 調査済み | [調査記録](research/vivliostyle-basic-presentation.md) |
| 定義リスト | 可能性あり | 標準記法は確認できない。`dl`、`dt`、`dd`と説明内のMarkdownをVFMが保持または変換し、CSSで字下げできる | 著者向け記法を`dl`、`dt`、`dd`へ変換する候補とする。説明内のMarkdown変換と表示はVFMとテーマCSSへ委譲する | 調査済み | [調査記録](research/vivliostyle-basic-presentation.md) |
| コラムなどの囲み枠 | 必須 | 複数段落と必要なMarkdown要素を含むHTMLコンテナをVFMが保持または変換し、VivliostyleとテーマCSSで囲み枠の表示と長いコラムのページ分割ができる。コラム内脚注も本文と連番でページ下部へ配置できる | 著者向け記法を、必須タイトルと複数のブロックを保持できるコラム用コンテナへ変換する候補とする。内部のMarkdownと脚注記法を壊さずVFMへ渡し、外観とページ分割はテーマCSSへ委譲する | 調査済み | [調査記録](research/vivliostyle-column.md) |

## 文書情報を扱う機能

| 機能 | 制作上の必要性 | Vivliostyleの対応 | `clono`の責務 | 調査状態 | 詳細 |
| --- | --- | --- | --- | --- | --- |
| 脚注 | 必須 | VFMの`dpub`モードは、本文とコラム内のPandoc風脚注をDPUB-ARIAの参照と脚注本文へ変換し、参照順に番号を付ける。一章一ファイルの構成では章ごとに番号が1へ戻る。Vivliostyle.jsはこの構造を認識し、脚注本文を参照ページの下部へ配置する | 脚注の参照、定義、インラインコード、リンクを壊さず後段へ渡す。独自脚注記法や脚注HTMLへの変換は実装しない | 調査済み | [調査記録](research/vivliostyle-footnotes.md)、[コラムとの結合](research/vivliostyle-column.md) |
| 見出し、画像、表、コードリストへの参照用IDの付与 | 必須 | 見出しはVFMの明示的なIDに対応できる。通常のMarkdown画像に指定したIDは`img`へ付与される。番号付き画像、表、コードリストでは、原稿に記述したIDとclassを持つ`figure`を保持でき、表とコードリストでは内側のMarkdownも変換できる | 見出しIDはVFMの記法を保持する。番号付き画像、表、コードリストの著者向け記法を、参照用IDと必要なclassを持つ`figure`構造へ変換し、タイトル参照用IDを持つ`figcaption`も生成する。未定義・重複IDの診断を担う候補とする | 調査済み | [見出し](research/vivliostyle-heading-references.md)、[画像](research/vivliostyle-figure-references.md)、[表](research/vivliostyle-table-references.md)、[コードリスト](research/vivliostyle-code-listing-references.md)、[結合検証](research/vivliostyle-reference-integration.md) |
| 見出し、画像、表、コードリストへの連番付与 | 必須 | すべてCSSカウンターで章ごとの番号を生成できる。図、表、コードリストのカウンターは、同じテーマと文書でも独立して動作する | 見出し番号、図番号、表番号、リスト番号を計算せず、VivliostyleとテーマCSSへ委譲する。統合テーマでは各カウンターを一つの`counter-reset`で初期化する | 調査済み | [見出し](research/vivliostyle-heading-references.md)、[画像](research/vivliostyle-figure-references.md)、[表](research/vivliostyle-table-references.md)、[コードリスト](research/vivliostyle-code-listing-references.md)、[結合検証](research/vivliostyle-reference-integration.md) |
| 参照用IDを使った見出し、画像、表、コードリストの番号参照 | 必須 | すべて`target-counter()`で同一・別原稿ファイルの番号を参照できる。統合テーマでも前方・後方の参照がPDF内部リンクになる | 著者向け記法を参照種別に応じた`href`とclassを持つ`a`要素へ変換し、参照を診断する。番号生成はVivliostyleへ委譲する | 調査済み | [見出し](research/vivliostyle-heading-references.md)、[画像](research/vivliostyle-figure-references.md)、[表](research/vivliostyle-table-references.md)、[コードリスト](research/vivliostyle-code-listing-references.md)、[結合検証](research/vivliostyle-reference-integration.md) |
| 参照用IDを使った見出し、画像、表、コードリストの番号とタイトルの参照 | 必須 | すべて`target-counter()`と`target-text()`で同一・別原稿ファイルの番号とタイトルを参照できる。統合テーマではタイトル取得先を`data-title-href`へ統一できる | 著者向け記法を、番号とクリック先を示す`href`、タイトル取得先を示す`data-title-href`、参照種別に応じたclassを持つ`a`要素へ変換し、参照を診断する。番号とタイトルの生成はVivliostyleへ委譲する | 調査済み | [見出し](research/vivliostyle-heading-references.md)、[画像](research/vivliostyle-figure-references.md)、[表](research/vivliostyle-table-references.md)、[コードリスト](research/vivliostyle-code-listing-references.md)、[結合検証](research/vivliostyle-reference-integration.md) |
| 表へのキャプションの付与 | 必須 | Markdown表を含む`figure`と`figcaption`をVFMが保持し、CSSで表番号を付けたキャプションを表の下へ表示できる | 番号付き表の著者向け記法を、IDを持つ`figure`、Markdown表、タイトル参照用IDを持つ`figcaption`へ変換する | 調査済み | [調査記録](research/vivliostyle-table-references.md) |
| 目次の生成 | 必須 | Vivliostyle CLIの自動目次で指定階層の見出し、タイトル、リンク先を抽出できる。設定の変換関数とテーマCSSを組み合わせ、掲載文書の選別、本文・付録・番号なし文書の番号、連続する紙面上のページ番号、PDF内部リンクを生成できる | 見出し一覧、番号、ページ番号、目次Markdownは生成せずVivliostyleへ委譲する。原稿順序、文書種別、目次への掲載有無を一つの書籍構造として表現し、必要に応じてVivliostyle設定へ変換する入力契約を担う候補とする | 調査済み | [調査記録](research/vivliostyle-table-of-contents.md) |
| 索引へ掲載するキーワードの指定と索引の生成 | 必須 | 文書化されたVFMとVivliostyle CLIには索引記法や自動生成設定を確認できない。生成済みの索引構造は保持でき、`target-counter()`による紙面上のページ番号と各索引マーカーへのPDF内部リンクを生成できる。同一ページの複数参照は自動統合されず、`2, 2`のように表示される | 索引語と読みの指定、読みの正規化、分類、並べ替え、項目の統合、索引マーカーと索引文書の生成を担う候補とする。初期仕様ではすべての出現位置を保持し、同一ページ番号の重複表示を許容する | 調査済み | [調査記録](research/vivliostyle-index.md) |

## 更新方針

- 調査単位ごとに個別の調査記録を`docs/research/`へ追加する
- 技術検証を行った場合は、再現に必要な最小限の入力、設定、依存関係、検証手順を`docs/research/fixtures/`へ保存し、個別の調査記録から参照する
- 検証に使用した依存関係の公式資料は、原則として対応するバージョンのタグまたはコミットへ固定して参照する。バージョンを固定できない公式Web資料を根拠とする場合は、参照日を記録する
- 調査結果を反映する際は、この文書の対応する行、調査状態、詳細へのリンクを同じPull Requestで更新する
- Vivliostyleの更新や要件の変更によって判断が変わった場合は、個別の調査記録とこの文書を同時に更新する
- すべての機能を`clono`かVivliostyleのどちらか一方へ割り当てることを目的としない。両者の組み合わせで要件を満たす場合は、それぞれの責務を明記する
