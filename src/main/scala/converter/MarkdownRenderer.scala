package edu.cmu.ckaestne.gdoc2latex.converter

import edu.cmu.ckaestne.gdoc2latex.util.GDrawing
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils

import java.io.{ByteArrayInputStream, File, InputStream}
import java.net.URI
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util


class MarkdownRenderer(ignoreImages: Boolean = true, downloadImages: Boolean = false, imgDir: File = new File("."), drawings: List[GDrawing] = Nil,
                       chapterURL: Option[String => String] = None) {

  def render(doc: IDocument): String = {
    "# "+renderParagraph(doc.title)+"\n\n"+
    doc.content.map(renderElement).mkString("\n")
  }

  private def renderParagraph(p: IParagraph): String = renderText(p.content)

  private def renderText(t: List[IFormattedText]): String = t.map(renderTextFragment).mkString

  private def renderTextFragment(t: IFormattedText): String = t match {
    case IPlainText(s) =>
      s.replace("--", "—")
    case IBold(i) => s"**${renderText(i)}**"
    case IItalics(i) => s"*${renderText(i)}*"
    case u@IUnderlined(i) =>
      if (chapterURL.isDefined)
        s"[${renderText(i)}](${chapterURL.get.apply(u.getPlainText())})"
      else renderText(i)
    case IReference(i) => ??? //s"[$i]($i)"
    case ICitation(refs) => ??? //"\\cite{" + refs.mkString(",") + "}"
    case IURL(link, None) => s"[$link]($link)"
    case IURL(link, Some(text)) => s"[${renderText(text)}]($link)"
  }

  private def renderElement(t: IDocumentElement): String = t match {
    case IParagraph(c) => renderText(c) + "\n"
    case IHeading(level, id, text) =>
      val l = "#" * (level+1)
      //      val anchor = id.map(headingId => s"\\label{$headingId}\n").getOrElse("")
      s"$l ${renderText(text.content)}\n"

    case IBulletList(bullets) =>
      bullets.map(renderElement).mkString("  * ", "\n  * ", "\n")


    case IBibliography(items) => ???
    //      items.map(i => s"\\bibitem{${i._1}} ${renderText(i._2.content)}").mkString("\\begin{thebibliography}{100}\n", "\n", "\n\\end{thebibliography}\n")

    case IImage(id: String, uri: String, caption: Option[IParagraph], width: Int) => if (ignoreImages) "" else {
      val cap = caption.map(p => "*" + renderText(p.content) + "*\n").getOrElse("")

      var address = uri
      if (downloadImages) {
        resolveImageUri(id, uri) match {
          case Some((filePath, content)) =>
            Files.copy(content, filePath, StandardCopyOption.REPLACE_EXISTING)
            address = "./"+filePath.getFileName.toString
          case None => println("Cannot download image: "+uri)
        }
      }
      s"![$cap]($address)\n"
    }

    case ICode(lang, code, caption) =>
      val cap = caption.map(p => "*" + renderText(p.content) + "*\n").getOrElse("")
      val end = if (code endsWith "\n") "" else "\n"
      s"```${lang.getOrElse("")}\n$code$end```\n$cap"
  }

  private def resolveImageUri(id: String, uri: String): Option[(Path, InputStream)] = {
    val connection = new URI(uri).toURL().openConnection
    val mimeType = connection.getContentType
    if (!supportedMime.contains(mimeType)) {
      System.err.println(s"unsupported image format \"$mimeType\" from $uri")
      return None
    }
    val content = IOUtils.toByteArray(connection.getInputStream)

    val drawing = drawings.find(x => util.Arrays.equals(x.contentPNG, content))

    if (drawing.isDefined) {
      val filePath = new File(imgDir, Util.textToId(drawing.get.name) + ".svg").toPath
      println(filePath)
      Some((filePath, new ByteArrayInputStream(drawing.get.contentSVG)))
    } else {
      val filePath = new File(imgDir, DigestUtils.md5Hex(id) + supportedMime(mimeType)).toPath
      Some((filePath, new ByteArrayInputStream(content)))
    }
  }

  val supportedMime = Map("image/jpeg" -> ".jpg", "image/png" -> ".png")


}
