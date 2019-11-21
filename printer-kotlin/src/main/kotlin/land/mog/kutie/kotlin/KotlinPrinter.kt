package land.mog.kutie.kotlin

import kastree.ast.Node
import kastree.ast.psi.Converter
import kastree.ast.psi.Parser
import land.mog.kutie.doc.Doc
import land.mog.kutie.doc.DocBuilder
import org.jetbrains.kotlin.backend.common.onlyIf

private val UNDERSCORE_VAR = Node.Decl.Property.Var("_", null)

object KotlinPrinter {

    fun printStringToDoc(str: String): Doc {
        val node = Parser(Converter.WithExtras()).parseFile(str)
        return printNodeToDoc(node)
    }

    private fun printNodeToDoc(node: Node): Doc {
        return when (node) {
            is Node.File -> printFile(node)
            is Node.Block -> printBlock(node)
            is Node.Decl.Func -> printFunc(node)
            is Node.Decl.Func.Body.Block -> printFuncBlock(node)
            is Node.Decl.Property -> {
                val builder = DocBuilder()
                if (node.anns.isNotEmpty()) {
                    builder.append(
                        printAnnotationSets(node.anns)
                    )
                    builder.append(Doc.Line)
                }
                val mods = node.mods.filterIsInstance<Node.Modifier.Lit>()
                if (mods.isNotEmpty()) {
                    builder.append(
                        Doc.join(
                            separator = Doc.Text(" "),
                            docs = mods.map { printNodeToDoc(it) }
                        )
                    )
                    builder.appendText(" ")
                }
                if (node.readOnly) {
                    builder.appendText("val ")
                }
                else {
                    builder.appendText("var ")
                }

                builder.append(printPropertyVars(node.vars))

                node.receiverType?.let {
                    builder.appendText(": ")
                    builder.append(printNodeToDoc(it))
                }

                node.expr?.let {
                    if (node.delegated) {
                        builder.appendText(" by ")
                    }
                    else {
                        builder.appendText(" = ")
                    }

                    builder.append(printNodeToDoc(it))
                }

                return builder.build()
            }
            is Node.Decl.Property.Var -> {
                val type = node.type
                if (type == null) {
                    return Doc.Text(node.name)
                }
                else {
                    return Doc.Text(node.name) + Doc.Text(": ") + printNodeToDoc(type)
                }
            }
            is Node.Decl.Structured -> printStructured(node)
            is Node.Expr.BinaryOp -> {
                printNodeToDoc(node.lhs) + Doc.Text(" ") + printNodeToDoc(node.oper) + Doc.Text(" ") + printNodeToDoc(node.rhs)
            }
            is Node.Expr.BinaryOp.Oper.Infix -> Doc.Text(node.str)
            is Node.Expr.BinaryOp.Oper.Token -> Doc.Text(node.token.str)
            is Node.Expr.Brace -> printBrace(node)
            is Node.Expr.Brace.Param -> {
                val destructType = node.destructType
                if (destructType == null) {
                    printPropertyVars(node.vars)
                }
                else {
                    printPropertyVars(node.vars) + Doc.Text(": ") + printNodeToDoc(destructType)
                }
            }
            is Node.Expr.Call -> {
                printNodeToDoc(node.expr)
                    .onlyIf({ node.typeArgs.isNotEmpty() }) { it + printTypeArguments(node.typeArgs) }
                    .plus(printArguments(node.args))
            }
            is Node.Expr.Const -> {
                when (node.form) {
                    Node.Expr.Const.Form.CHAR -> Doc.Text("'${node.value}'")
                    else -> Doc.Text(node.value)
                }
            }
            is Node.Expr.If -> printIf(node)
            is Node.Expr.Name -> Doc.Text(node.name)
            is Node.Expr.StringTmpl -> printStringTemplate(node)
            is Node.Modifier.AnnotationSet -> printAnnotationSet(node)
            is Node.Modifier.AnnotationSet.Annotation -> printAnnotation(node)
            is Node.Modifier.Lit -> printLit(node)
            is Node.Stmt.Decl -> printNodeToDoc(node.decl)
            is Node.Stmt.Expr -> printNodeToDoc(node.expr)
            is Node.Type -> {
                val builder = DocBuilder()
                if (node.mods.isNotEmpty()) {
                    builder.append(
                        Doc.join(
                            separator = Doc.Text(" "),
                            docs = node.mods.map { printNodeToDoc(it) }
                        )
                    )
                }
                builder.append(printNodeToDoc(node.ref))

                builder.build()
            }
            is Node.TypeParam -> printTypeParam(node)
            is Node.TypeRef.Simple -> printNodeToDoc(node.pieces.first())
            is Node.TypeRef.Simple.Piece -> {
                Doc.Text(node.name).let {
                    if (node.typeParams.isNotEmpty()) {
                        it + printTypeArguments(node.typeParams)
                    }
                    else {
                        it
                    }
                }
            }
            else -> TODO("Not supported yet node: $node")
        }
    }

    private fun printFile(node: Node.File): Doc {
        return Doc.join(
            separator = Doc.Line,
            docs = node.decls.map { printNodeToDoc(it) }
        )
    }

    private fun printFunc(node: Node.Decl.Func): Doc {
        val builder = DocBuilder()
        val mods = node.mods.filterIsInstance<Node.Modifier.Lit>()

        if (node.anns.isNotEmpty()) {
            builder.append(
                printAnnotationSets(node.anns)
            )
        }
        if (mods.isNotEmpty()) {
            builder.appendText(" ")
            builder.append(
                Doc.concat(mods.map { printNodeToDoc(it) + Doc.Text(" ") })
            )
        }

        builder.appendText("fun")
        if (node.typeParams.isNotEmpty()) {
            builder.appendText(" ")
            builder.append(
                printTypeParameters(node.typeParams)
            )
        }
        if (node.name != null) {
            builder.appendText(" ${node.name}")
        }
        builder.append(printParameters(node.params))

        val body = node.body
        if (body != null) {
            builder.appendText(" ")
            builder.append(printNodeToDoc(body))
        }

        return builder.build()
    }

    private fun printFuncBlock(node: Node.Decl.Func.Body.Block): Doc {
        val blockDoc = Doc.join(
            separator = Doc.Line,
            docs = node.block.stmts.map { printNodeToDoc(it) }
        )

        return blockDoc.bracketBy(Doc.Text("{"), Doc.Text("}"), false)
    }

    private fun printTypeParam(node: Node.TypeParam): Doc {
        val builder = DocBuilder()

        if (node.mods.isNotEmpty()) {
            builder.append(
                Doc.join(
                    separator = Doc.Text(" "),
                    docs = node.mods.map { printNodeToDoc(it) }
                )
            )
            builder.appendText(" ")
        }
        builder.appendText(node.name)

        node.type?.let {
            builder.appendText(": ")
            builder.append(printNodeToDoc(it))
        }

        return builder.build()
    }

    private fun printStructured(node: Node.Decl.Structured): Doc {
        val builder = DocBuilder()
        val mods = node.mods.filterIsInstance<Node.Modifier.Lit>()
        if (node.anns.isNotEmpty()) {
            builder.append(
                printAnnotationSets(node.anns)
            )
        }
        if (mods.isNotEmpty()) {
            builder.appendAll(
                mods.map { printNodeToDoc(it) + Doc.Text(" ") }
            )
        }
        val form = printStructuredForm(node.form)
        builder.append((form + Doc.Text(" ")))

        if (node.form != Node.Decl.Structured.Form.COMPANION_OBJECT || node.name == "Companion") {
            builder.append(Doc.Text(node.name))
        }


        if (node.members.isNotEmpty()) {
            val block = (Doc.Line + Doc.concat(node.members.map { printNodeToDoc(it) })).indent(1)
            builder.append(
                Doc.Text(" {")
                + block
                + Doc.Line
                + Doc.Text("}")
            )
        }

        return builder.build()
    }

    private fun printParameters(parameters: List<Node.Decl.Func.Param>): Doc {
        val paramsDoc = Doc.join(
            separator = Doc.Text(",") + Doc.lineOrSpace,
            docs = parameters.map { printNodeToDoc(it) }
        )

        return paramsDoc.bracketBy(Doc.Text("("), Doc.Text(")"), true)
    }

    private fun printTypeParameters(parameters: List<Node.TypeParam>): Doc {
        val paramsDoc = Doc.join(
            separator = Doc.Text(",") + Doc.lineOrSpace,
            docs = parameters.map { printNodeToDoc(it) }
        )

        return paramsDoc.bracketBy(Doc.Text("<"), Doc.Text(">"), true)
    }

    private fun printStructuredForm(form: Node.Decl.Structured.Form): Doc = Doc.Text(
        when (form) {
            Node.Decl.Structured.Form.CLASS -> "class"
            Node.Decl.Structured.Form.ENUM_CLASS -> "enum class"
            Node.Decl.Structured.Form.INTERFACE -> "interface"
            Node.Decl.Structured.Form.OBJECT -> "object"
            Node.Decl.Structured.Form.COMPANION_OBJECT -> "companion object"
        }
    )

    private fun printAnnotationSet(annotationSet: Node.Modifier.AnnotationSet): Doc {
        val annotations = annotationSet.anns
        if (annotations.size == 1) {
            return Doc.Text("@") + printNodeToDoc(annotations[0])
        }
        else {
            return Doc.Text("@[") + Doc.join(
                separator = Doc.Text(" "),
                docs = annotations.map { printNodeToDoc(it) }
            ) + Doc.Text("]")
        }
    }

    private fun printLit(lit: Node.Modifier.Lit): Doc {
        return Doc.Text(lit.keyword.name.toLowerCase())
    }

    private fun printAnnotation(annotation: Node.Modifier.AnnotationSet.Annotation): Doc {
        return Doc.Text(annotation.names[0])
            .onlyIf({ annotation.typeArgs.isNotEmpty() },  { it + printTypeArguments(annotation.typeArgs) })
            .onlyIf({ annotation.args.isNotEmpty() }, { it + printArguments(annotation.args)} )
    }

    private fun printArguments(arguments: List<Node.ValueArg>): Doc {
        return Doc.join(
            separator = Doc.Text(",") + Doc.lineOrSpace,
            docs = arguments.map { printNodeToDoc(it.expr) }
        ).bracketBy(
            prefix = Doc.Text("("),
            postfix = Doc.Text(")"),
            tight = true
        )
    }

    private fun printIf(node: Node.Expr.If): Doc {
        val builder = DocBuilder()
        val isBrace = node.body is Node.Expr.Brace
        builder
            .appendText("if (")
            .append(printNodeToDoc(node.expr))
            .appendText(") ")

        if (!isBrace) {
            builder.append(
                (Doc.Line + printNodeToDoc(node.body)).indent(1)
            )
        } else {
            builder.append(printNodeToDoc(node.body))
        }

        val elseBody = node.elseBody
        if (elseBody != null) {
            if (!isBrace) {
                builder.append(Doc.Line)
            } else {
                builder.appendText(" ")
            }
            builder.appendText("else ")
            builder.append(printNodeToDoc(elseBody))
        }

        return builder.build()
    }

    private fun printBrace(expr: Node.Expr.Brace): Doc {
        val block = expr.block ?: return Doc.Text("")

        val docs = mutableListOf<Doc>()
        docs.add(Doc.Text("{"))
        if (expr.params.isNotEmpty()) {
            docs.add(Doc.Text(" "))
            docs.add(
                Doc.join(
                    separator = Doc.Text(", "),
                    docs = expr.params.map { printNodeToDoc(it) })
            )
            docs.add(Doc.Text(" ->"))
        }
        val blockDoc = Doc.concat(
            Doc.lineOrSpace,
            printNodeToDoc(block)
        ).indent(1)
        docs.add(blockDoc)
        docs.add(Doc.lineOrSpace)
        docs.add(Doc.Text("}"))
        return Doc.concat(docs)
    }

    private fun printBlock(block: Node.Block): Doc {
        return Doc.join(
            separator = Doc.Line,
            docs = block.stmts.map { printNodeToDoc(it) }
        )
    }

    private fun printStringTemplate(template: Node.Expr.StringTmpl): Doc {
        val surround = Doc.Text(
            if (template.raw) "\"\"\"" else "\""
        )

        return surround + Doc.concat(template.elems.map { printStringTemplateElement(it) }) + surround
    }

    private fun printStringTemplateElement(element: Node.Expr.StringTmpl.Elem): Doc {
        return when (element) {
            is Node.Expr.StringTmpl.Elem.Regular -> Doc.join(
                separator = Doc.NoIndentLine,
                docs = element.str.split('\n').map { Doc.Text(it) }
            )
            is Node.Expr.StringTmpl.Elem.ShortTmpl -> Doc.Text('$' + element.str)
            is Node.Expr.StringTmpl.Elem.LongTmpl -> Doc.Text("\${") + printNodeToDoc(element.expr) + Doc.Text("}")
            is Node.Expr.StringTmpl.Elem.UnicodeEsc -> Doc.Text("\\u" + element.digits)
            is Node.Expr.StringTmpl.Elem.RegularEsc -> Doc.Text("\\" + when(element.char) {
                '\b' -> 'b'
                '\n' -> 'n'
                '\t' -> 't'
                '\r' -> 'r'
                else -> element.char
            })
        }
    }

    private fun printTypeArguments(typeArguments: List<Node.Type?>): Doc {
        val arguments = Doc.join(
            separator = Doc.Text(",") + Doc.lineOrSpace,
            docs = typeArguments.map { printType(it) }
        )

        return arguments.bracketBy(Doc.Text("<"), Doc.Text(">"), true)
    }

    private fun printType(type: Node.Type?): Doc {
        return if (type == null) {
            Doc.Text("Any")
        }
        else {
            val ref = type.ref as? Node.TypeRef.Simple ?: throw IllegalArgumentException("aa")
            printPiece(ref.pieces.first())
        }
    }

    private fun printPiece(piece: Node.TypeRef.Simple.Piece): Doc {
        return Doc.Text(
            buildString {
                append(piece.name)
                if (piece.typeParams.isNotEmpty()) {
                    append(printTypeArguments(piece.typeParams))
                }
            }
        )
    }

    private fun printPropertyVars(vars: List<Node.Decl.Property.Var?>): Doc {
        return when {
            vars.isEmpty() -> Doc.Text("")
            vars.size == 1 -> printNodeToDoc(vars[0] ?: UNDERSCORE_VAR)
            else -> (
                        Doc.Text("(")
                        + Doc.join(
                            separator = Doc.Text(", "),
                            docs = vars.map { printNodeToDoc(it ?: UNDERSCORE_VAR) }
                        )
                        + Doc.Text(")")
                    )
        }
    }

    private fun printAnnotationSets(sets: List<Node.Modifier.AnnotationSet>): Doc {
       return Doc.join(
            separator = Doc.lineOrSpace,
            docs = sets.map { printNodeToDoc(it) }
        )
    }
}