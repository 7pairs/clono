# Vivliostyleの脚注機能に関する調査

- 状態: 調査中
- 初回調査日: 2026-08-15
- 最終更新日: 2026-08-20
- 検証環境:
  - 実行環境: macOS、Node.js 24.19.0
  - HTML変換: `@vivliostyle/vfm` 2.7.0および2.7.2
  - PDF生成: `@vivliostyle/cli` 11.1.0
    - CLIが依存する`@vivliostyle/vfm` 2.7.0
    - CLIが使用するVivliostyle.js 2.44.1

## 背景

Thunder Clawが前作を執筆した当時、Vivliostyle Flavored Markdown（VFM）のPandoc風脚注記法は、脚注をページ下部ではなく章末にまとめて出力していた。ページ下部に脚注を出力するには、本文中へ次のHTMLを直接記述する必要があり、Markdown原稿の可読性を損ねていた。

```html
<span class="footnote">脚注。</span>
```

この問題を避けるため、前身のビルドスクリプトではPandoc風脚注記法を`span.footnote`へ変換していた。

現在のVivliostyleで同じ変換を再実装する必要があるか判断するため、VFMの脚注出力とVivliostyleによる組版を調査した。

## Thunder Clawの要件

- 脚注は章末ではなくページ下部へ出力する
- 原稿には可読性の高いPandoc風脚注記法を使用する
- 脚注内では、変数名やファイル名のためにインラインコードを使用したい
- 脚注内に記述したURLをリンクにしたい
- 10行程度の脚注を扱う。一ページにわたるような脚注は想定しない
- 同じ脚注を複数箇所から参照する機能は必要ない。同じ内容が必要な場合も、それぞれ別の脚注として記述する
- コラム内から参照した脚注も、本文と連続した番号で同じページ下部の脚注領域へ出力する
- 表内の脚注が必要になるかは未定とする
- 脚注番号は、可能であれば章ごとに1から振り直す。実現が困難な場合は書籍全体の連番を許容する

## 公式情報の確認

VFM 2.6以降では、Pandoc風脚注記法から生成するHTMLを`footnote`オプションで切り替えられる。

| モード | 生成される主なHTML | 組版結果 | 追加のテーマCSS |
| --- | --- | --- | --- |
| `pandoc` | 文書末尾の`section[role="doc-endnotes"]` | 文末脚注 | 不要 |
| `gcpm` | `span.footnote` | ページ下部の脚注 | `float: footnote`を含むCSSが必要 |
| `dpub` | `role="doc-noteref"`を持つ参照と`aside[role="doc-footnote"]` | ページ下部の脚注 | 不要 |

`pandoc`は調査時点のデフォルトであり、`footnote`オプションを省略すると文末脚注になる。`gcpm`は前身のスクリプトと同様の`span.footnote`を生成するが、theme-baseやtheme-techbookなど、`.footnote`へ`float: footnote`を適用するCSSを必要とする。

`dpub`はDPUB-ARIAのロールを持つHTMLを生成する。Vivliostyle.js 2.41以降はこのHTMLを脚注として認識し、テーマCSSなしでページ下部へ配置する。Vivliostyle公式の脚注ガイドでは、新規の構成には`dpub`が推奨されている。

プロジェクト全体で`dpub`を使用する場合は、Vivliostyleの設定へ次のように記述する。

```javascript
export default {
  vfm: {
    footnote: 'dpub',
  },
};
```

ファイル単位では、YAML frontmatterでも指定できる。

```yaml
---
vfm:
  footnote: dpub
---
```

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/vivliostyle-footnotes/)をリポジトリ内へ保存している。このfixtureは`clono`本体から独立したnpmプロジェクトであり、`package.json`と`package-lock.json`によって次の依存関係を固定する。

- `@vivliostyle/vfm` 2.7.0: PDF生成に使用するCLIが依存するバージョン
- `@vivliostyle/vfm` 2.7.2: 初回調査時に直接使用したバージョン
- `@vivliostyle/cli` 11.1.0

HTMLの検証プログラムは、VFM 2.7.0と2.7.2をnpm aliasによって同じ環境へインストールし、インラインコード、リンク、10行程度の脚注を含む同じ原稿を`dpub`モードで変換する。両方の出力が空でないこと、必要なHTML要素と内容を含むこと、および出力全体が一致することを自動で確認する。

PDFの検証プログラムは`@vivliostyle/cli` 11.1.0を使用する。このCLIは`@vivliostyle/vfm` 2.7.0とVivliostyle.js 2.44.1を使用する。既存のPDFを削除してからビルドを実行し、終了コードが0であることと、生成されたPDFが空でないことを自動で確認する。ページ下部への配置、表示、リンク、内容の欠落、章ごとの番号は、生成されたPDFをPDFビューアーで確認する。

初回調査では、直接実行したVFM 2.7.2によるHTML変換と、VFM 2.7.0へ依存するCLIによるPDF生成との間に、VFMのバージョン差があった。現在のfixtureでは両方のVFMを明示的に固定して比較し、検証原稿に対する出力が一致することを確認している。PDF生成は、CLIが依存するVFM 2.7.0を使用する工程として扱う。

最小の検証用原稿、Vivliostyle設定、検証プログラム、再現手順、期待結果、`package.json`と`package-lock.json`はfixtureへ保存している。生成したHTMLとPDF、`node_modules/`およびVivliostyleの一時ファイルはリポジトリへ追加しない。

### ページ下部への配置

[検証原稿](fixtures/vivliostyle-footnotes/basic.md)を`dpub`モードでPDFへ変換した。本文中に脚注番号が表示され、脚注本文が同じページの下部へ配置されることを目視で確認した。

### インラインコードとリンク

脚注内にインラインコードとリンクを記述した。

```markdown
[^note]: The variable `footnoteMode` is described in the [Vivliostyle footnotes guide](https://docs.vivliostyle.org/ja/cookbook/footnotes/).
```

検証プログラムによって、VFMがインラインコードを`code`要素、リンクを`a`要素として出力することを確認した。生成したPDFではインラインコードの表示が維持され、PDFビューアーからリンクを開けることも確認した。

### 10行程度の脚注

[検証原稿](fixtures/vivliostyle-footnotes/basic.md)の脚注定義を10行に分け、各行に説明文を含む原稿をPDFへ変換した。HTMLにすべての内容が含まれることを検証プログラムで確認し、脚注全体がページ下部の脚注領域へ配置され、内容の欠落や意図しない分割がないことをPDFで目視確認した。

Vivliostyleには脚注をページ間で分割するか制御する`footnote-policy`がある。ただし、Thunder Clawでは一ページにわたる脚注を想定しないため、現時点では追加の要件を定めない。

### 章ごとの番号

脚注を一つ含むMarkdownファイルを二つ用意し、それぞれを別の`entry`として一冊のPDFへ変換した。

```javascript
export default {
  vfm: {
    footnote: 'dpub',
  },
  entry: ['chapter-one.md', 'chapter-two.md'],
};
```

第1章と第2章のどちらでも、最初の脚注番号が1になることを確認した。一章を一つのMarkdownファイルとして別々の`entry`にする構成では、追加のCSSなしで章ごとに番号を振り直せる。

複数の章を一つのMarkdownファイルへ記述する構成は検証していない。Vivliostyle.js 2.41以降では、名前付きページと`counter-reset`を使用してページグループ単位で脚注番号をリセットできる。

## 再現方法

検証に使用する入力と、HTMLおよびPDFの確認手順は、[検証用fixtureのREADME](fixtures/vivliostyle-footnotes/README.md)を参照する。fixture内で`npm ci`を実行した後、HTMLは`npm run verify:html`、PDFは`npm run build:pdf`で再生成できる。PDFの組版結果はREADMEに記載した観点で目視確認する。

## 暫定的な責務判断

コラム内の脚注を除く基本要件は、現在のVFMとVivliostyleで満たせる。この範囲では、clonoに独自の脚注記法や`span.footnote`への変換を実装しない方針とする。

- 原稿にはVFMのPandoc風脚注記法を使用する
- Vivliostyleの設定で`footnote: 'dpub'`を明示する
- 一章を一つのMarkdownファイルとして構成し、章ごとに脚注番号を振り直す
- clonoは、入力された脚注の参照、定義、インラインコード、リンクを壊さずに後段へ渡す

コラム内から参照した脚注については、コラムの構文と出力HTMLが未決定のため、まだ要件を満たすことを確認できない。脚注全体の責務判断は暫定とし、コラムとの結合テストが完了した後に確定する。

## 未確認事項

- コラム内から脚注を参照した場合に、本文と連続した番号で同じページ下部へ出力できるか
- 表内で脚注が必要になった場合に、期待する配置になるか
- 複数の章を一つのMarkdownファイルへ記述した場合の番号リセット

コラム内の脚注は必須要件であるため、コラムの構文と出力HTMLを決定した後に結合テストで確認する。表内の脚注と一つのMarkdownファイルに複数章を含める構成は、具体的な必要性が生じた場合に調査する。

## 再調査する条件

- VFMのデフォルト脚注モードまたは`dpub`モードの出力契約が変更された場合
- Vivliostyle.jsによるDPUB-ARIA脚注の組版方法が変更された場合
- コラム機能との結合テストで期待する脚注配置や連番を実現できなかった場合
- Thunder Clawで、サイドノート、同じ脚注の複数参照、一ページにわたる脚注などの新しい要件が生じた場合

## 参照資料

- [脚注ガイド | Vivliostyle Documentation](https://docs.vivliostyle.org/ja/cookbook/footnotes/)（2026-08-15参照）
- [Vivliostyle Flavored Markdown 2.7.0](https://github.com/vivliostyle/vfm/blob/v2.7.0/docs/ja/vfm.md)
- [Vivliostyle Flavored Markdown 2.7.2](https://github.com/vivliostyle/vfm/blob/ae9e5564dcd72dfa4a146df317f8bcfe68d7f851/docs/ja/vfm.md)
- [Vivliostyle CLI 11.1.0の変更履歴](https://github.com/vivliostyle/vivliostyle-cli/blob/v11.1.0/CHANGELOG.md)
- [Vivliostyle.js 2.41.0のリリース](https://github.com/vivliostyle/vivliostyle.js/releases/tag/v2.41.0)
