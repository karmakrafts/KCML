/*
 * Copyright 2025 Karma Krafts & associates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.karmakrafts.kcml.ir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.impl.EmptyPackageFragmentDescriptor
import org.jetbrains.kotlin.fir.backend.FirMetadataSource
import org.jetbrains.kotlin.fir.builder.buildPackageDirective
import org.jetbrains.kotlin.fir.declarations.builder.buildFile
import org.jetbrains.kotlin.ir.builders.declarations.IrDeclarationBuilder
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.util.NaiveSourceBasedFileEntryImpl
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.addFile
import org.jetbrains.kotlin.ir.util.fileEntry
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div

class IrTopLevelClassBuilder(
    private val module: IrModuleFragment
) : IrDeclarationBuilder() {
    lateinit var packageName: FqName

    var kind: ClassKind = ClassKind.CLASS
    var modality: Modality = Modality.FINAL

    var isCompanion: Boolean = false
    var isInner: Boolean = false
    var isData: Boolean = false
    var isExternal: Boolean = false
    var isValue: Boolean = false
    var isExpect: Boolean = false
    var isFun: Boolean = false
    var hasEnumEntries: Boolean = false

    fun updateFrom(from: IrClass) {
        super.updateFrom(from)

        kind = from.kind
        modality = from.modality
        isCompanion = from.isCompanion
        isInner = from.isInner
        isData = from.isData
        isExternal = from.isExternal
        isValue = from.isValue
        isExpect = from.isExpect
        isFun = from.isFun
        hasEnumEntries = from.hasEnumEntries
    }
}

@PublishedApi
internal fun Name.asCleanString(): String = asString().drop(1).dropLast(1).replace('-', '_').replace('.', '_')

inline fun IrFactory.buildTopLevelClass( // @formatter:off
    module: IrModuleFragment,
    block: IrTopLevelClassBuilder.() -> Unit
): IrClass { // @formatter:on
    val builder = IrTopLevelClassBuilder(module).apply(block)
    val declaration = createClass(
        startOffset = builder.startOffset,
        endOffset = builder.endOffset,
        origin = builder.origin,
        name = builder.name,
        visibility = builder.visibility,
        symbol = IrClassSymbolImpl(),
        kind = builder.kind,
        modality = builder.modality,
        isExternal = builder.isExternal,
        isCompanion = builder.isCompanion,
        isInner = builder.isInner,
        isData = builder.isData,
        isValue = builder.isValue,
        isExpect = builder.isExpect,
        isFun = builder.isFun,
        hasEnumEntries = builder.hasEnumEntries,
    )
    val moduleName = module.name.asCleanString()
    val declarationName = declaration.name.asCleanString()
    val fileName = "__generated_${moduleName}_${declarationName}__.kt"
    val firFile = buildFile {
        moduleData = (declaration.metadata as FirMetadataSource.Class).fir.moduleData
        name = fileName
        packageDirective = buildPackageDirective {
            packageFqName = builder.packageName
        }
    }
    val filePath = Path(declaration.fileEntry.name).parent / fileName
    val irFile = IrFileImpl(
        fileEntry = NaiveSourceBasedFileEntryImpl(filePath.absolutePathString()),
        packageFragmentDescriptor = EmptyPackageFragmentDescriptor(
            module = module.descriptor, fqName = builder.packageName
        ),
        module = module
    ).apply {
        metadata = FirMetadataSource.File(firFile)
    }
    irFile.addChild(declaration)
    module.addFile(irFile)
    return declaration
}