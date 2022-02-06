package edu.cmu.ckaestne.gdoc2latex.converter


case class LatexDoc(title: String, abstr: String, latexBody: String)

object LatexRenderer {

  def render(doc: IDocument): LatexDoc = LatexDoc(
    renderText(doc.title.content).trim,
    doc.abstr.map(p=>p.map(renderParagraph).mkString("\n\n")).getOrElse(""),
    doc.content.map(renderElement).mkString("\n")
  )

  private def renderParagraph(p: IParagraph): String = renderText(p.content)
  private def renderText(t: List[IFormattedText]): String = t.map(renderTextFragment).mkString

  private def renderTextFragment(t: IFormattedText): String = t match {
    case IPlainText(s) =>
      s.replace("“", "``").replace("”", "''").replace("’", "'").replace("%", "\\%").replace("&", "\\&")
    case IBold(i) => s"\\textbf{${renderText(i)}}"
    case IItalics(i) => s"\\emph{${renderText(i)}}"
    case IReference(i) => s"\\href{$i}}"
    case ICitation(refs) => "\\cite{" + refs.mkString(",") + "}"
    case IURL(link, None) => s"\\url{$link}"
    case IURL(link, Some(text)) => s"\\href{$link}{${renderText(text)}}"
  }

  private def renderElement(t: IDocumentElement): String = t match {
    case IParagraph(c) => renderText(c)
    case IHeading(level, id, text) =>
      val l = if (level == 1) "section" else if (level == 2) "subsection" else "subsubsection"
      val anchor = id.map(headingId => s"\\label{$headingId}\n").getOrElse("")
      s"\\$l{${renderText(text.content).trim}}$anchor"

    case IBulletList(bullets) =>
      bullets.map(renderElement).mkString("\\begin{compactitem}\n\t\\item", "\n\t\\item", "\n\\end{compactitem}\n")


    case IBibliography(items) =>
      items.map(i => s"\\bibitem{${i._1}} ${renderText(i._2.content)}").mkString("\\begin{thebibliography}{100}\n", "\n", "\n\\end{thebibliography}\n")

  }

}
