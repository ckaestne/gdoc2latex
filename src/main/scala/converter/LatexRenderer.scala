package edu.cmu.ckaestne.gdoc2latex.converter

import converter.AbstractRenderer
import edu.cmu.ckaestne.gdoc2latex.util.GDrawing
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils

import java.io.{ByteArrayInputStream, File, InputStream}
import java.net.URI
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util


case class LatexDoc(title: String, abstr: String, latexBody: String)

class LatexRenderer(ignoreImages: Boolean = true, downloadImages: Boolean = false, imgDir: File = new File("."), drawings: List[GDrawing]=Nil)
  extends AbstractRenderer(drawings, imgDir){

  def render(doc: IDocument): LatexDoc = LatexDoc(
    renderText(doc.title.content),
    doc.abstr.map(p => p.map(p=>renderParagraph(p).replace("Abstract: ","")).mkString("\n\n")).getOrElse(""),
    doc.content.map(renderElement).mkString("\n")
  )

  protected def renderParagraph(p: IParagraph): String = renderText(p.content)

  protected def renderText(t: List[IFormattedText]): String = t.map(renderTextFragment).mkString


  protected def getEmojiWithNbsp: List[String] = List("🕮", "🗎", "📰", "🖮", "🔗")

  protected def renderPlainText(s: String): String = {
    val x = s.replace("“", "``").replace("”", "''").replace("’", "'").replace("%", "\\%").replace("&", "\\&").
      replace("_", "\\_").
      replace("#", "\\#").
      replace("$", "\\$").
      replace("\\$\\$", "$").
      replace("α", "$\\alpha$").
      replace("β", "$\\beta$").
      replace("ϕ", "$\\phi$").
      replace("δ", "$\\delta$").
      replace("⇒", "$\\Rightarrow$").
      replace("∀", "$\\forall$").
      replace("¬", "$\\neg$").
      replace("∧", "$\\wedge$").
      replace("⊨", "$\\models$").
      replace("⊥", "$\\perp$").
      replace("∣", "$\\mid$").
      replace("∈", "$\\in$").
      replace("\u200A", " ").
      replace("\u000B", " ").
      replace("—", "--")
    //non-breaking space after some emoji
    getEmojiWithNbsp.foldLeft(x)((x,emoji) => x.replace(emoji+" ",emoji+"~"))
  }

  protected def renderTextFragment(t: IFormattedText): String = t match {
    case IPlainText(s) => renderPlainText(s)
    case IBold(i) => s"\\textbf{${renderText(i)}}"
    case IItalics(i) => s"\\emph{${renderText(i)}}"
    case ISub(i) => "$_\\text{"+renderText(i)+"}$"
    case ISup(i) => "$^\\text{"+renderText(i)+"}$"
    case IHighlight(i,_) => renderText(i)
    case IUnderlined(i) => renderText(i) // not rendering underling
//    case u@IUnderlined(i) =>
//      println("    Link to Section \"" + u.getPlainText()+"\"")
//      s"\\hyperref[${Util.textToId(u.getPlainText())}]{${renderText(List(IItalics(i)))}}"
    case IReference(i) => s"\\href{$i}"
    case ICitation(refs) => "\\cite{" + refs.mkString(",") + "}"
    case IURL(link, None) => s"\\url{${link.replace("#", "\\#").replace("%", "\\%")}}"
    case IURL(link, Some(text)) => s"\\href{${link.replace("#","\\#").replace("%","\\%")}}{${renderText(text)}}"
  }

  protected def itemEnv: String = "compactitem"

  protected def renderElement(t: IDocumentElement): String = t match {
    case IParagraph(c) => renderText(c) + "\n"
    case IHeading(level, id, text) =>
      val l = if (level == 1) "section" else if (level == 2) "subsection" else "subsubsection"
      val anchor = id.map(headingId => s"\\label{$headingId}").getOrElse("")
      s"\\$l{${renderText(text.content)}}$anchor"

    case IBulletList(bullets) =>
      bullets.map(renderElement).mkString(s"\\begin{$itemEnv}\n\t\\item ", "\n\t\\item ", s"\n\\end{$itemEnv}\n")


    case IBibliography(items) =>
      items.map(i => s"\\bibitem{${i._1}} ${renderText(i._2.content)}").mkString("\\begin{thebibliography}{100}\n", "\n", "\n\\end{thebibliography}\n")

    case img@IImage(id: String, uri: String, caption: Option[IParagraph], altTextOption: Option[String], width: Int) => if (ignoreImages) "" else {
      if (downloadImages) {
        resolveImageUri(id, uri) match {
          case Some((filePath, content)) =>
            Files.copy(content, filePath, StandardCopyOption.REPLACE_EXISTING)
            imageLatex(img, Some(filePath))
          case None =>imageLatex(img, None)
        }
      } else
        imageLatex(img, None)
    }

    case ICode(lang, code, caption) =>
      val config = (lang.map("language="+_) :: caption.map(p=> "title={"+renderText(p.content)+"}") :: Nil).flatten
      val configTxt = if (config.isEmpty) "" else config.mkString("[",",","]")
      s"\\begin{minipage}{\\linewidth}\\begin{lstlisting}$configTxt\n$code\\end{lstlisting}\\end{minipage}\n"
  }

  protected def imageLabel(img: IImage): String =
    s"\\label{${img.id}}"

  protected def imageCaption(img: IImage): String =
    img.caption.map(p => "\\caption{" + renderText(p.content) + "}\n").getOrElse("")+
      img.altText.map(p=>"\\alt{"+p+"}\n").getOrElse("")

  protected def imageLocation = "h!tp"

  protected def imageLatex(img: IImage, filePath: Option[Path]): String = {
    val (draft, path) = if (filePath.isDefined) ("", filePath.get.getFileName.toString) else ("draft=false,","")
    s"\\begin{figure}[$imageLocation]\n\\centering\\includegraphics[${draft}width=${imgWidth(img.widthPt)}]{$path}\n${imageCaption(img)}${imageLabel(img)}\\end{figure}\n"
  }



  protected def getDrawingContent(drawing: GDrawing): (String, Array[Byte]) =
    (".pdf",drawing.contentPDF)

  protected def imgWidth(widthInPt: Int): String =
//    widthInPt+"pt"
    f"${((widthInPt / 468d).min(1))}%.2f"+"\\linewidth"

}



