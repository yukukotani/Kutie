import land.mog.kutie.doc.Doc

fun main() {
    val params = Doc.join(
        separator = Doc.Text(",") +  Doc.lineOrSpace,
        docs = listOf(
            Doc.Text("val test: String"),
            Doc.Text("val num: Int")
        )
    )
    val doc = params.bracketBy(
        prefix =  Doc.Text("class Test("),
        postfix =  Doc.Text(")"),
        tight = true
    )
    println(doc.print(30))
    println(doc.print(100))
}