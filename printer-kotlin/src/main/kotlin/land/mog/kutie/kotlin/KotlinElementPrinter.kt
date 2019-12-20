package land.mog.kutie.kotlin

import land.mog.kutie.doc.Doc
import land.mog.kutie.doc.DocBuilder
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*

object KotlinElementPrinter {

    private val project by lazy {
        KotlinCoreEnvironment.createForProduction(
            Disposer.newDisposable(),
            CompilerConfiguration(),
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        ).project
    }

    fun printCodeToDoc(code: String): Doc {

        val file = PsiManager.getInstance(project).findFile(
            LightVirtualFile("temp.kt", KotlinFileType.INSTANCE, code)
        ) as KtFile

        return printKtElementToDoc(file)
    }

    private fun printKtElementToDoc(element: KtElement): Doc {
        return when (element) {
            is KtFile -> Doc.join(
                separator = Doc.Line,
                docs = element.declarations.map { printKtElementToDoc(it) }
            )
            is KtBlockExpression -> {
                Doc.join(
                    separator = Doc.Line,
                    docs = element.statements.map { printKtElementToDoc(it) }
                )
            }
            is KtNamedFunction -> {
                val builder = DocBuilder()
                if (element.modifierList != null) {
                    builder.append(printKtElementToDoc(element.modifierList!!))
                }
                builder.appendText("fun ")
                if (element.typeParameters.isNotEmpty()) {
                    builder.append(printTypeParameters(element.typeParameters))
                    builder.appendText(" ")
                }
                if (element.name != null) {
                    builder.appendText(element.name!!)
                }
                builder.append(printParameters(element.valueParameters))
                if (element.hasBlockBody()) {
                    builder.append(
                        printKtElementToDoc(element.bodyBlockExpression!!)
                            .bracketBy(Doc.Text(" {"), Doc.Text("}"), false)
                    )
                } else if (element.hasBody()) {
                    builder.append(
                        Doc.concat(
                            Doc.Text(" ="),
                            Doc.lineOrSpace,
                            printKtElementToDoc(element.bodyExpression!!)
                        ).indent(1)
                    )
                }

                builder.build()
            }
            is KtTypeParameter -> {
                val builder = DocBuilder()
                if (element.modifierList != null) {
                    builder.append(printKtElementToDoc(element.modifierList!!))
                    builder.appendText(" ")
                }
                builder.appendText(element.name!!)
                if (element.extendsBound != null) {
                    builder.appendText(": ")
                    builder.append(printKtElementToDoc(element.extendsBound!!))
                }
                builder.build()
            }
            is KtTypeReference -> printKtElementToDoc(element.typeElement!!)
            is KtUserType -> {
                Doc.Text(element.referencedName!!).let {
                    if (element.typeArgumentList != null)
                        it.plus(printKtElementToDoc(element.typeArgumentList!!))
                    else
                        it
                }
            }
            is KtModifierList -> {
                val builder = DocBuilder()
                element.children.forEach { modifier ->
                    when (modifier) {
                        is KtAnnotation -> builder.append(printKtElementToDoc(modifier)).append(Doc.Line)
                        is KtAnnotationEntry -> builder.append(printKtElementToDoc(modifier)).append(Doc.Line)
                        else -> printElementAsModifier(modifier)?.let { builder.append(it).appendText(" ") }
                    }
                }
                builder.build()
            }
            is KtClass -> {
                val builder = DocBuilder()
                if (element.modifierList != null) {
                    builder.append(printKtElementToDoc(element.modifierList!!))
                }
                builder.appendText(
                    when {
                        element.isData() -> "data class "
                        element.isEnum() -> "enum class "
                        element.isInner() -> "inner class "
                        element.isInterface() -> "interface "
                        element.isSealed() -> "sealed class "
                        else -> "class "
                    }
                )
                builder.appendText(element.name!!)
                // TODO: print parameters, constructor
                if (element.declarations.isNotEmpty()) {
                    builder.append(
                        Doc.join(Doc.Line, element.declarations.map { printKtElementToDoc(it) })
                            .bracketBy(Doc.Text(" {"), Doc.Text("}"), false)
                    )
                }
                builder.build()
            }
            is KtIfExpression -> DocBuilder().apply {
                val condDoc = DocBuilder()
                    .appendText("if (")
                    .append(
                        (Doc.lineOrEmpty + printKtElementToDoc(element.condition!!)).indent(1)
                    )
                    .append(Doc.lineOrEmpty)
                    .appendText(") ")
                    .build().group()
                append(condDoc)

                if (element.then is KtBlockExpression) {
                    appendText("{")
                    append(
                        (Doc.Line + printKtElementToDoc(element.then!!)).indent(1)
                    )
                    append(Doc.Line)
                    appendText("}")
                } else {
                    append(
                        (Doc.Line + printKtElementToDoc(element.then!!))
                    )
                }

                if (element.`else` != null) {
                    if (element.then !is KtBlockExpression)
                        append(Doc.Line)
                    else
                        appendText(" ")
                    appendText(("else"))
                    when (element.`else`) {
                        is KtIfExpression -> appendText(" ").append(printKtElementToDoc(element.`else`!!))
                        is KtBlockExpression -> {
                            appendText(" {")
                            append(
                                (Doc.Line + printKtElementToDoc(element.`else`!!)).indent(1)
                            )
                            append(Doc.Line)
                            appendText("}")
                        }
                        else -> append(
                            (Doc.Line + printKtElementToDoc(element.`else`!!)).indent(1)
                        )
                    }
                }
            }.build()
            is KtBinaryExpression -> {
                val right = if ((element.operationReference.text == "&&" || element.operationReference.text == "||") && element.parent is KtContainerNode) {
                    Doc.lineOrSpace + printKtElementToDoc(element.right!!)
                } else {
                    (Doc.lineOrSpace + printKtElementToDoc(element.right!!)).indent(1)
                }

                (printKtElementToDoc(element.left!!) + Doc.Text(" ") + printKtElementToDoc(element.operationReference) + right).group()
            }
            is KtConstantExpression -> when (element.elementType) {
                KtNodeTypes.CHARACTER_CONSTANT -> Doc.Text("'${element.text}'")
                else -> Doc.Text(element.text)
            }
            is KtSimpleNameExpression -> Doc.Text(element.getReferencedName())
            is KtCallExpression -> DocBuilder().apply {
                append(printKtElementToDoc(element.calleeExpression!!))
                if (element.typeArgumentList != null) {
                    append(printKtElementToDoc(element.typeArgumentList!!))
                }
                append(printKtElementToDoc(element.valueArgumentList!!))
            }.build()
            is KtTypeArgumentList -> printTypeArguments(element.arguments)
            is KtValueArgumentList -> printArguments(element.arguments)
            is KtValueArgument -> printKtElementToDoc(element.getArgumentExpression()!!)
            is KtTypeProjection -> printKtElementToDoc(element.typeReference!!)
            is KtStringTemplateExpression -> DocBuilder().apply {
                if (element.firstChild.textLength == 3) {
                    appendText("\"\"\"")
                    append(
                        ((Doc.lineOrEmpty + Doc.concat(element.entries.map { printKtElementToDoc(it) }))
                        .indent(1) + Doc.lineOrEmpty).group()
                    )
                    appendText("\"\"\"")
                } else {
                    appendText("\"")
                    append(Doc.concat(element.entries.map { printKtElementToDoc(it) }))
                    appendText("\"")
                }
            }.build()
            is KtLiteralStringTemplateEntry -> Doc.Text(element.text)
            is KtSimpleNameStringTemplateEntry -> Doc.Text("$" + element.text)
            is KtBlockStringTemplateEntry -> Doc.Text("\${") + printKtElementToDoc(element.expression!!) + Doc.Text("}")
            is KtEscapeStringTemplateEntry -> Doc.Text(element.text)
            is KtProperty -> DocBuilder().apply {
                if (element.modifierList != null) {
                    append(printKtElementToDoc(element.modifierList!!))
                }
                if (element.isVar) {
                    appendText("var ")
                } else {
                    appendText("val ")
                }
                appendText(element.name!!)
                if (element.typeReference != null) {
                    appendText(": ")
                    append(printKtElementToDoc(element.typeReference!!))
                }
                if (element.hasDelegateExpression()) {
                    appendText(" by ")
                } else {
                    appendText(" = ")
                }
                append(printKtElementToDoc(element.delegateExpressionOrInitializer!!))

            }.build()
            is KtParenthesizedExpression -> Doc.Text("(") + printKtElementToDoc(element.expression!!) + Doc.Text(")")
            is KtLambdaExpression -> DocBuilder().apply {
                appendText("{")
                if (element.valueParameters.isNotEmpty()) {
                    appendText(" ")
                    append(
                        Doc.join(Doc.Text(", "), element.valueParameters.map { printKtElementToDoc(it) })
                    )
                    appendText(" ->")
                }
                append(
                    ((Doc.lineOrSpace + printKtElementToDoc(element.bodyExpression!!)).indent(1) + Doc.lineOrSpace).group()
                )
                appendText("}")

            }.build()
            is KtParameter -> DocBuilder().apply {
                if (element.hasValOrVar()) {
                    if (element.isVarArg) {
                        appendText("var ")
                    } else {
                        appendText("val ")
                    }
                }
                when {
                    element.destructuringDeclaration != null -> {
                        append(printKtElementToDoc(element.destructuringDeclaration!!))
                    }
                    element.name == null -> {
                        appendText("_")
                    }
                    else -> {
                        appendText(element.name!!)
                    }
                }
                if (element.typeReference != null) {
                    appendText(": ")
                    append(printKtElementToDoc(element.typeReference!!))
                }
                if (element.hasDefaultValue()) {
                    appendText(" = ")
                    append(printKtElementToDoc(element.defaultValue!!))
                }
            }.build()
            is KtDestructuringDeclaration -> DocBuilder().apply {
                appendText("(")
                append(
                    Doc.join(Doc.Text(", "), element.entries.map { printKtElementToDoc(it) })
                )
                appendText(")")
            }.build()
            is KtDestructuringDeclarationEntry -> DocBuilder().apply {
                if (element.name == null) {
                    appendText("_")
                } else {
                    appendText(element.name!!)
                }
                if (element.receiverTypeReference != null) {
                    appendText(": ")
                    append(printKtElementToDoc(element.receiverTypeReference!!))
                }
            }.build()
            else -> TODO("Not Supported Yet: ${element::class.java.simpleName}")
        }
    }

    private fun printArguments(elements: List<KtValueArgument>): Doc {
        val paramsDoc = Doc.join(
            separator = Doc.Text(",") + Doc.lineOrSpace,
            docs = elements.map { printKtElementToDoc(it) }
        )

        return paramsDoc.bracketBy(Doc.Text("("), Doc.Text(")"), true)
    }

    private fun printParameters(elements: List<KtParameter>): Doc {
        val paramsDoc = Doc.join(
            separator = Doc.Text(",") + Doc.lineOrSpace,
            docs = elements.map { printKtElementToDoc(it) }
        )

        return paramsDoc.bracketBy(Doc.Text("("), Doc.Text(")"), true)
    }

    private fun printTypeArguments(elements: List<KtTypeProjection>): Doc {
        val params = Doc.join(
            separator = Doc.Text(",") + Doc.lineOrSpace,
            docs = elements.map { printKtElementToDoc(it) }
        )

        return params.bracketBy(Doc.Text("<"), Doc.Text(">"), true)
    }

    private fun printTypeParameters(elements: List<KtTypeParameter>): Doc {
        val params = Doc.join(
            separator = Doc.Text(",") + Doc.lineOrSpace,
            docs = elements.map { printKtElementToDoc(it) }
        )

        return params.bracketBy(Doc.Text("<"), Doc.Text(">"), true)
    }

    private fun printElementAsModifier(element: PsiElement): Doc? {
        return if (element !is PsiWhiteSpace && element.text != "enum" && element.text != "companion") {
            Doc.Text(element.text)
        } else {
            null
        }
    }
}