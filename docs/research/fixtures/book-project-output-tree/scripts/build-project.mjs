import { randomUUID } from 'node:crypto';
import {
  copyFile,
  mkdir,
  readFile,
  readdir,
  rename,
  rmdir,
  rm,
  stat,
  writeFile,
} from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { outputRoot, publication, sourceRoot } from '../project/book.mjs';

const markerName = '.clono-output.json';
const projectDirectory = fileURLToPath(new URL('../project/', import.meta.url));
const sourceDirectory = path.resolve(projectDirectory, sourceRoot);
const outputDirectory = path.resolve(projectDirectory, outputRoot);
const outputParent = path.dirname(outputDirectory);
const outputBaseName = path.basename(outputDirectory);
const lockDirectory = path.join(outputParent, `.${outputBaseName}.clono-lock`);
const marker = {
  format: 1,
  producer: '@clono/research-book-project-output-tree',
  sourceRoot,
  outputRoot,
};

function isWithin(parent, candidate) {
  const relative = path.relative(parent, candidate);
  return relative !== '' && !relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative);
}

function validateRoots() {
  if (!isWithin(projectDirectory, sourceDirectory)) {
    throw new Error('The source root must be inside the book project.');
  }
  if (!isWithin(projectDirectory, outputDirectory)) {
    throw new Error('The output root must be inside the book project.');
  }
  if (
    sourceDirectory === outputDirectory ||
    isWithin(sourceDirectory, outputDirectory) ||
    isWithin(outputDirectory, sourceDirectory)
  ) {
    throw new Error('The source and output roots must not overlap.');
  }
}

async function pathExists(targetPath) {
  try {
    await stat(targetPath);
    return true;
  } catch (error) {
    if (error.code === 'ENOENT') return false;
    throw error;
  }
}

async function directoryIsEmpty(directory) {
  return (await readdir(directory)).length === 0;
}

async function verifyOwnedOutput() {
  if (!(await pathExists(outputDirectory))) return 'missing';

  const outputStat = await stat(outputDirectory);
  if (!outputStat.isDirectory()) {
    throw new Error(`The output path is not a directory: ${outputRoot}`);
  }
  if (await directoryIsEmpty(outputDirectory)) return 'empty';

  let existingMarker;
  try {
    existingMarker = JSON.parse(
      await readFile(path.join(outputDirectory, markerName), 'utf8'),
    );
  } catch (error) {
    if (error.code === 'ENOENT' || error instanceof SyntaxError) {
      throw new Error(
        `The non-empty output directory does not have a valid ownership marker: ${outputRoot}`,
      );
    }
    throw error;
  }

  if (JSON.stringify(existingMarker) !== JSON.stringify(marker)) {
    throw new Error(`The output ownership marker does not match this project: ${outputRoot}`);
  }
  return 'owned';
}

async function withOutputLock(action) {
  try {
    await mkdir(lockDirectory);
  } catch (error) {
    if (error.code === 'EEXIST') {
      throw new Error(`The output directory is locked by another build: ${outputRoot}`);
    }
    throw error;
  }

  try {
    return await action();
  } finally {
    await rmdir(lockDirectory);
  }
}

function transformMarkdown(source) {
  const transformed = source.replaceAll('FIXTURE_SOURCE_TOKEN', 'FIXTURE_TRANSFORMED_TOKEN');
  if (transformed === source) {
    throw new Error('Fixture Markdown must contain its source transformation token.');
  }
  return transformed;
}

async function mirrorDirectory(source, destination) {
  await mkdir(destination, { recursive: true });

  for (const entry of await readdir(source, { withFileTypes: true })) {
    const sourcePath = path.join(source, entry.name);
    const destinationPath = path.join(destination, entry.name);

    if (entry.isSymbolicLink()) {
      throw new Error(`Symbolic links are not supported: ${sourcePath}`);
    }
    if (entry.isDirectory()) {
      await mirrorDirectory(sourcePath, destinationPath);
    } else if (entry.isFile() && path.extname(entry.name).toLowerCase() === '.md') {
      const sourceMarkdown = await readFile(sourcePath, 'utf8');
      await writeFile(destinationPath, transformMarkdown(sourceMarkdown), 'utf8');
    } else if (entry.isFile()) {
      await copyFile(sourcePath, destinationPath);
    } else {
      throw new Error(`Unsupported filesystem entry: ${sourcePath}`);
    }
  }
}

async function verifyPublication(stagingDirectory) {
  for (const { path: entryPath } of publication) {
    const absoluteEntryPath = path.resolve(stagingDirectory, entryPath);
    if (!isWithin(stagingDirectory, absoluteEntryPath)) {
      throw new Error(`A publication entry escapes the output root: ${entryPath}`);
    }
    const entryStat = await stat(absoluteEntryPath);
    if (!entryStat.isFile()) {
      throw new Error(`A publication entry is not a file: ${entryPath}`);
    }
  }
}

async function publish(stagingDirectory, outputState) {
  if (outputState === 'missing') {
    await rename(stagingDirectory, outputDirectory);
    return;
  }

  if (outputState === 'empty') {
    await rmdir(outputDirectory);
    await rename(stagingDirectory, outputDirectory);
    return;
  }

  if (outputState !== 'owned') {
    throw new Error(`Unknown output state: ${outputState}`);
  }

  const backupDirectory = path.join(
    outputParent,
    `.${outputBaseName}.clono-backup-${randomUUID()}`,
  );
  await rename(outputDirectory, backupDirectory);
  try {
    await rename(stagingDirectory, outputDirectory);
  } catch (error) {
    await rename(backupDirectory, outputDirectory);
    throw error;
  }
  await rm(backupDirectory, { recursive: true });
}

validateRoots();
await stat(sourceDirectory);
await mkdir(outputParent, { recursive: true });

const stagingDirectory = path.join(
  outputParent,
  `.${outputBaseName}.clono-staging-${randomUUID()}`,
);

try {
  await mirrorDirectory(sourceDirectory, stagingDirectory);
  await verifyPublication(stagingDirectory);
  await writeFile(
    path.join(stagingDirectory, markerName),
    `${JSON.stringify(marker, null, 2)}\n`,
    'utf8',
  );
  await withOutputLock(async () => {
    const outputState = await verifyOwnedOutput();
    await publish(stagingDirectory, outputState);
  });
} catch (error) {
  await rm(stagingDirectory, { recursive: true, force: true });
  throw error;
}
