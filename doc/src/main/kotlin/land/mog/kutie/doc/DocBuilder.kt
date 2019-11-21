package land.mog.kutie.doc

class DocBuilder {
    private var doc: Doc? = null

    fun append(doc: Doc): DocBuilder = this.apply {
        if (this.doc == null) {
            this.doc = doc
        }
        else {
            this.doc = this.doc!! + doc
        }
    }

    fun appendText(text: String): DocBuilder = this.apply {
        append(Doc.Text(text))
    }

    fun build(): Doc = doc ?: Doc.Text("")
}