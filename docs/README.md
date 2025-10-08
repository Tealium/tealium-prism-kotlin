# Docs

The Docs module is purely for generating documentation using Dokka.

## Overview

There are two plugins specified in `buildSrc` that serve two different purposes: 

### `DokkaRootPlugin` 

This plugin should be applied to this project only. It's used to declare which projects are Dokka dependencies
and therefore which projects should be included when generating the documentation.

### `DokkaLibraryPlugin`

This plugin should be applied to any project whose non-internal api should be documented.

The `DokkaLibraryPlugin` is automatically applied to any project that has `TealiumLibraryPlugin` applied. 
And all classes will be included in the generated documentation unless contained within a package with 
`internal` in the name.

## Usage

### dokkaGenerate

The project is set up with Dokka 2.0. To generate the documentation you should run the `dokkaGenerate`
task to whichever project has the `DokkaRootPlugin` applied. At the time of writing, that wil be this 
`docs` module - e.g.

```shell
./gradlew :docs:dokkaGenerate
```

The documentation is formatted as HTML for now, and will be output to `<project>/build/dokka/html` by default

### Module.md & Packages.md

Each project that applies the `DokkaLibraryPlugin` should also include a `Module.md` and `Packages.md` 
else the `dokkaGenerate` task will fail.

`Module.md` - allows you to define some Markdown as an introduction to the module, and will appear 
in both the landing page for the specific module, and also partially in the `All modules` section in 
the main project landing page.

`Package.md` - allows you to define introductions/descriptions of specific packages. Again they will
appear in the lists of packages but also rendered on the package's landing page.

These files have the convention that each top level markdown heading should start with either `Module`
or `Package` - see [Module docs](https://kotlinlang.org/docs/dokka-module-and-package-docs.html)


## Further reading

See the official [Dokka](https://kotlinlang.org/docs/dokka-introduction.html) documentation for what
is available out of the box