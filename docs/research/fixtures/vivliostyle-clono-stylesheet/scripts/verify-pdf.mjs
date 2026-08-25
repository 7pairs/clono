import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, rm, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

import mupdf from 'mupdf';

const fixtureDirectory = fileURLToPath(new URL('..', import.meta.url));
const cliPath = fileURLToPath(
  new URL('../node_modules/@vivliostyle/cli/dist/cli.js', import.meta.url),
);
const outputDirectory = fileURLToPath(new URL('../output/', import.meta.url));
const workspaceDirectory = fileURLToPath(new URL('../.vivliostyle/', import.meta.url));
const prepareGeneratedTreePath = fileURLToPath(
  new URL('./prepare-generated-tree.mjs', import.meta.url),
);
const preparePackageThemePath = fileURLToPath(
  new URL('./prepare-package-theme.mjs', import.meta.url),
);

const variants = [
  {
    configPath: fileURLToPath(new URL('../vivliostyle.config.mjs', import.meta.url)),
    name: 'package-theme',
    outputPath: fileURLToPath(new URL('../output/package-theme.pdf', import.meta.url)),
    preparationPath: preparePackageThemePath,
  },
  {
    configPath: fileURLToPath(
      new URL('../vivliostyle.generated-theme.config.mjs', import.meta.url),
    ),
    name: 'generated-theme',
    outputPath: fileURLToPath(new URL('../output/generated-theme.pdf', import.meta.url)),
    preparationPath: prepareGeneratedTreePath,
  },
];

function blockBounds({ x, y, w, h }) {
  return [x, y, x + w, y + h];
}

function findUniqueLine(pages, expectedText) {
  const matches = pages.flatMap(({ pageNumber, textLines }) =>
    textLines
      .filter(({ text }) => text.trim() === expectedText)
      .map((line) => ({ ...line, pageNumber })),
  );
  assert.equal(matches.length, 1, `PDF must contain one line for ${expectedText}`);
  return matches[0];
}

function runPreparation(preparationPath) {
  const result = spawnSync(process.execPath, [preparationPath], {
    cwd: fixtureDirectory,
    stdio: 'inherit',
    timeout: 30_000,
  });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, 'The fixture preparation must succeed');
}

function verifyPdf(outputPath, variantName) {
  const document = mupdf.Document.openDocument(outputPath);
  const pageCount = document.countPages();
  assert.equal(pageCount, 2, `${variantName} fixture must produce exactly two pages`);

  const pages = Array.from({ length: pageCount }, (_, pageNumber) => {
    const page = document.loadPage(pageNumber);
    const structuredTextJson = JSON.parse(page.toStructuredText().asJSON());
    return {
      pageNumber,
      textLines: structuredTextJson.blocks
        .filter(({ type }) => type === 'text')
        .flatMap(({ lines }) =>
          lines.map((line) => ({
            bounds: blockBounds(line.bbox),
            text: line.text,
          })),
        ),
    };
  });

  const alignmentReference = findUniqueLine(pages, '左端の基準');
  const overriddenAlignment = findUniqueLine(pages, 'ユーザーテーマで左揃えへ上書き');
  assert.equal(alignmentReference.pageNumber, 0);
  assert.equal(overriddenAlignment.pageNumber, 0);
  assert.ok(
    Math.abs(alignmentReference.bounds[0] - overriddenAlignment.bounds[0]) <= 2,
    `${variantName} user stylesheet must override the clono right-alignment rule`,
  );

  const userThemeMarker = findUniqueLine(
    pages,
    'USER THEME APPLIED: ユーザーテーマの適用対象',
  );
  assert.equal(userThemeMarker.pageNumber, 0, `${variantName} user stylesheet must generate its marker`);

  const pageBreakHeading = findUniqueLine(pages, '基盤CSSによる改ページ後');
  assert.equal(
    pageBreakHeading.pageNumber,
    1,
    `${variantName} clono stylesheet must move content after the page-break marker`,
  );
}

await rm(outputDirectory, { recursive: true, force: true });
await mkdir(outputDirectory, { recursive: true });

for (const variant of variants) {
  await rm(variant.outputPath, { force: true });
  await rm(workspaceDirectory, { recursive: true, force: true });
  runPreparation(variant.preparationPath);

  const buildResult = spawnSync(
    process.execPath,
    [cliPath, 'build', '--config', variant.configPath, '--output', variant.outputPath],
    {
      cwd: fixtureDirectory,
      stdio: 'inherit',
      timeout: 120_000,
    },
  );

  if (buildResult.error) throw buildResult.error;
  assert.equal(buildResult.status, 0, `${variant.name} build must finish successfully`);

  const outputStat = await stat(variant.outputPath);
  assert.ok(outputStat.size > 0, `${variant.name} build must produce a non-empty PDF`);
  verifyPdf(variant.outputPath, variant.name);
}

console.log('Verified package and generated stylesheet behavior in PDF outputs');
