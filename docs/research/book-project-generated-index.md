# 書籍プロジェクトの生成索引に関する調査

- 状態: 調査済み
- 調査日: 2026-09-12
- 最終更新日: 2026-09-12
- 検証環境:
  - OS: macOS 26.6.2
  - Node.js: 24.19.0
  - npm: 11.17.0
  - JDK: Temurin 21.0.12
  - ClojureScriptビルド: shadow-cljs 3.4.12
  - Markdown解析: `mdast-util-from-markdown` 2.0.3
  - Markdown直列化: `mdast-util-to-markdown` 2.1.2
  - directive構文拡張: `micromark-extension-directive` 4.0.0
  - directive用mdast拡張: `mdast-util-directive` 3.1.0
  - HTML変換: `@vivliostyle/vfm` 2.7.0
  - HTML検証: `node-html-parser` 9.0.1

## 背景

[Vivliostyleの索引に関する調査](vivliostyle-index.md)では、VFMとVivliostyle CLIの文書化された公開インターフェースに、clonoの要件を満たす索引自動生成機能が確認できないことが分かった。一方、clonoが本文中の索引マーカーと索引文書を事前生成すれば、紙面上のページ番号とPDF内部リンクをVivliostyleへ委譲できることも確認した。

[索引の読み正規化・分類・並べ替えに関する調査](index-normalization.md)では、著者が指定した自然な読みから並べ替えキーを導出し、英数字と五十音の行への分類、同じ項目の統合、および決定的な並べ替えを行えることを確認した。

ただし、これらは独立した検証だった。索引をclonoの書籍プロジェクトへ組み込むには、次の処理を一つの流れとして成立させる必要がある。

1. `publication`を正本として掲載Markdownを決定する
2. 掲載Markdownから索引指定を原稿順に収集する
3. 本文中の索引指定を一意なマーカーへ変換する
4. 収集結果を正規化、分類、統合および並べ替えする
5. 各出現位置へのリンクを持つ索引Markdownを生成する
6. 変換済み原稿ツリーの所定位置へ索引を配置する

今回の調査では、この一連の処理を既存の書籍プロジェクト方式へ組み込めるか検証した。

## 調査目的

- 入力原稿に存在しない生成索引を`publication`の一要素として表現できるか確認する
- 掲載Markdownだけを対象に、`publication`順と原稿内の位置順で索引指定を収集できるか確認する
- 索引指定を本文表示を変えない一意なマーカーへ変換できるか確認する
- 正規化fixtureで検証した分類、統合および並べ替えを書籍全体の収集結果へ適用できるか確認する
- 生成索引から別ディレクトリの各原稿へ、正しい相対HTML URLを生成できるか確認する
- VFM変換後も索引構造、リンクおよび本文マーカーが保持されるか確認する
- 設定または索引指定が不正な場合に、変換済み原稿ツリーを生成する前に処理を中止できるか確認する

この調査は、索引の最終仕様、著者向け記法、設定項目、生成するHTML構造、class名または診断文言を確定するものではない。

## 技術検証

調査結果を再検証できるように、[検証用fixture](fixtures/book-project-generated-index/)をリポジトリ内へ保存している。このfixtureはclono本体から独立したClojureScriptおよびnpmプロジェクトであり、`package.json`と`package-lock.json`によって依存関係を固定する。

fixtureは実際の`clono.config.mjs`を読み込み、次の順序で候補処理を実行する。

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

掲載されていないMarkdownは通常ファイルとして同じ相対位置へコピーするが、索引収集の対象にはしない。

## `publication`における生成索引

fixtureでは、入力原稿ではなくclonoが生成する索引を、次の候補形式で`publication`へ配置した。

```javascript
{
  type: 'index',
  path: 'generated/index.md',
  title: '索引',
  includeInToc: true,
}
```

候補形式には次の規則を設けた。

- `path`、`title`および`includeInToc`を必須とする
- `path`は`outputRoot`からの相対Markdownパスとする
- `sourceRoot`の入力ファイルと同一または祖先・子孫関係になる`path`は拒否する
- 生成索引は最大一件とする
- 本文の章と付録より後ろへ配置する
- 生成索引より後ろには後付原稿を配置できる

fixtureでは、前付、二つの本文原稿、付録、生成索引、後付をこの順で配置できた。生成索引を複数指定した場合、生成先が入力ファイルと同一または祖先・子孫関係になった場合、および索引より後ろへ本文または付録を置いた場合は、出力前に拒否できた。パス要素の境界を使って判定するため、名前の接頭辞が一致するだけの別パスは衝突として扱わない。

この`type: 'index'`は調査用の候補である。現行の[書籍プロジェクト仕様](../specifications/book-project.md)ではまだ使用できない。

## 索引指定の収集と本文マーカー

著者向け記法の候補には、Generic DirectivesのText directiveを使用した。

```markdown
:index[バックナンバー]{reading="ばっくなんばー"}
```

ラベルを紙面へ表示する索引語、`reading`を必須の読みとして扱った。ラベルは空でないプレーンテキストに限定し、`reading`以外の属性は受理しない候補とした。

掲載Markdownを`publication`順に解析し、各原稿内では入力位置順に索引指定を収集できた。fixtureに含めた掲載外Markdownの索引指定は収集されず、ファイル内容も変更されなかった。

各索引指定は、書籍全体の収集順から採番した一意なマーカーへ変換した。

```html
<span class="clono-index-marker" id="clono-index-marker-8">バックナンバー</span>
```

読みと並べ替えキーは本文マーカーへ出力しない。VFM変換後も、class、IDおよび表示語が保持された。HTMLで意味を持つ文字を索引語へ含めた場合も、生成するraw HTMLで別の要素または属性として解釈されないようにエンコードできた。

## 正規化、分類、統合および並べ替え

読みの処理には、[索引正規化fixture](fixtures/index-normalization/)と同じClojureScript実装を使用した。書籍全体から収集した10件の索引指定を、次の7項目へ統合して並べ替えられた。

1. `Android`
2. `API`
3. `一気`
4. `五木`
5. `画像`
6. `コラム`
7. `バックナンバー`

空でない「英数字」「あ行」「か行」「は行」だけが生成された。`一気（いっき）`と`五木（いつき）`は並べ替えキーがともに`いつき`になるが、正規化済み読みと表示語を後続の比較キーとして使用することで、常に同じ順序になった。

同じ表示語と正規化済み読みを持つ指定は一項目へ統合し、原稿をまたぐすべての出現位置を元の順序で保持できた。同じ表示語へ異なる正規化済み読みが指定された場合は、どちらかを暗黙に採用せず拒否できた。

## 索引Markdownと相対URL

生成索引は、見出しと空でない分類だけを持つMarkdownとした。分類、項目および出現位置は、VFMが保持できるraw HTMLで表現した。

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

索引原稿の`generated/index.md`から、ルート原稿、ネストした原稿および特殊文字を含む原稿へ、それぞれ完全な`documentPath#targetId`を生成できた。ファイルシステム上の相対パスを計算した後、URLの各パス要素をUTF-8でパーセントエンコードし、`/`を区切り文字として保持した。

たとえば、`appendix #notes.md`へのリンクは次の形になった。

```text
../appendix%20%23notes.html#clono-index-marker-10
```

VFM変換後の各リンクについて、完全な`href`と、参照先HTMLに実在するマーカーIDを対応付けて検証できた。

索引のタイトルはmdastの`text`ノードとして生成した。`索引 *draft*`のようにMarkdownで意味を持つ文字を含むタイトルも、意図しない強調へ変わらずプレーンテキストとして直列化できた。

## 失敗契約の候補

fixtureでは、次の条件を変換と索引生成の前に検出した。

- `publication`に生成索引が複数ある
- 生成索引のパスが入力ファイルと同一または祖先・子孫関係になる
- 生成索引より後ろに本文または付録がある
- 索引指定があるのに生成索引が設定されていない
- 同じ表示語へ異なる正規化済み読みが指定されている

索引指定が一件もない場合は、明示的に設定された空の索引を生成できた。これは、空の索引を許可するという最終仕様を決めるものではなく、索引の有無と設定の有無を区別できることの確認である。

プロトタイプは、索引固有の設定検証、収集、正規化および変換をすべて完了してから、fixture専用の`project/build/`を再生成する。既存の書籍プロジェクト基盤が持つ診断の一括収集、所有マーカー、staging、backup、排他ロックおよび安全な公開処理は再実装していない。

## 評価

書籍構造、索引指定の収集順、読みの正規化、本文変換、索引Markdown生成および相対URL生成を、一つの候補パイプラインとして接続できた。

とくに、原稿順序の正本である`publication`へ生成索引自身も配置する方式には、次の利点がある。

- 索引の生成位置を別の設定で二重管理しなくてよい
- 掲載外Markdownを索引収集から除外できる
- 前付、本文、付録、索引および後付の順序を同じデータモデルで検証できる
- 将来のVivliostyle設定ヘルパーが、同じ`publication`から原稿順と目次掲載情報を取得できる

以上から、索引生成をclonoの書籍プロジェクト変換における公開前処理として実装できる見通しが立った。

## 仕様策定への示唆

今回の結果から、初期仕様では次の契約を候補にできる。

- 生成索引を`publication`の明示的な要素として配置する
- 索引指定はGeneric DirectivesのText directiveとする
- 初期仕様では索引語と読みをともに必須とする
- `publication`に掲載されたMarkdownだけを索引収集の対象とする
- 収集順は`publication`順、その中では原稿内の入力位置順とする
- 索引指定ごとに書籍全体で一意な本文マーカーIDを割り当てる
- 本文マーカーには表示語だけを出力し、読みと並べ替えキーは内部データとして扱う
- 同じ表示語と読みを一項目へ統合し、すべての出現位置を保持する
- 生成索引から各出現位置へ、URLとして安全に直列化した相対リンクを生成する
- 索引固有の診断が一件でもある場合は、本文変換と索引生成の結果を公開しない

設定の名称、空の索引の扱い、マーカーIDの生成規則、HTML構造、class名および診断形式は、実装前の仕様策定で改めて決定する。

## 成立条件と未確認事項

この調査は、候補となる索引Markdownの生成とVFM変換後のHTML構造までを検証した。紙面上のページ番号、PDF内部リンクおよび同一ページ番号の重複表示は、[Vivliostyle索引fixture](fixtures/vivliostyle-index/)を同じ候補構造へ更新したうえで再検証している。二つのfixtureは独立しており、生成索引fixtureの成果物をVivliostyle索引fixtureへ直接渡す単一の実行パイプラインではない。

次の事項は対象外とした。

- 読みの省略と自動取得
- ユーザー定義の分類
- 階層索引、ページ範囲、主要ページおよび項目間相互参照
- 同一ページにある同じ項目のページ番号統合
- Thunder Clawの実際の項目数を含む複数ページの索引
- 目次、脚注、図、表、コードリスト、索引を同じ書籍へ配置した結合結果
- 製品用テーマにおける索引の紙面レイアウト
- `publication`からVivliostyle設定を生成するヘルパー
- 生成したマーカーIDと、著者が指定したHTML IDまたはほかのclono機能が生成するIDとの衝突検査
- Windows、LinuxおよびNode.js 22系で同じ結果になること

既存の書籍プロジェクト基盤が担う安全な出力公開処理も、今回のfixtureでは再検証していない。索引をclono本体へ実装するときは、索引固有の事前検査と変換結果を既存の公開処理へ接続する必要がある。

## 依存関係の監査

2026年9月12日にfixtureで`npm audit`を実行した結果、固定した依存関係全体に3件のmoderateと3件のhighが報告された。`npm audit --omit=dev`では0件だった。

VFM、`node-html-parser`およびshadow-cljsは検証専用の開発依存関係であり、clono本体のproduction依存関係として追加したものではない。fixtureは外部から受け取った信頼できない入力の処理には使用しない。調査対象との対応と再現性を維持するため、監査結果だけを理由に固定バージョンを変更しない。

## 再現方法

[検証用fixtureのREADME](fixtures/book-project-generated-index/README.md)を参照する。fixture内で`npm ci`を実行した後、`npm run verify`で自動検証できる。

検証時には4テスト、92アサーションが実行され、失敗とエラーがないことを確認した。生成済み原稿ツリーを目視する場合は`npm run build:project`を実行する。

## 再調査する条件

- 索引の設定、著者向け記法または出力構造を大きく変更する場合
- 読みを任意にし、自動取得または推定する場合
- ユーザー定義の分類または並べ替え規則を導入する場合
- `publication`以外を原稿順序または生成索引位置の正本にする場合
- Vivliostyleへ渡す主要形式をMarkdown以外へ変更する場合
- 実原稿で複数ページの索引、同一ページ番号の重複または製品用テーマに問題が見つかった場合
- 生成索引を既存の安全な出力公開処理へ接続できない場合

## 参照資料

- [プロジェクト憲章](../project-charter.md)
- [書籍プロジェクト仕様](../specifications/book-project.md)
- [Vivliostyleとの責務整理](../vivliostyle-responsibilities.md)
- [索引の読み正規化・分類・並べ替えに関する調査](index-normalization.md)
- [Vivliostyleの索引に関する調査](vivliostyle-index.md)
- [書籍プロジェクトの生成済み原稿ツリーに関する調査](book-project-output-tree.md)
- [検証用fixture](fixtures/book-project-generated-index/)
