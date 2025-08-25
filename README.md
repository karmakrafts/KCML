yes# KCML

The Kotlin Compiler Meta Loader project allows multiple Kotlin compiler plugins to interoperate  
and provides access to the LLVM internals of the compiler for advanced multiplatform plugins.

### How it works

Most of the functionality provided by KCML is implemented using `ServiceLoader` and a regular  
compiler plugin which delegates functionality to the meta-plugins.

However, the interface for extending the LLVM backend is implemented using the `jdk.attach` API  
in order to instrument compiler internals using bytecode manipulation, since there's no official way
to interact with LLVM directly.