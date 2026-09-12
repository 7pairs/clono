# 書籍プロジェクトの生成索引検証用fixture

## 目的

書籍プロジェクトの`publication`を正本として、掲載Markdownから索引指定を収集し、本文中の索引マーカーと巻末の索引Markdownを同時に生成できるか検証する。

このfixtureは、索引の最終仕様、著者向け記法、設定項目、HTML構造、class名または診断文言を確定するものではない。索引生成を既存の書籍プロジェクト変換へ組み込む前に、必要な処理段階とデータの流れが成立することを確認するプロトタイプである。

読みの正規化、分類、統合および決定的な並べ替えには、隣接する[index-normalization fixture](../index-normalization/)で検証した同じClojureScript実装を使用する。

## 候補となる設定

fixtureの[`project/clono.config.mjs`](project/clono.config.mjs)では、入力原稿ではなくclonoが生成する索引を`publication`へ次のように配置する。

```javascript
{
  type: 'index',
  path: 'generated/index.md',
  title: '索引',
  includeInToc: true,
}
```

- `path`は`outputRoot`からの相対Markdownパスであり、`sourceRoot`に同じファイルが存在してはならない
- `title`は生成する索引原稿の見出しに使用する
- `includeInToc`は、後続のVivliostyle設定との橋渡しで使用する候補として保持する
- 索引は最大一件とし、本文の章と付録より後ろへ配置する
- 索引より後ろには、後付原稿を配置できる

この設定形式は調査用の候補であり、現行の書籍プロジェクト仕様ではまだ使用できない。

## 候補となる索引記法

掲載Markdownでは、Generic DirectivesのText directiveを使用する。

```markdown
:index[バックナンバー]{reading="ばっくなんばー"}
```

ラベルを紙面へ表示する索引語、`reading`を必須の読みとして扱う。候補変換では各出現を、原稿順と原稿内の位置から採番した一意なマーカーへ置き換える。

```html
<span class="clono-index-marker" id="clono-index-marker-8">バックナンバー</span>
```

索引用の読みと並べ替えキーは本文へ出力しない。紙面上では指定した索引語だけを表示する。

## 検証する処理

fixtureは次の順で処理する。

```text
clono.config.mjsを読み込む
  → publicationと生成索引の配置を検証する
  → 掲載Markdownをpublication順に解析する
  → index directiveを原稿内の位置順に収集する
  → 読みを正規化し、項目を分類・統合・並べ替える
  → 本文中のdirectiveを一意な索引マーカーへ変換する
  → 全出現位置への相対HTMLリンクを持つ索引Markdownを生成する
  → 入力原稿ツリーをミラーして変換済み原稿と索引を出力する
  → VFMでHTMLへ変換し、構造とリンクを検証する
```

掲載されていないMarkdownは、入力原稿ツリーの通常ファイルとして同じ相対位置へコピーするが、索引収集の対象にはしない。

## 生成索引の候補構造

索引Markdownは、索引見出しと、空でない分類だけを出力する。各項目は、すべての出現位置を原稿順に保持する。

```html
<section class="clono-index-group clono-index-group-ha">
<h2>は行</h2>
<dl class="clono-index-list">
<div class="clono-index-entry">
<dt>バックナンバー</dt>
<dd><a class="clono-index-page" href="../nested/chapter-two.html#clono-index-marker-8" aria-label="バックナンバーの出現1"></a></dd>
</div>
</dl>
</section>
```

索引が`generated/index.md`、参照先が`nested/chapter-two.md`であるため、出力HTML間の相対URLは`../nested/chapter-two.html`となる。相対パスはURLの各要素をパーセントエンコードしてから、マーカーIDをフラグメントとして追加する。

## 検証環境

- 実行確認: macOS 26.6.2、Node.js 24.19.0、Temurin JDK 21.0.12
- 対応範囲: Node.js 22.13.0以降の22系、または24系
- shadow-cljs 3.4.12
- `mdast-util-from-markdown` 2.0.3
- `mdast-util-to-markdown` 2.1.2
- `micromark-extension-directive` 4.0.0
- `mdast-util-directive` 3.1.0
- VFM 2.7.0
- `node-html-parser` 9.0.1

正確な依存関係は`package.json`と`package-lock.json`で固定する。VFMと`node-html-parser`は、候補となる生成MarkdownのHTML結合検証だけに使用する開発依存関係である。

## 実行方法

```shell
set -eu
npm ci
npm run verify
```

生成結果を目視する場合は、次のコマンドを使用する。

```shell
set -eu
npm run build:project
```

生成した原稿ツリーは`project/build/`へ出力する。`project/build/`、`target/`、`.shadow-cljs/`および`node_modules/`は生成物であり、Gitの管理対象には含めない。

## 自動検証

自動テストは、次を確認する。

- 実際の`clono.config.mjs`を読み込み、生成索引を章・付録と後付の間に一件だけ配置できる
- 複数の生成索引、入力ファイルと同じ生成先、および索引より後ろにある章・付録を拒否する
- 索引指定が存在するのに生成索引が設定されていない場合は拒否する
- 同じ表示語へ異なる正規化済み読みが指定された場合は拒否する
- 索引指定がない場合も、明示された空の索引を生成できる
- 掲載Markdownだけから、原稿順と原稿内の位置順で索引指定を収集する
- 同じ表示語と読みを一項目へ統合し、すべての出現位置を保持する
- 正規化fixtureで検証した分類と決定的な並べ替えを適用する
- 各索引指定を、表示語だけを持つ一意な本文マーカーへ変換する
- ネストした原稿および空白や`#`を含む原稿パスについて、各出現位置への完全な相対HTML URLを生成する
- VFM変換後も、索引の分類、項目、ページ番号用リンクおよび本文マーカーが保持される
- HTMLで意味を持つ文字を索引語へ含めても、生成するraw HTMLで要素や属性として解釈されない
- 生成索引が入力原稿ツリーへ書き込まれず、掲載外Markdownが内容を変えずにコピーされる

## 検証結果

2026年9月12日にmacOS 26.6.2、Node.js 24.19.0およびTemurin JDK 21.0.12で`npm run verify`を実行し、4テスト、89アサーションがすべて成功した。

- `publication`に生成索引を一件の独立した要素として置き、入力原稿に存在しないMarkdownを指定位置へ生成できた
- 掲載Markdownにある10件の索引指定を原稿順と入力位置順に収集し、一意な本文マーカーへ変換できた
- 同じ表示語を一項目へ統合し、原稿をまたぐすべての出現位置を元の順序で保持できた
- 索引正規化fixtureの分類と並べ替えを適用し、空の分類を省略できた
- 生成索引のサブディレクトリから、ルート原稿、ネストした原稿、および空白と`#`を含む原稿への相対HTML URLを生成できた
- VFM変換後も、各リンクの完全な`documentPath#targetId`とリンク先の本文マーカーを対応付けられた
- 設定、索引指定または読みが不正な場合に、本文変換と索引生成を開始する前のデータ処理で拒否できた

以上から、索引の収集、正規化、統合、並べ替え、本文マーカーへの変換、および生成索引Markdownの作成を、書籍プロジェクト変換の公開前処理としてまとめて実行できる見通しが立った。

## 依存関係の監査

2026年9月12日に`npm audit`を実行した結果、固定した依存関係全体に3件のmoderateと3件のhighが報告された。`npm audit --omit=dev`では0件だった。

VFM、`node-html-parser`およびshadow-cljsは検証専用の開発依存関係であり、clono本体のproduction依存関係として追加したものではない。fixtureは外部から受け取った信頼できない入力の処理には使用しない。調査対象との対応と再現性を維持するため、監査結果だけを理由に固定バージョンを変更しない。

## 検証範囲の境界

このfixtureは、候補となる索引Markdownの生成までを検証する。Vivliostyleによる紙面上のページ番号生成、PDF内部リンクおよび同一ページ番号の重複表示は、既存の[Vivliostyle索引fixture](../vivliostyle-index/)で検証している。

既存の書籍プロジェクト基盤で実装済みの、診断の一括収集、所有マーカー、staging、backup、排他ロック、基盤CSSのコピーおよび安全な公開処理も再検証しない。プロトタイプは索引固有の検証と変換をすべて完了してから、fixture専用の`project/build/`を再生成する。

次の機能も対象外とする。

- 読みの省略と自動取得
- ユーザー定義の分類
- 階層索引、ページ範囲、主要ページおよび項目間相互参照
- 同一ページにある同じ項目のページ番号統合
- 複数ページにまたがる大規模な索引
- Vivliostyle設定を`publication`から生成するヘルパー
- 生成したマーカーIDと、著者が指定したHTML IDまたはほかのclono機能が生成するIDとの衝突検査

## 参照資料

- [索引の読み正規化・分類・並べ替えに関する調査](../../index-normalization.md)
- [Vivliostyleの索引に関する調査](../../vivliostyle-index.md)
- [書籍プロジェクトの生成済み原稿ツリーに関する調査](../../book-project-output-tree.md)
- [書籍プロジェクト仕様](../../../specifications/book-project.md)
- [Vivliostyleとの責務整理](../../../vivliostyle-responsibilities.md)
