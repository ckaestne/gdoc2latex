package edu.cmu.ckaestne.gdoc2latex.converter

import edu.cmu.ckaestne.gdoc2latex.util.GDrawing
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils

import java.io.{ByteArrayInputStream, File, InputStream}
import java.net.URI
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util


case class LatexDoc(title: String, abstr: String, latexBody: String)

class LatexRenderer(ignoreImages: Boolean = true, downloadImages: Boolean = false, imgDir: File = new File("."), drawings: List[GDrawing]=Nil) {

  def render(doc: IDocument): LatexDoc = LatexDoc(
    renderText(doc.title.content),
    doc.abstr.map(p => p.map(renderParagraph).mkString("\n\n")).getOrElse(""),
    doc.content.map(renderElement).mkString("\n")
  )

  private def renderParagraph(p: IParagraph): String = renderText(p.content)

  private def renderText(t: List[IFormattedText]): String = t.map(renderTextFragment).mkString

  private def renderTextFragment(t: IFormattedText): String = t match {
    case IPlainText(s) => {
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
      List("🕮","🗎","📰","🖮").foldLeft(x)((x,emoji) => x.replace(emoji+" ",emoji+"~"))
    }
    case IBold(i) => s"\\textbf{${renderText(i)}}"
    case IItalics(i) => s"\\emph{${renderText(i)}}"
    case u@IUnderlined(i) =>
      println("Section:" + u.getPlainText())
      s"\\hyperref[${Util.textToId(u.getPlainText())}]{${renderText(i)}}"
    case IReference(i) => s"\\href{$i}"
    case ICitation(refs) => "\\cite{" + refs.mkString(",") + "}"
    case IURL(link, None) => s"\\url{${link.replace("#", "\\#").replace("%", "\\%")}}"
    case IURL(link, Some(text)) => s"\\href{${link.replace("#","\\#").replace("%","\\%")}}{${renderText(text)}}"
  }

  private def renderElement(t: IDocumentElement): String = t match {
    case IParagraph(c) => renderText(c) + "\n"
    case IHeading(level, id, text) =>
      val l = if (level == 1) "section" else if (level == 2) "subsection" else "subsubsection"
      val anchor = id.map(headingId => s"\\label{$headingId}").getOrElse("")
      s"\\$l{${renderText(text.content)}}$anchor"

    case IBulletList(bullets) =>
      bullets.map(renderElement).mkString("\\begin{compactitem}\n\t\\item ", "\n\t\\item ", "\n\\end{compactitem}\n")


    case IBibliography(items) =>
      items.map(i => s"\\bibitem{${i._1}} ${renderText(i._2.content)}").mkString("\\begin{thebibliography}{100}\n", "\n", "\n\\end{thebibliography}\n")

    case IImage(id: String, uri: String, caption: Option[IParagraph], width: Int) => if (ignoreImages) "" else {
      val latexcaption = caption.map(p => "\\caption{" + renderText(p.content) + "}\n").getOrElse("")

      val defaultImg =             s"\\begin{figure}[h!tp]\n\\centering\\includegraphics[draft=false,width=${imgWidth(width)}]{}\n$latexcaption\\end{figure}\n"

      if (downloadImages) {
        resolveImageUri(id, uri) match {
          case Some((filePath, content)) =>
            Files.copy(content, filePath, StandardCopyOption.REPLACE_EXISTING)
            s"\\begin{figure}[h!tp]\n\\centering\\includegraphics[width=${imgWidth(width)}]{${filePath.getFileName.toString}}\n$latexcaption\\end{figure}\n"
          case None =>defaultImg
        }
      } else
        defaultImg
    }

    case ICode(lang, code, caption) =>
      val config = (lang.map("language="+_) :: caption.map(p=> "title={"+renderText(p.content)+"}") :: Nil).flatten
      val configTxt = if (config.isEmpty) "" else config.mkString("[",",","]")
      s"\\begin{lstlisting}$configTxt\n$code\\end{lstlisting}"
  }

  private def resolveImageUri(id: String, uri: String): Option[(Path, InputStream)] = {
    val connection = new URI(uri).toURL().openConnection
    val mimeType = connection.getContentType
    if (!supportedMime.contains(mimeType)) {
      System.err.println(s"unsupported image format \"$mimeType\" from $uri")
      return None
    }
    val content = IOUtils.toByteArray(connection.getInputStream)

    val drawing = drawings.find(x=>util.Arrays.equals(x.contentPNG,content))

    if (drawing.isDefined) {
      val filePath = new File(imgDir, drawing.get.name.toLowerCase().replaceAll("\\W", "-").replace("--", "-")+".pdf").toPath
      println(filePath)
      Some((filePath, new ByteArrayInputStream(drawing.get.contentPDF)))
    } else {
      val filePath = new File(imgDir, DigestUtils.md5Hex(id) + supportedMime(mimeType)).toPath
      Some((filePath, new ByteArrayInputStream(content)))
    }
  }

  val supportedMime = Map("image/jpeg"->".jpg", "image/png"->".png")

  private def imgWidth(widthInPt: Int): String =
//    widthInPt+"pt"
    ((widthInPt/468d).min(1)).formatted("%.2f")+"\\linewidth"

}
