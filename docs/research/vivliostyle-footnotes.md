# Vivliostyleの脚注機能に関する調査

- 状態: 調査中
- 初回調査日: 2026-08-15
- 最終更新日: 2026-08-16
- 確認工程:
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

リポジトリ外の一時プロジェクトで、直接インストールした`@vivliostyle/vfm` 2.7.2を使用し、同じPandoc風脚注を`pandoc`、`gcpm`、`dpub`の各モードによってHTMLへ変換した。

PDFの検証には`@vivliostyle/cli` 11.1.0を使用した。このCLIは`@vivliostyle/vfm` 2.7.0とVivliostyle.js 2.44.1を使用するため、HTML変換とPDF生成は同一バージョンのVFMによる一つの工程ではない。

依存関係の差を確認した後、CLIが依存する`@vivliostyle/vfm` 2.7.0でも、インラインコード、リンク、10行程度の脚注を含む同じ検証原稿を`dpub`モードでHTMLへ変換した。検証した原稿では、VFM 2.7.0と2.7.2の出力が一致することを確認した。

最小の検証用原稿、Vivliostyle設定、再現手順、期待結果は[検証用fixture](fixtures/vivliostyle-footnotes/)に保存する。生成したHTML、PDF、画像、インストールした依存関係はリポジトリへ追加しない。

### ページ下部への配置

次の原稿を`dpub`モードでPDFへ変換した。

```markdown
This is the body text[^note].

[^note]: This is the footnote at the bottom of the page.
```

本文中に脚注番号が表示され、脚注本文が同じページの下部へ配置されることを目視で確認した。

### インラインコードとリンク

脚注内にインラインコードとリンクを記述した。

```markdown
[^note]: The variable `footnoteMode` is described in the [Vivliostyle footnotes guide](https://docs.vivliostyle.org/en/cookbook/footnotes/).
```

VFMがインラインコードを`code`要素、リンクを`a`要素として出力することを確認した。生成したPDFではインラインコードの表示が維持され、URLがリンク注釈として含まれることも確認した。

### 10行程度の脚注

脚注定義を10行に分け、各行に説明文を含む原稿をPDFへ変換した。脚注全体がページ下部の脚注領域へ配置され、内容の欠落や意図しない分割がないことを確認した。

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

検証に使用する入力と、HTMLおよびPDFの確認手順は、[検証用fixtureのREADME](fixtures/vivliostyle-footnotes/README.md)を参照する。

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

- [脚注ガイド | Vivliostyle Documentation](https://docs.vivliostyle.org/ja/cookbook/footnotes/)
- [Vivliostyle Flavored Markdown | Vivliostyle Documentation](https://docs.vivliostyle.org/ja/vfm/vfm/)
- [Vivliostyle CLIの変更履歴](https://github.com/vivliostyle/vivliostyle-cli/blob/main/CHANGELOG.md)
- [Vivliostyle.js 2.41.0のリリース](https://github.com/vivliostyle/vivliostyle.js/releases/tag/v2.41.0)
