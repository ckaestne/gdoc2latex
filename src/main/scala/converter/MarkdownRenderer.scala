package edu.cmu.ckaestne.gdoc2latex.converter

import converter.AbstractRenderer
import edu.cmu.ckaestne.gdoc2latex.util.GDrawing
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils

import java.io.{ByteArrayInputStream, File, InputStream}
import java.net.URI
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util


class MarkdownRenderer(ignoreImages: Boolean = true, downloadImages: Boolean = false, imgDir: File = new File("."), referenceDir: File = new File(".")/*img path will be relative to this dir*/, drawings: List[GDrawing] = Nil,
                       /*chapterURL: Option[String => Option[String]] = None, */imgRenderingFormat: String = "![$cap]($address)\n",codeWithCaptionRenderingFormat: String = "```$lang\n$code```\n*$cap*\n")
    extends AbstractRenderer(drawings, imgDir){

  def render(doc: IDocument): String = {
    "# "+renderParagraph(doc.title)+"\n\n"+
    doc.content.map(renderElement).mkString("\n")
  }

  protected def renderParagraph(p: IParagraph): String = renderText(p.content)

  protected def renderText(t: List[IFormattedText]): String = t.map(renderTextFragment).mkString

  protected def renderTextFragment(t: IFormattedText): String = t match {
    case IPlainText(s) => s
//      s.replace("--", "—")
    case IBold(i) => s"**${renderText(i)}**"
    case IItalics(i) => s"*${renderText(i)}*"
    case ISup(i) => s"<sup>${renderText(i)}</sup>"
    case ISub(i) => s"<sub>${renderText(i)}</sub>"
    case IHighlight(i,_) => renderText(i)
    case IUnderlined(i) => renderText(i) // not rendering underling
//    case u@IUnderlined(i) =>
//      val link = chapterURL.flatMap(_.apply(u.getPlainText()))
//      link.map(l=>s"[${renderText(i)}]($l)").getOrElse(renderText(i))
    case IReference(i) => ??? //s"[$i]($i)"
    case ICitation(refs) => ??? //"\\cite{" + refs.mkString(",") + "}"
    case IURL(link, None) => s"[$link]($link)"
    case IURL(link, Some(text)) => s"[${renderText(text)}]($link)"
  }

  protected def renderElement(t: IDocumentElement): String = t match {
    case IParagraph(c) => renderText(c) + "\n"
    case IHeading(level, id, text) =>
      val l = "#" * (level+1)
      //      val anchor = id.map(headingId => s"\\label{$headingId}\n").getOrElse("")
      s"$l ${renderText(text.content)}\n"

    case IBulletList(bullets) =>
      bullets.map(renderElement).mkString("  * ", "\n  * ", "\n")


    case IBibliography(items) => ???
    //      items.map(i => s"\\bibitem{${i._1}} ${renderText(i._2.content)}").mkString("\\begin{thebibliography}{100}\n", "\n", "\n\\end{thebibliography}\n")

    case IImage(id: String, uri: String, caption: Option[IParagraph], altTextOption: Option[String], width: Int) => if (ignoreImages) "" else {
      val cap = caption.map(p => renderText(p.content)).getOrElse("")
      val altText = altTextOption.getOrElse("")

      var address = uri
      if (downloadImages) {
        resolveImageUri(id, uri) match {
          case Some((filePath, content)) =>
            Files.copy(content, filePath, StandardCopyOption.REPLACE_EXISTING)
            address = "./"+referenceDir.toPath.relativize(filePath).toString
          case None => System.err.println("Cannot download image: "+uri)
        }
      }
      imgRenderingFormat.replace("$cap",cap).replace("$address", address).replace("$alt",altText)
    }

    case ICode(lang, code, caption) =>
      val end = if (code endsWith "\n") "" else "\n"
      if (caption.isDefined) {
        val cap = renderText(caption.get.content)
        codeWithCaptionRenderingFormat.replace("$code",code+end).replace("$cap",cap).replace("$lang", lang.getOrElse(""))
      } else
        s"```${lang.getOrElse("")}\n$code$end```\n"

  }

  override protected def getDrawingContent(drawing: GDrawing): (String, Array[Byte]) = (".svg", drawing.contentSVG)


}
