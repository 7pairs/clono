# 書籍プロジェクト仕様

- 状態: 実装予定
- 作成日: 2026-08-25
- 最終更新日: 2026-08-27

## 目的

この文書は、複数の原稿と静的ファイルから、Vivliostyleが処理できる生成済み原稿ツリーを作るための初期仕様を定める。

書籍プロジェクト変換は、原稿順序と文書種別を一つの設定で管理し、Markdownへclonoの変換パイプラインを適用する。画像やHTMLなど、変換しないファイルも相対パスを保って出力し、Vivliostyle設定と利用者テーマは書籍プロジェクト側に残す。

変換後のMarkdown、通過HTMLおよび静的ファイルからWebPubまたはPDFを生成する処理はVivliostyleへ委譲する。

## 対象範囲

初期仕様では、次の機能を扱う。

- 一つの入力原稿ツリーを一つの生成済み原稿ツリーへ変換する
- Markdownの変換と通常ファイルのコピー
- 前付、本文、付録、後付からなる原稿順序の管理
- 各原稿を目次へ掲載するかどうかの指定
- 生成済み原稿ツリーの安全な再生成
- clono基盤CSSの生成済み原稿ツリーへの配置

次の機能は初期仕様に含めない。

- Vivliostyle設定の生成または変更
- 利用者テーマの管理またはコピー
- clono設定をVivliostyle設定へ変換するヘルパー
- 空白ページ、目次または索引文書の生成
- watchモードと差分ビルド
- シンボリックリンクの追跡またはコピー
- 複数の入力原稿ルートまたは出力形式

## 実装段階

この仕様は複数のPull Requestに分けて実装する。仕様全体の契約と現在の実装範囲を区別し、後続作業を会話やPull Requestの履歴だけに依存させないため、次の表を実装状態の正本とする。

| 状態 | 意味 |
| --- | --- |
| 未実装 | 仕様は決定しているが、着手するPull Requestをまだ作成していない |
| 実装予定 | 次に着手する実装対象として合意している |
| 実装中 | 対応する実装とテストをPull Requestで作業している |
| 実装済み | 対応する実装と自動テストが完了し、利用できる |

| 段階 | 内容 | 状態 |
| --- | --- | --- |
| 1 | `clono.config.mjs`の読み込み、設定構造とパスの検証 | 実装済み |
| 2 | 入力原稿ツリーの走査と変換計画の作成 | 実装済み |
| 3 | 複数原稿の変換と診断の収集 | 未実装 |
| 4 | 通常ファイル、clono基盤CSSおよび所有マーカーの生成 | 未実装 |
| 5 | staging、backupおよび排他ロックによる安全な出力の公開 | 未実装 |
| 6 | `transform`と`build`サブコマンドへのCLI統合 | 未実装 |

各実装Pull Requestでは、着手した段階を「実装中」、実装と自動テストが完了した段階を「実装済み」へ更新する。段階の分割、統合または順序を変更する場合も、変更理由と後続段階への影響が分かるように同じPull Requestでこの表を更新する。

## コマンド形式

書籍プロジェクトを変換するコマンド形式は、次のとおりとする。

```text
clono build
clono build <project>
```

- `<project>`には書籍プロジェクトのルートディレクトリを指定する
- `<project>`を省略した場合は、カレントディレクトリを書籍プロジェクトのルートとする
- 相対パスで指定した`<project>`は、`clono`を起動したときのカレントディレクトリを基準に解決する
- 指定したプロジェクトルート直下の`clono.config.mjs`だけを読み込み、祖先ディレクトリを探索しない
- 初期仕様では、設定ファイルのパス、入力原稿ルートまたは生成済み原稿ルートをCLIオプションで上書きしない

現在の単一ファイル変換CLIは、書籍プロジェクト用CLIを実装する際に`transform`サブコマンドへ移行する。npm公開前かつ実利用前であるため、現在の`clono <input> --output <output>`との後方互換性は保証しない。

## 設定ファイル

### 形式と読み込み

設定ファイル名は`clono.config.mjs`とし、ECMAScript Moduleのdefault exportとして設定オブジェクトを返す。

```javascript
export default {
  sourceRoot: "manuscripts",
  outputRoot: "build/manuscripts",
  publication: [
    { path: "preface.md", kind: "frontmatter", includeInToc: true },
    { path: "chapter-one.md", kind: "chapter", includeInToc: true },
    { path: "appendix-a.md", kind: "appendix", includeInToc: true },
    { path: "appendix-notes.md", kind: "appendix", includeInToc: false },
    { path: "index.md", kind: "backmatter", includeInToc: true },
    { path: "colophon.html", kind: "backmatter", includeInToc: false },
  ],
};
```

`clono.config.mjs`はJavaScriptとして実行される。書籍プロジェクトの設定は信頼できる入力として扱い、信頼できないリポジトリの設定を実行しない。clonoは設定コードをサンドボックス内で実行しない。

設定項目に既定値は設けない。`sourceRoot`、`outputRoot`および`publication`は明示必須とし、不足、型の不一致または未知の設定項目がある場合は設定エラーとする。`publication`の各要素についても、この仕様で定義していない項目はエラーとする。

### 入力原稿ルートと生成済み原稿ルート

`sourceRoot`と`outputRoot`には、プロジェクトルートからの相対ディレクトリパスを指定する。

- 空文字列、絶対パスおよびプロジェクトルートの外側へ出るパスはエラーとする
- `sourceRoot`は、読み取り可能な既存のディレクトリでなければならない
- `outputRoot`は存在しなくてもよく、clonoが必要に応じて作成する
- 入力と出力が同じパスである場合はエラーとする
- 一方が他方の祖先または子孫である場合はエラーとする
- プロジェクトルートから対象までの既存のパス要素がシンボリックリンクである場合はエラーとする

設定内のパスには`/`を区切り文字として使用する。clonoは実行OSのファイルシステム用パスへ変換して扱う。

### 書籍構造

`publication`は、書籍へ掲載する原稿を掲載順に並べた空でない配列とする。書籍構造と原稿順序の正本は`clono.config.mjs`の`publication`であり、同じ一覧を`vivliostyle.config.mjs`へ重複して記述しない。

各要素は、次の必須項目を持つ。

| 項目 | 型 | 意味 |
| --- | --- | --- |
| `path` | 文字列 | `sourceRoot`からの相対原稿パス |
| `kind` | 文字列 | 文書種別 |
| `includeInToc` | 真偽値 | Vivliostyleの目次へ掲載するか |

初期仕様で許可する`kind`は、次の四種類とする。

| 値 | 意味 |
| --- | --- |
| `frontmatter` | 前付 |
| `chapter` | 本文の章 |
| `appendix` | 付録 |
| `backmatter` | 索引を含む後付 |

`path`には、拡張子を小文字化した結果が`.md`または`.html`となる通常ファイルを指定できる。絶対パス、`sourceRoot`の外側へ出るパス、ディレクトリ、存在しないファイル、重複したパス、および途中のパス要素を含めてシンボリックリンクを経由するパスはエラーとする。

`kind`と`includeInToc`は、目次、章番号および付録番号をVivliostyleへ設定するための入力である。初期の`clono build`は値を検証して保持するが、目次や番号を自ら生成しない。

## 入力原稿ツリーの処理

clonoは`sourceRoot`以下を再帰的に走査し、次の規則で`outputRoot`へ出力する。

| 入力 | 動作 |
| --- | --- |
| 拡張子を小文字化した結果が`.md`となる通常ファイル | clonoのMarkdown変換パイプラインを適用する |
| その他の通常ファイル | 相対パスと内容を保ってコピーする |
| ディレクトリ | 同じ相対位置へ作成する |
| シンボリックリンク | エラーにする |
| その他のファイル種別 | エラーにする |

HTML、画像、CSSなど、Markdown以外の通常ファイルはclonoの変換対象にしない。ファイルの許可ビット、タイムスタンプ、ACLおよび拡張属性の保持は初期仕様で保証しない。

出力操作を開始する前に、入力原稿ツリーを走査して次の変換計画を作成する。

- ディレクトリ、Markdownおよびその他の通常ファイルを、それぞれディレクトリ作成、Markdown変換およびファイルコピーの操作として記録する
- 各操作は入力原稿ルートからの相対パスと入力元の絶対パスを持ち、正式な出力先への書き込みは行わない
- 各ディレクトリの項目を名前順に走査し、ディレクトリ作成をその子孫の操作より前に記録することで、ファイルシステムの列挙順に依存しない決定的な計画を作成する
- すべての`publication`要素に対応するMarkdown変換またはファイルコピーの操作が計画に含まれることを確認し、含まれない場合は診断を返して部分的な変換計画を公開しない
- 予約パス、シンボリックリンク、対応していないファイル種別または読み取り失敗を検出した場合は診断を返し、部分的な変換計画を公開しない

変換計画との整合性を確認した後も、後続の生成処理では、すべての`publication`要素に対応するファイルが生成済み原稿ツリーに存在することを公開前に改めて確認する。

## clono基盤CSS

`clono build`は、著者向け記法の使用状況にかかわらず、実行中のclonoに同梱された`styles/clono.css`を次の固定パスへ毎回コピーする。

```text
<outputRoot>/_clono/styles/clono.css
```

コピー後の基盤CSSは、実行中のclonoに同梱されたファイルと同じ内容を持つ。`_clono/`はclonoが生成するファイルの予約領域とし、`sourceRoot`直下に同名のファイルまたはディレクトリが存在する場合は、利用者の内容を上書きせずエラーとする。

clonoが生成するHTML構造には、用途を示す固定の`clono-`接頭辞付きclassを付け、インラインスタイルを使用しない。基盤CSSはこれらのclassを基本として機能上必要な規則だけを定義し、次のものを使用しない。

- IDセレクター
- `!important`

利用者テーマによるあらゆる上書きを無条件に保証するものではない。同じオリジンとカスケードレイヤーにおいて、基盤CSSを先、同じ詳細度以上の利用者テーマを後から読み込んだ場合に、通常のCSSカスケードによって上書きできる構造とする。

利用者テーマは`clono.config.mjs`では管理せず、書籍固有の`vivliostyle.config.mjs`で管理する。初期仕様では設定ヘルパーを提供しないため、`vivliostyle.config.mjs`は`clono.config.mjs`を直接importし、各設定値をVivliostyleの契約へ明示的に変換する。

- `path`は、`outputRoot`以下にある生成済み原稿の`entry`へ変換する
- `kind`は、本文・付録などの原稿別テーマの選択と、目次項目の文書種別を示すメタデータへ変換する
- `includeInToc`が`false`の原稿は、`kind`にかかわらず`transformDocumentList`で目次から除外する
- clono基盤CSSは、すべての原稿で利用者テーマより前に指定する

次の例は、この変換の要点を示す。`element`と`text`は、Vivliostyle CLIの目次変換関数が扱うhastノードを生成するためのローカル関数であり、clonoが提供するヘルパーではない。

```javascript
import clonoConfig from "./clono.config.mjs";

const { outputRoot, publication } = clonoConfig;

function element(tagName, properties = {}, children = []) {
  return { type: "element", tagName, properties, children };
}

function text(value) {
  return { type: "text", value };
}

function outputHtmlPath(sourcePath) {
  return sourcePath.replace(/\.md$/u, ".html");
}

const documentByOutput = new Map(
  publication.map((document) => [outputHtmlPath(document.path), document]),
);

function addDocumentKind(node, kind) {
  if (node.type !== "element" || node.tagName !== "li") return node;
  return {
    ...node,
    properties: {
      ...node.properties,
      "data-document-kind": kind,
    },
  };
}

function transformDocumentList(nodeList) {
  return (propsList) =>
    element(
      "ol",
      {},
      nodeList.flatMap((document, index) => {
        const metadata = documentByOutput.get(document.href);
        if (!metadata?.includeInToc) return [];

        const children = [propsList[index].children].flat(2);
        if (document.sections?.length === 1 && document.sections[0].level === 1) {
          return children.flatMap((child) =>
            child.type === "element" && child.tagName === "ol"
              ? child.children.map((item) => addDocumentKind(item, metadata.kind))
              : child,
          );
        }

        return [
          element("li", { "data-document-kind": metadata.kind }, [
            element("a", { href: document.href }, [text(document.title)]),
            ...children,
          ]),
        ];
      }),
    );
}

function themeFor(kind) {
  const kindTheme = {
    chapter: "themes/chapter.css",
    appendix: "themes/appendix.css",
  }[kind];
  return [
    `${outputRoot}/_clono/styles/clono.css`,
    "themes/book.css",
    ...(kindTheme ? [kindTheme] : []),
  ];
}

export default {
  entry: publication.map(({ path, kind }) => ({
    path: `${outputRoot}/${path}`,
    theme: themeFor(kind),
  })),
  toc: {
    title: "目次",
    htmlPath: "toc.html",
    sectionDepth: 2,
    transformDocumentList,
  },
};
```

`includeInToc`による選別、`kind`に応じた本文・付録の番号、および目次項目への文書種別の付与は、[Vivliostyleの目次に関する調査](../research/vivliostyle-table-of-contents.md)とそのfixtureでWebPubおよびPDFまで検証している。書籍固有の目次テンプレート、CSSおよび`transformSectionList`は、利用者が`vivliostyle.config.mjs`で管理する。

利用者テーマを指定しない場合も、生成した構造を機能させるため基盤CSSはVivliostyle設定へ指定する。clonoは`vivliostyle.config.mjs`を生成、変更または検証しない。

## 出力先の所有と保護

### 所有マーカー

生成済み原稿ツリーのルートには、clonoが管理する出力であることを識別する`.clono-output.json`を配置する。

```json
{
  "format": 1,
  "producer": "clono",
  "sourceRoot": "manuscripts",
  "outputRoot": "build/manuscripts"
}
```

`sourceRoot`と`outputRoot`には、設定値を正規化したプロジェクト相対パスを記録する。clonoは、`format`、`producer`、`sourceRoot`および`outputRoot`が現在の書籍プロジェクトと一致する場合だけ、既存出力を所有済みとして扱う。

所有マーカーは誤った出力先の指定による破壊を防ぐための識別子であり、悪意ある利用者または外部プロセスに対するセキュリティ境界ではない。`sourceRoot`直下に`.clono-output.json`が存在する場合は、生成時の衝突を避けるためエラーとする。

### 出力先の扱い

| `outputRoot`の状態 | 動作 |
| --- | --- |
| 存在しない | ディレクトリを作成して出力する |
| 空のディレクトリ | clonoの管理対象として初期化する |
| 一致する所有マーカーを持つディレクトリ | 新しい生成物で置き換える |
| 所有マーカーのない空でないディレクトリ | 変更せずエラーにする |
| 不正または一致しない所有マーカーを持つディレクトリ | 変更せずエラーにする |
| 通常ファイルまたはその他の非ディレクトリ | 変更せずエラーにする |

clonoは、所有マーカーのない空でないディレクトリを削除、移動または上書きしない。

### stagingと排他制御

Markdownの変換、通常ファイルのコピー、基盤CSSと所有マーカーの生成、および`publication`の検証は、正式な`outputRoot`とは別のstagingディレクトリで完了させる。いずれかが失敗した場合はstagingを可能な範囲で削除し、既存の`outputRoot`を変更しない。

公開前には、出力先ごとの排他ロックを取得する。ロック取得後に`outputRoot`の状態と所有マーカーを再確認し、公開処理が成功または失敗するまでロックを保持する。同じ規約に従う別のclonoプロセスがロックを保持している場合は、既存出力を変更せずエラーにする。

既存の所有済み出力を置き換える場合は、stagingを正式な出力として公開できなければ既存出力を復元する。プロセスの強制終了、OSまたはファイルシステムの障害、およびロックを無視する外部プロセスに対する完全な原子性は保証しない。残留したstaging、backupまたはロックの自動回復は初期仕様に含めない。

## 失敗時の契約

設定、パス、原稿、変換、コピー、予約領域、所有マーカーまたは排他ロックに問題がある場合は、問題の対象と理由を標準エラーへ出力し、終了コード`1`で終了する。通常の利用者向けエラーではJavaScriptのスタックトレースを表示しない。

一件以上のMarkdown診断がある場合は、その診断をファイル名、行、列とともに表示し、部分的な生成済み原稿ツリーを公開しない。公開前の失敗では、既存の所有済み出力を変更しない。

すべての処理が成功した場合だけ生成済み原稿ツリーを公開し、終了コード`0`で終了する。標準出力と診断の収集順序を含む詳細なCLI表示契約は、`build`サブコマンドの実装前に決定する。

## Vivliostyleとの責務分担

### clonoが担う責務

- 書籍プロジェクト設定の読み込みと検証
- 原稿順序、文書種別および目次掲載指定の管理
- Markdownの解析、検証、変換および直列化
- 通常ファイルと基盤CSSを含む生成済み原稿ツリーの作成
- 出力先の所有確認、排他制御および安全な置き換え

### Vivliostyleへ委譲する責務

- MarkdownとHTMLの処理
- 原稿の指定順での組版
- 文書種別に応じた章番号と付録番号の生成
- 目次項目、紙面上のページ番号およびリンクの生成
- clono基盤CSSと利用者テーマの指定順での適用
- WebPubおよびPDFの生成

### 利用者が担う責務

- 変換前の原稿と静的ファイルの管理
- `clono.config.mjs`における書籍構造の管理
- `vivliostyle.config.mjs`におけるVivliostyle設定と利用者テーマの管理
- clono基盤CSSを利用者テーマより前に指定すること
- clonoが生成しない表紙、奥付およびその他の書籍資材の管理

## 更新方針

- 書籍プロジェクトの設定形式、生成規則、予約領域、出力保護またはCLIの公開契約を変更する場合は、実装と同じPull Requestでこの文書を更新する
- 実装へ着手または実装を完了した場合は、同じPull Requestで実装段階表の状態を更新する
- この仕様に対応する実装と自動テストが揃った場合は、状態を「実装済み」へ更新する
- 書籍プロジェクト変換が利用可能になった場合は、READMEの開発状況、使用方法および制限事項を実装と同じPull Requestで更新する

## 参照資料

- [プロジェクト憲章](../project-charter.md)
- [単一ファイル変換CLI仕様](single-file-cli.md)
- [clono著者向け記法](authoring-syntax.md)
- [Generic DirectivesとmdastによるMarkdown変換パイプラインのADR](../decisions/0003-adopt-generic-directives-mdast-transformation-pipeline.md)
- [書籍プロジェクトの生成済み原稿ツリーに関する調査](../research/book-project-output-tree.md)
- [Vivliostyleにおけるclono基盤CSSと利用者テーマの統合に関する調査](../research/vivliostyle-clono-stylesheet.md)
