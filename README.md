# clono

clono is a command-line preprocessor for extending
[Vivliostyle Flavored Markdown (VFM)](https://github.com/vivliostyle/vfm) with
features needed for book production.

It is intended to support features such as cross-references, page footnotes,
and indexes while preserving VFM-compatible Markdown as an inspectable
intermediate output.

```text
Markdown with clono extensions
  -> clono
  -> VFM-compatible Markdown
  -> Vivliostyle
```

clono is not a replacement for Vivliostyle and does not run the Vivliostyle
typesetting process itself.

## Status

clono is in early development. The Markdown transformation features are not
implemented yet, and the npm package has not been published.

The current CLI only provides help and version information:

```console
$ node dist/clono.js --help
Usage: clono [options]

Options:
  --help     Show help
  --version  Show version
```

No compatibility guarantees are provided before the first stable release.

## Requirements

Running a packaged version of clono requires Node.js 22 or later.

Developing clono additionally requires:

- Node.js 24.19.0
- npm
- Java 21 or later

Java and ClojureScript tooling are development dependencies. They will not be
required to run the published npm package.

## Development

Install dependencies and run the test suite:

```console
npm ci
npm test
```

Create a release build:

```console
npm run build
node dist/clono.js --version
```

See the [development guide](docs/development.md) for the complete development
workflow.

## Documentation

Project documents other than this README are primarily written in Japanese.

- [Project charter](docs/project-charter.md)
- [Development guide](docs/development.md)
- [ADR 0001: npm distribution and shadow-cljs build](docs/decisions/0001-distribution-and-build.md)

## License

clono is licensed under the [Apache License 2.0](LICENSE).
