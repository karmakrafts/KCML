# KCML

The Kotlin Compiler Meta Loader project allows multiple Kotlin compiler plugins to interoperate  
and provides access to the LLVM internals of the compiler for advanced multiplatform plugins.

> KCML heavily tampers with the compiler based on what meta plugins are applied.  
> In order to prevent an influx of false reports on the Kotlin issue tracker,  
> **please do not report any issues occurring in a project setup that uses KCML to the Kotlin
> issue tracker!**  
> Report the issue to the KCML repository and we will delegate the issue if needed.

This project is in now way associated with Kotlin or JetBrains. Use at your own risk!

### How it works

Most of the functionality provided by KCML is implemented using `ServiceLoader` and a regular  
compiler plugin which delegates functionality to the meta-plugins.

However, the interface for extending the LLVM backend is implemented using the `jdk.attach` API  
in order to instrument compiler internals using bytecode manipulation, since there's no official way
to interact with LLVM directly.