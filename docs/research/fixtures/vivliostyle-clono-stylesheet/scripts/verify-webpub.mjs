import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFile, rm, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const fixtureDirectory = fileURLToPath(new URL('..', import.meta.url));
const cliPath = fileURLToPath(
  new URL('../node_modules/@vivliostyle/cli/dist/cli.js', import.meta.url),
);
const workspaceDirectory = fileURLToPath(new URL('../.vivliostyle/', import.meta.url));
const prepareGeneratedTreePath = fileURLToPath(
  new URL('./prepare-generated-tree.mjs', import.meta.url),
);
const preparePackageThemePath = fileURLToPath(
  new URL('./prepare-package-theme.mjs', import.meta.url),
);
const sourceStylesheetPath = fileURLToPath(
  new URL('../../../../../styles/clono.css', import.meta.url),
);
const generatedStylesheetPath = fileURLToPath(
  new URL('../generated/manuscripts/_clono/styles/clono.css', import.meta.url),
);
const outputRoot = fileURLToPath(new URL('../output/', import.meta.url));

const variants = [
  {
    configPath: fileURLToPath(new URL('../vivliostyle.config.mjs', import.meta.url)),
    expectClonoStylesheet: true,
    name: 'package-theme',
    outputDirectory: fileURLToPath(new URL('../output/package-theme-webpub/', import.meta.url)),
    preparationPath: preparePackageThemePath,
  },
  {
    configPath: fileURLToPath(
      new URL('../vivliostyle.hidden-generated-theme.config.mjs', import.meta.url),
    ),
    expectClonoStylesheet: false,
    name: 'hidden-generated-theme',
    outputDirectory: fileURLToPath(
      new URL('../output/hidden-generated-theme-webpub/', import.meta.url),
    ),
    preparationPath: prepareGeneratedTreePath,
  },
  {
    configPath: fileURLToPath(
      new URL('../vivliostyle.generated-theme.config.mjs', import.meta.url),
    ),
    expectClonoStylesheet: true,
    name: 'generated-theme',
    outputDirectory: fileURLToPath(new URL('../output/generated-theme-webpub/', import.meta.url)),
    preparationPath: prepareGeneratedTreePath,
  },
];

function runPreparation(preparationPath) {
  const result = spawnSync(process.execPath, [preparationPath], {
    cwd: fixtureDirectory,
    stdio: 'inherit',
    timeout: 30_000,
  });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, 'The fixture preparation must succeed');
}

async function pathExists(targetPath) {
  try {
    await stat(targetPath);
    return true;
  } catch (error) {
    if (error?.code === 'ENOENT') return false;
    throw error;
  }
}

for (const variant of variants) {
  await rm(outputRoot, { recursive: true, force: true });
  await rm(workspaceDirectory, { recursive: true, force: true });
  runPreparation(variant.preparationPath);

  const buildResult = spawnSync(
    process.execPath,
    [
      cliPath,
      'build',
      '--config',
      variant.configPath,
      '--output',
      variant.outputDirectory,
      '--format',
      'webpub',
    ],
    {
      cwd: fixtureDirectory,
      stdio: 'inherit',
      timeout: 120_000,
    },
  );

  if (buildResult.error) throw buildResult.error;
  assert.equal(buildResult.status, 0, `${variant.name} build must finish successfully`);
  assert.ok(
    (await stat(variant.outputDirectory)).isDirectory(),
    `${variant.name} build must produce a WebPub`,
  );

  const manifest = JSON.parse(
    await readFile(path.join(variant.outputDirectory, 'publication.json'), 'utf8'),
  );
  assert.equal(
    manifest.readingOrder.length,
    1,
    `${variant.name} WebPub must have one reading-order entry`,
  );
  const htmlPath = path.join(variant.outputDirectory, manifest.readingOrder[0].url);
  const html = await readFile(htmlPath, 'utf8');
  assert.match(
    html,
    /class="clono-align-right"[^>]*>ユーザーテーマで左揃えへ上書き<\/p>/u,
    `${variant.name} manuscript must retain the clono alignment class`,
  );
  assert.match(
    html,
    /class="clono-page-break"/u,
    `${variant.name} manuscript must retain the clono page-break class`,
  );

  const stylesheetHrefs = [
    ...html.matchAll(/<link\b[^>]*\brel="stylesheet"[^>]*\bhref="([^"]+)"[^>]*>/gu),
  ].map((match) => match[1]);
  const resolvedStylesheets = await Promise.all(
    stylesheetHrefs.map(async (href) => {
      const stylesheetPath = fileURLToPath(new URL(href, `file://${htmlPath}`));
      const exists = await pathExists(stylesheetPath);
      return {
        content: exists ? await readFile(stylesheetPath, 'utf8') : '',
        exists,
        href,
      };
    }),
  );
  const clonoIndex = resolvedStylesheets.findIndex(({ content }) =>
    content.includes('.clono-page-break'),
  );
  const userIndex = resolvedStylesheets.findIndex(({ content }) =>
    content.includes('USER THEME APPLIED:'),
  );

  assert.notEqual(userIndex, -1, `${variant.name} WebPub must include the user stylesheet`);
  if (variant.expectClonoStylesheet) {
    assert.notEqual(clonoIndex, -1, `${variant.name} WebPub must include the clono stylesheet`);
    assert.ok(
      clonoIndex < userIndex,
      `${variant.name} user stylesheet must follow the clono stylesheet`,
    );
  } else {
    assert.equal(clonoIndex, -1, `${variant.name} WebPub must expose the missing clono stylesheet`);
    const hiddenStylesheet = resolvedStylesheets.find(({ href }) =>
      href.endsWith('.clono/styles/clono.css'),
    );
    assert.ok(hiddenStylesheet, 'The hidden generated theme must retain its stylesheet link');
    assert.equal(
      hiddenStylesheet.exists,
      false,
      'Vivliostyle must not copy the hidden generated stylesheet into the WebPub',
    );
  }

  const installedThemePath = fileURLToPath(
    new URL('../.vivliostyle/themes/node_modules/clono/', import.meta.url),
  );
  assert.equal(
    await pathExists(installedThemePath),
    variant.name === 'package-theme',
    `${variant.name} must ${variant.name === 'generated-theme' ? 'not ' : ''}install clono as a theme package`,
  );

  if (variant.name === 'generated-theme') {
    assert.equal(
      await readFile(generatedStylesheetPath, 'utf8'),
      await readFile(sourceStylesheetPath, 'utf8'),
      'The generated manuscript tree must contain the exact clono stylesheet',
    );
    assert.match(
      resolvedStylesheets[clonoIndex].href,
      /_clono\/styles\/clono\.css$/u,
      'The generated-theme WebPub must retain the reserved stylesheet path',
    );
  }
}

console.log('Verified package and generated stylesheet integration in WebPub outputs');
