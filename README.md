# KCML

The Kotlin Compiler Meta Loader project allows multiple Kotlin compiler plugins to interoperate  
and provides a stable API for FIR, IR and backend processing.  
It also exposes some additional internals of the compiler to allow extending Kotlin beyond the IR.

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

### Recommendations

It is recommended to use KCML in conjunction with the in-process compiler execution mode.
This can be enabled by adding the following to your `gradle.properties`:

```properties
kotlin.compiler.execution.strategy=in-process
```