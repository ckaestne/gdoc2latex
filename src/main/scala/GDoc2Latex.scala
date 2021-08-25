import com.google.api.services.docs.v1.model.{Document, Paragraph, ParagraphElement, StructuralElement}

import java.io.{File, FileWriter, PrintWriter}
import scala.collection.mutable
import scala.jdk.CollectionConverters._


object GDoc2Latex extends App {

  val doc = GDocConnection.getDocument()
  val outw: PrintWriter =
    if (args.size >= 1) new PrintWriter(new FileWriter(new File(args(0))))
    else new PrintWriter(System.out);
  val outw2: PrintWriter =
    if (args.size == 2) new PrintWriter(new FileWriter(new File(args(1))))
    else new PrintWriter(System.out);

  var _abstract = ""
  var _title = "title"

  process(outw, outw2, doc);
  outw.close()
  outw2.close()


  def getParagraphText(p: Paragraph): String = if (p.getElements == null || isPaperpileRef(p)) "" else {
    p.getElements.asScala.flatMap(getParagraphElementText).mkString.trim
  }

  def getParagraphElementText(e: ParagraphElement): Option[String] = {
    if (e.getTextRun == null) System.err.println(e)
    if (e.getTextRun == null || e.getTextRun.getContent == null) None
    else {
      var text = processPlainText(e.getTextRun.getContent)
      val style = e.getTextRun.getTextStyle
      //use strikethrough as comment
      if (style != null && style.getStrikethrough)
        return None
      if (style != null && style.getBold && text.trim.nonEmpty)
        text = s"\\textbf{$text}"
      if (style != null && style.getItalic && text.trim.nonEmpty)
        text = s"\\emph{$text}"
      if (style != null && style.getLink != null && text.trim.nonEmpty) {
        val link = style.getLink.getUrl
        val hlink = style.getLink.getHeadingId
        if (hlink != null) {
          text = s"\\ref{$hlink}"
        } else if (link != null && (link contains "paperpile.com/c/")) {
          val refs = link.drop(link.lastIndexOf("/") + 1).split("\\+")
          text = "\\cite{" + refs.mkString(",") + "}"
        } else if (link != null && !(link contains "paperpile.com/b/"))
          System.err.println(s"unsupported direct link ${style.getLink.getUrl} for $text")
      }


      Some(text)
    }
  }

  def isPaperpileRef(p: Paragraph): Boolean =
    if (p.getElements == null) false
    else p.getElements.asScala.exists(hasPaperpileRefLink)

  private def hasPaperpileRefLink(e: ParagraphElement): Boolean = getPaperpileRefLink(e).isDefined

  private def getPaperpileRefLink(e: ParagraphElement): Option[String] =
    if (e.getTextRun == null ||
      e.getTextRun.getTextStyle == null ||
      e.getTextRun.getTextStyle.getLink == null ||
      e.getTextRun.getTextStyle.getLink.getUrl == null) None
    else {
      val link = e.getTextRun.getTextStyle.getLink.getUrl
      if (link contains "paperpile.com/b/")
        Some(link.drop(link.lastIndexOf("/") + 1))
      else None
    }


  def processPlainText(s: String): String =
    s.replace("“", "``").replace("”", "''").replace("’", "'").replace("%", "\\%").replace("&", "\\&")


  def processParagraph(out: PrintWriter, p: Paragraph): Unit = {
    if (p.getBullet != null)
      out.println("\\begin{compactitem}\\item")
    val text = getParagraphText(p)
    if (text startsWith "Abstract: ")
      _abstract = text.drop(10)
    else
      out.println(text + "\n")
    if (p.getBullet != null)
      out.println("\\end{compactitem}")
  }

  def printPaperpileRefs(out: PrintWriter, paragraphs: List[Paragraph]): Unit = {
    out.println("\\begin{thebibliography}{100}")
    paragraphs.filter(isPaperpileRef).foreach(printPaperpileRef(out, _))
    out.println("\\end{thebibliography}")
  }

  def printPaperpileRef(out: PrintWriter, p: Paragraph): Unit = {
    val f = p.getElements.asScala.filter(hasPaperpileRefLink)
    if (f.isEmpty) return
    val id = getPaperpileRefLink(f.head).get
    val ref = f.flatMap(getParagraphElementText).mkString
    out.println(s"\\bibitem{$id} $ref")
  }

  def process(out: PrintWriter,out2: PrintWriter, doc: Document): Unit = {
    val body = doc.getBody

    val paragraphs = body.getContent.asScala.flatMap(se => Option(se.getParagraph)).toList

    for (p: Paragraph <- paragraphs) {
      //      println(c)
      val style = p.getParagraphStyle.getNamedStyleType
      val ptext = getParagraphText(p)
      if ("TITLE" == style && ptext.trim.nonEmpty)
        _title = ptext
      else if ("HEADING_1" == style && ptext.trim.nonEmpty)
        out.println(s"\n\\section{${ptext}}\n")
      else if ("HEADING_2" == style && ptext.trim.nonEmpty)
        out.println(s"\n\\subsection{${ptext}}\n")
      else if ("HEADING_3" == style && ptext.trim.nonEmpty)
        out.println(s"\n\\subsubsection{${ptext}}\n")
      else if ("NORMAL_TEXT" == style)
        processParagraph(out, p)
      else
        System.err.println("unknonw style: " + style)

      val headingId = p.getParagraphStyle.getHeadingId
      if (headingId != null)
        out.println(s"\\label{$headingId}")


    }
    printPaperpileRefs(out, paragraphs)

    out2.println(s"\\newcommand\\papertitle{${_title}}")
    out2.println(s"\\newcommand\\paperabstract{${_abstract}}")

    //    println(doc)
  }
}
