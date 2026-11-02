# KCML Example Plugin

This project contains a KCML example plugin which provides some basic scaffolding as a template
to create your own plugins, and as a technical reference.

## JavaScript

The `js` package demonstrates late JavaScript backend initialization, IR transformations that
continue normal generation, and replacements using JavaScript AST expressions and statements.
The intrinsic example recognizes `@CustomIntrinsic` calls and evaluates their arguments once,
in order, before returning a custom result. Nested argument generation still passes through
code-generation extensions; generating the intercepted call itself would recurse.

The corresponding declarations and tests are in the example project's `jsMain` and `jsTest`
source sets. Run them with `./gradlew :kcml-example-project:jsNodeTest` from the repository root.
KCML preserves Kotlin's incremental JS AST caches between builds and invalidates them when the
KCML plugin or runtime classpath changes, so cached ASTs reflect late code-generation extensions.