package land.mog.kutie.doc

sealed class Doc {

    /**
     * Print this doc as a String.
     * Line length of result string is limited to `width` or shorter when possible.
     *
     * @param width The line length to limit.
     */
    fun print(width: Int): String {

        tailrec fun printQueue(state: RenderingState, queue: List<Doc>, flatten: Boolean): String {
            if (queue.isEmpty()) return state.buildText()
            val doc = queue[0]
            val remain = queue.drop(1)
            return when (doc) {
                is Concat -> printQueue(state, listOf(doc.left, doc.right) + remain, flatten)
                is IfFlattened -> {
                    if (flatten) printQueue(state, doc.flattened + remain, flatten)
                    else printQueue(state, doc.broken + remain, flatten)
                }
                is Text -> printQueue(state.appendText(doc.value), remain, flatten)
                is ForceBreak -> printQueue(state, doc.doc + remain, false)
                is Line -> printQueue(state.appendNewLine(), remain, flatten)
                is NoIndentLine -> printQueue(state.appendText("\n"), remain, flatten)

                is Indent -> {
                    @Suppress("NON_TAIL_RECURSIVE_CALL")
                    val rendered = printQueue(state.indent(doc.indent).copy(builder = StringBuilder()), listOf(doc.doc), flatten)
                    printQueue(state.appendText(rendered), remain, flatten)
                }
                is Group -> {
                    @Suppress("NON_TAIL_RECURSIVE_CALL")
                    val flattened = printQueue(state.copy(builder = StringBuilder()), listOf(doc.doc), true)

                    if (!flattened.contains('\n') && state.position + flattened.length < width) {
                        printQueue(state.appendText(flattened), remain, flatten)
                    }
                    else {
                        @Suppress("NON_TAIL_RECURSIVE_CALL")
                        val broken = printQueue(state.copy(builder = StringBuilder()), listOf(doc.doc), false)

//                        val newPosition = broken.takeLastWhile { it != '\n' }.length.let {
//                            if (it != broken.length) it
//                            else state.position + it
//                        }
                        printQueue(state.appendText(broken), remain, flatten)
                    }
                }
            }
        }

        return printQueue(RenderingState(), listOf(this), true)
    }

    /**
     * Raw text.
     *
     * Limitation:
     * - The text must not contains newline.
     */
    data class Text(val value: String): Doc()
    data class Concat(val left: Doc, val right: Doc): Doc()
    data class Group(val doc: Doc): Doc()
    data class Indent(val indent: Int, val doc: Doc): Doc()
    data class IfFlattened(val flattened: Doc, val broken: Doc): Doc()
    data class ForceBreak(val doc: Doc): Doc()
    object Line: Doc()
    object NoIndentLine: Doc()

    operator fun plus(doc: Doc): Doc = Concat(this, doc)
    operator fun plus(docs: List<Doc>): List<Doc> = listOf(this, *docs.toTypedArray())

    fun bracketBy(prefix: Doc, postfix: Doc, tight: Boolean): Doc {
        val space = if (tight) lineOrEmpty else lineOrSpace
        return Group(
            concat(
                prefix,
                Indent(
                    1,
                    concat(space, this)
                ),
                space,
                postfix
            )
        )
    }

    fun indent(amount: Int): Doc {
        return Indent(amount, this)
    }

    fun forceBreak(): Doc {
        return ForceBreak(this)
    }

    fun group(): Doc {
        return Group(this)
    }

    companion object {

        val lineOrSpace =
            IfFlattened(Text(" "), Line)
        val lineOrEmpty =
            IfFlattened(Text(""), Line)

        fun concat(vararg docs: Doc): Doc {
            if (docs.isEmpty())
                return Text("")

            return docs.reduce { acc, doc -> acc + doc }
        }
        fun concat(docs: List<Doc>): Doc {
            if (docs.isEmpty())
                return Text("")

            return docs.reduce { acc, doc -> acc + doc }
        }

        fun join(separator: Doc, docs: List<Doc>): Doc {
            if (docs.isEmpty()) {
                return Text("")
            }

            return docs.reduce { acc, doc -> acc + separator + doc }
        }
    }
}

private data class RenderingState(val position: Int = 0, val indent: Int = 0, private val builder: StringBuilder = StringBuilder()) {

    fun indent(diff: Int = 1) = this.copy(
        indent = indent + diff
    )

    fun appendNewLine() = this.copy(
        position = indent * 4,
        builder = this.builder.append("\n").append(indentString(this.indent))
    )

    fun appendText(text: String): RenderingState {
        val lastLine = text.takeLastWhile { it != '\n' }
        val position = if (lastLine.length == text.length) {
            position + text.length
        } else {
            lastLine.length
        }

        return this.copy(
            builder = this.builder.append(text),
            position = position
        )
    }

    fun buildText(): String = builder.toString()
}

private fun indentString(indent: Int): String = "    ".repeat(indent)