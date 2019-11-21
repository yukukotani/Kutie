package land.mog.kutie.doc

sealed class Doc {

    /**
     * Print this doc as a String.
     * Line length of result string is limited to `width` or shorter when possible.
     *
     * @param width The line length to limit.
     */
    fun print(width: Int): String {

        tailrec fun renderQueue(env: RenderingEnvironment, queue: List<Doc>, builder: StringBuilder, flatten: Boolean): String {
            if (queue.isEmpty()) return builder.toString()
            val doc = queue[0]
            val remain = queue.drop(1)
            return when (doc) {
                is Concat -> renderQueue(env, listOf(doc.left, doc.right) + remain, builder, flatten)
                is IfFlattened -> {
                    if (flatten) renderQueue(env, doc.flattened + remain, builder, flatten)
                    else renderQueue(env, doc.broken + remain, builder, flatten)
                }
                is Text -> renderQueue(env.addPosition(doc.value.length), remain, builder.append(doc.value), flatten)
                is Line -> renderQueue(env, remain, builder.append('\n').append(
                    indentString(env.indent)
                ), flatten)
                is NoIndentLine -> renderQueue(env, remain, builder.append('\n'), flatten)

                is Indent -> {
                    @Suppress("NON_TAIL_RECURSIVE_CALL")
                    val rendered = renderQueue(env.indent(doc.indent), listOf(doc.doc), StringBuilder(), flatten)
                    renderQueue(env, remain, builder.append(rendered), flatten)
                }
                is Group -> {
                    @Suppress("NON_TAIL_RECURSIVE_CALL")
                    val flattened = renderQueue(RenderingEnvironment(), listOf(doc.doc), StringBuilder(), true)

                    if (!flattened.contains('\n') && env.position + flattened.length < width) {
                        renderQueue(env.addPosition(flattened.length), remain, builder.append(flattened), flatten)
                    }
                    else {
                        @Suppress("NON_TAIL_RECURSIVE_CALL")
                        val broken = renderQueue(env, listOf(doc.doc), StringBuilder(), false)

                        val newPosition = broken.takeLastWhile { it != '\n' }.length.let {
                            if (it != broken.length) it
                            else env.position + it
                        }
                        renderQueue(env.setPosition(newPosition), remain, builder.append(broken), flatten)
                    }
                }
            }
        }

        return renderQueue(RenderingEnvironment(), listOf(this), StringBuilder(), true)
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

private data class RenderingEnvironment(val position: Int = 0, val indent: Int= 0) {

    fun addPosition(diff: Int) = this.copy(position = position + diff)
    fun setPosition(new: Int) = this.copy(position = new)
    fun indent(diff: Int = 1) = this.copy(indent = indent + diff)
}

private fun indentString(indent: Int): String = "    ".repeat(indent)