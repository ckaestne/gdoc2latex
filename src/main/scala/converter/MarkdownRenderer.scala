package edu.cmu.ckaestne.gdoc2latex.converter

import converter.AbstractRenderer
import edu.cmu.ckaestne.gdoc2latex.util.GDrawing

import java.io.{ByteArrayInputStream, File}
import java.nio.file.{Files, Path, StandardCopyOption}


class MarkdownRenderer(ignoreImages: Boolean = true, downloadImages: Boolean = false, imgDir: Path = new File(".").toPath, referenceDir: File = new File(".")/*img path will be relative to this dir*/, drawings: List[GDrawing] = Nil,
                       /*chapterURL: Option[String => Option[String]] = None, */imgRenderingFormat: String = "![$cap]($address)\n",codeWithCaptionRenderingFormat: String = "```$lang\n$code```\n*$cap*\n")
    extends AbstractRenderer(drawings){

  def render(doc: IDocument): (String, Map[String, Array[Byte]]) = {
    val prefix = "# "+renderParagraph(doc.title)+"\n\n"
    val (md, files) = renderContent(doc.content)
    (prefix + md, files)
  }

  private def renderContent(content: List[IDocumentElement]): (String, Map[String, Array[Byte]]) = {
    content.map(renderElement).reduce((a, b) => (a._1 + "\n" + b._1, a._2 ++ b._2))
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
    case IReference(i) => "" //s"[$i]($i)"
    case ICitation(_, text, _) => renderText(text) //"\\cite{" + refs.mkString(",") + "}"
    case IURL(link, None) => s"[$link]($link)"
    case IURL(link, Some(text)) => s"[${renderText(text)}]($link)"
    case IFootnote(text) => s"[Footnote: ${text.map(renderParagraph).mkString}]"
  }

  protected def renderElement(t: IDocumentElement): (String, Map[String, Array[Byte]]) = t match {
    case IParagraph(c, _) => (renderText(c) + "\n", Map())
    case IHeading(level, id, text) =>
      if (level == 10) ("**"+renderText(text.content)+"** ", Map())
      else {
        val l = "#" * (level + 1)
        //      val anchor = id.map(headingId => s"\\label{$headingId}\n").getOrElse("")
        (s"$l ${renderText(text.content)}\n", Map())
      }

    case IBulletList(bullets) =>
      val items: List[(String, Map[String, Array[Byte]])] = bullets.map(renderElement)
      val text = items.map(_._1).mkString("  * ", "\n  * ", "\n")
      val files = items.map(_._2).reduce(_ ++ _)
      (text, files)

    case IBibliography(items) =>
      (items.map(i=>i._2+renderElement(i._3)).mkString("  * ", "\n  * ", "\n"), Map())

    case IImage(id: String, uri: String, caption: Option[IParagraph], altTextOption: Option[String], width: Int) => if (ignoreImages) ("", Map()) else {
      val cap = caption.map(p => renderText(p.content)).getOrElse("")
      val altText = altTextOption.getOrElse("").trim
      val altTextMdEncoded = altText.replace("[","\\[").replace("]","\\]").replace("*","\\*").replace("\n", " ")

      var address = uri
      val imgMap: Map[String, Array[Byte]] = if (downloadImages) {
        resolveImageUri(id, uri) match {
          case Some((filePath, content)) =>
//            Files.copy(new ByteArrayInputStream(content), Path.of(filePath), StandardCopyOption.REPLACE_EXISTING)
            address = imgDir.resolve(filePath).toString// "./"+referenceDir.toPath.relativize(Path.of(filePath)).toString
            Map(filePath->content)
          case None => System.err.println("Cannot download image: "+uri); Map()
        }
      } else Map()
      (imgRenderingFormat.replace("$cap",cap).replace("$address", address).replace("$altTextMdEncoded",altTextMdEncoded).replace("$alt",altText), imgMap)
    }

    case ICode(lang, code, caption) =>
      val end = if (code endsWith "\n") "" else "\n"
      if (caption.isDefined) {
        val cap = renderText(caption.get.content)
        (codeWithCaptionRenderingFormat.replace("$code",code+end).replace("$cap",cap).replace("$lang", lang.getOrElse("")), Map())
      } else
        (s"```${lang.getOrElse("")}\n$code$end```\n", Map())

  }

  override protected def getDrawingContent(drawing: GDrawing): (String, Array[Byte]) = (".svg", drawing.contentSVG)


}
