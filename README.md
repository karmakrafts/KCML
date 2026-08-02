# ↺ KCML

[![](https://git.karmakrafts.dev/kk/kcml/badges/master/pipeline.svg)](https://git.karmakrafts.dev/kk/kcml/-/pipelines)
[![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo.maven.apache.org%2Fmaven2%2Fdev%2Fkarmakrafts%2Fkcml%2Fkcml-plugin-api%2Fmaven-metadata.xml
)](https://git.karmakrafts.dev/kk/kcml/-/packages)
[![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fkarmakrafts%2Fkcml%2Fkcml-plugin-api%2Fmaven-metadata.xml
)](https://git.karmakrafts.dev/kk/kcml/-/packages)
[![](https://img.shields.io/badge/2.4.10-blue?logo=kotlin&label=kotlin)](https://kotlinlang.org/)
[![](https://img.shields.io/badge/documentation-black?logo=kotlin)](https://docs.karmakrafts.dev/kcml-plugin-api)

![](https://img.shields.io/badge/-JVM-blue?logo=kotlin&labelColor=black)
![](https://img.shields.io/badge/-Android-green?logo=kotlin&labelColor=black)
![](https://img.shields.io/badge/-Native-lightgray?logo=kotlin&labelColor=black)
![](https://img.shields.io/badge/-JS-gold?logo=kotlin&labelColor=black)
![](https://img.shields.io/badge/-WASM/JS-orange?logo=kotlin&labelColor=black)
![](https://img.shields.io/badge/-WASM/WASI-purple?logo=kotlin&labelColor=black)

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

![KCML Architecture Diagram](docs/architecture.svg)

### Recommendations

It is recommended to use KCML in conjunction with the in-process compiler execution mode.
This can be enabled by adding the following to your `gradle.properties`:

```properties
kotlin.compiler.execution.strategy=in-process
```