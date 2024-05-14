package edu.cmu.ckaestne.gdoc2latex.converter

import com.google.api.services.docs.v1.model.{Document, InlineObject, Paragraph, ParagraphElement}

import scala.collection.convert.ImplicitConversions.`map AsScala`
import scala.jdk.CollectionConverters._


class GDocParser {


  def convert(doc: Document): IDocument = {
    val body = doc.getBody
    var title: IParagraph = IParagraph(List(IPlainText(Option(doc.getTitle).getOrElse(""))))
    var abstr: Option[List[IParagraph]] = None

    val paragraphs = body.getContent.asScala.flatMap(se => Option(se.getParagraph)).toList

    val (bibliographyParagraphs, mainParagraphs) = paragraphs.partition(isPaperpileRef)

    val inlineObjects: Map[String, InlineObject] = if (doc.getInlineObjects==null) Map() else Map.from(doc.getInlineObjects)
    val footnotes: Map[String, List[IParagraph]] = getFootnotes(doc)
    val context = Context(inlineObjects, footnotes)

    var mainContent: List[IDocumentElement] = Nil

    def addParagraph(p: IParagraph, isBullet: Boolean): Unit = {
      if (!isBullet)
        mainContent ::= p
      else
        mainContent = mainContent match {
          case IBulletList(ps) :: tail => IBulletList(p :: ps) :: tail
          case e => IBulletList(List(p)) :: e
        }
    }

    for (paragraph <- mainParagraphs.reverse; pe <- convertParagraph(context, paragraph))
      pe match {
        case p: IParagraph if p.trimNonEmpty || p.indexTerms.nonEmpty =>
          val namedStyle = paragraph.getParagraphStyle.getNamedStyleType
          val headingId = Option(paragraph.getParagraphStyle.getHeadingId)
          if ("TITLE" == namedStyle)
            title = p
          else if ("HEADING_1" == namedStyle) {
            mainContent ::= IHeading(1, headingId, p)
          } else if ("HEADING_2" == namedStyle)
            mainContent ::= IHeading(2, headingId, p)
          else if ("HEADING_3" == namedStyle)
            mainContent ::= IHeading(3, headingId, p)
          else if ("NORMAL_TEXT" == namedStyle) {
            if (p.plainText.startsWith("Abstract: "))
              abstr = Some(List(p))
            else
              addParagraph(p, paragraph.getBullet != null)
          } else {
            System.err.println("unknown style: " + namedStyle)
          }
        case _: IParagraph =>
        case i: IImage => mainContent ::= i
      }

    val bibitems: List[(String, IParagraph)] =
      for (bibitem <- bibliographyParagraphs;
           p <- convertTextParagraph(context, bibitem); if p.trimNonEmpty;
           f = bibitem.getElements.asScala.toList.take(1).filter(hasPaperpileRefLink) ++ bibitem.getElements.asScala.toList.drop(1); if f.nonEmpty) yield {
        val id = getPaperpileRefLink(f.head).get
        val text = IParagraph(postprocessingTextFragments(convertParagraphText(f, context, Set())))
        (id, text)
      }
    if (bibitems.nonEmpty)
      mainContent :+= IBibliography(bibitems)


    IDocument(title, abstr, postprocessingDocumentElements(mainContent))
  }


  private def convertParagraph(context: Context, p: Paragraph): Option[IDocumentElement] =
    if (p.getElements == null) None
    else extractImage(context, p).orElse(
      Some(IParagraph(postprocessingTextFragments(convertParagraphText(p.getElements.asScala.toList, context)), findIndexTerms(p))))

  private def convertTextParagraph(context: Context, p: Paragraph): Option[IParagraph] =
    if (p.getElements == null) None
    else
      Some(IParagraph(postprocessingTextFragments(convertParagraphText(p.getElements.asScala.toList, context)), findIndexTerms(p)))


  private sealed trait SequenceFormatting {
    def hasFormatting(e: ParagraphElement): Boolean

    def continuesFormatting(from: ParagraphElement, to: ParagraphElement) = hasFormatting(from) && hasFormatting(to)

    def seqLength(seq: List[ParagraphElement]): Int = if (seq.isEmpty || !hasFormatting(seq.head)) 0 else {
      var result = 1
      val head = seq.head
      var tail = seq.tail
      while (tail.nonEmpty && tail.head.getTextRun.getContent != "\n" && continuesFormatting(head, tail.head)) {
        result += 1
        tail = tail.tail
      }
      result
    }

    def split(seq: List[ParagraphElement]): (List[ParagraphElement], List[ParagraphElement]) = {
      val l = seqLength(seq)
      (seq.take(l), seq.drop(l))
    }

    def getFormatter(e: ParagraphElement): List[IFormattedText] => List[IFormattedText]
  }

  private def isWhitespace(c: Char) = c == ' '

  private def isWordChar(c: Char) = "\\w".r.matches("" + c)

  /**
   * trims out non-word characters in the front end whitespace at the end (only in plain-text fragments, assuming that inner ones have been processed correctly)
   */
  private[converter] def trimFrontEnd(l: List[IFormattedText]): (String /*prefix*/ , List[IFormattedText], String /*postfix*/ ) = if (l.isEmpty) ("", l, "") else {
    var inner = l
    var prefix = ""
    var postfix = ""
    var newHead = inner.head
    inner.head match {
      case IPlainText(text) =>
        prefix = text.takeWhile(isWhitespace)
        newHead = IPlainText(text.drop(prefix.length))
      case _ =>
    }
    if (inner.head != newHead)
      inner = newHead :: inner.tail
    var newLast = inner.last
    inner.last match {
      case IPlainText(text) =>
        postfix = text.reverse.takeWhile(isWhitespace).reverse
        newLast = IPlainText(text.dropRight(postfix.length))
      case _ =>
    }
    if (inner.last != newLast)
      inner = (newLast :: inner.reverse.tail).reverse
    (prefix, inner, postfix)
  }

  private def applyAfterTrim(f: List[IFormattedText] => IFormattedText): List[IFormattedText] => List[IFormattedText] = (inner) => {
    val (prefix, newInner, postfix) = trimFrontEnd(inner)

    var result: List[IFormattedText] = Nil
    if (postfix.nonEmpty)
      result = IPlainText(postfix) :: result
    result = f(newInner) :: result
    if (prefix.nonEmpty)
      result = IPlainText(prefix) :: result
    result
  }


  private object BoldFormatting extends SequenceFormatting {
    override def hasFormatting(e: ParagraphElement): Boolean =
      if (e.getTextRun != null && e.getTextRun.getTextStyle != null) e.getTextRun.getTextStyle.getBold else false

    def getFormatter(e: ParagraphElement): List[IFormattedText] => List[IFormattedText] = applyAfterTrim(IBold.apply)
  }

  private object UnderlinedFormatting extends SequenceFormatting {
    override def hasFormatting(e: ParagraphElement): Boolean =
      if (e.getTextRun != null && e.getTextRun.getTextStyle != null) e.getTextRun.getTextStyle.getUnderline && e.getTextRun.getTextStyle.getLink == null else false

    def getFormatter(e: ParagraphElement): List[IFormattedText] => List[IFormattedText] = applyAfterTrim(IUnderlined.apply)
  }

  private object ItalicsFormatting extends SequenceFormatting {
    override def hasFormatting(e: ParagraphElement): Boolean =
      if (e.getTextRun != null && e.getTextRun.getTextStyle != null) e.getTextRun.getTextStyle.getItalic else false

    def getFormatter(e: ParagraphElement): List[IFormattedText] => List[IFormattedText] = applyAfterTrim(IItalics.apply)
  }

  private object HighlightingFormatting extends SequenceFormatting {
    override def hasFormatting(e: ParagraphElement): Boolean =
      if (e.getTextRun != null && e.getTextRun.getTextStyle != null) e.getTextRun.getTextStyle.getBackgroundColor != null else false


    private def rgbToHtmlColor(r: Float, g: Float, b: Float): String =
      f"#${(r * 255).toInt}%02x${(g * 255).toInt}%02x${(b * 255).toInt}%02x"


    def getFormatter(e: ParagraphElement): List[IFormattedText] => List[IFormattedText] = {
      val color = e.getTextRun.getTextStyle.getBackgroundColor.getColor.getRgbColor
      applyAfterTrim(x => IHighlight.apply(x, rgbToHtmlColor(color.getRed, color.getGreen, color.getBlue)))
    }
  }

  private object SubFormatting extends SequenceFormatting {
    override def hasFormatting(e: ParagraphElement): Boolean =
      if (e.getTextRun != null && e.getTextRun.getTextStyle != null) e.getTextRun.getTextStyle.getBaselineOffset == "SUBSCRIPT" else false

    def getFormatter(e: ParagraphElement): List[IFormattedText] => List[IFormattedText] = applyAfterTrim(ISub.apply)
  }

  private object SupFormatting extends SequenceFormatting {
    override def hasFormatting(e: ParagraphElement): Boolean =
      if (e.getTextRun != null && e.getTextRun.getTextStyle != null) e.getTextRun.getTextStyle.getBaselineOffset == "SUPSCRIPT" else false

    def getFormatter(e: ParagraphElement): List[IFormattedText] => List[IFormattedText] = applyAfterTrim(ISup.apply)
  }

  private object LinkFormatting extends SequenceFormatting {
    override def hasFormatting(e: ParagraphElement): Boolean =
      e.getTextRun != null &&
        e.getTextRun.getTextStyle != null &&
        e.getTextRun.getTextStyle.getLink != null &&
        (e.getTextRun.getTextStyle.getLink.getUrl == null || !e.getTextRun.getTextStyle.getLink.getUrl.contains("paperpile.com/b/"))

    def getFormatter(e: ParagraphElement): List[IFormattedText] => List[IFormattedText] = (inner: List[IFormattedText]) => {
      assert(e != null && e.getTextRun != null && e.getTextRun.getTextStyle != null &&
        e.getTextRun.getTextStyle.getLink != null)
      val style = e.getTextRun.getTextStyle

      val link = style.getLink.getUrl
      val hlink = style.getLink.getHeadingId
      assert(hlink != null || link != null)
      if (hlink != null) {
        List(IReference(hlink))
      } else if (link != null && (link contains "paperpile.com/c/")) {
        val refs = link.drop(link.lastIndexOf("/") + 1).split("\\+")
        List(ICitation(refs.toList, inner))
      } else {
        if (link == inner.map(_.getPlainText()).mkString)
          List(IURL(link, None))
        else applyAfterTrim(i => IURL(link, Some(i)))(inner)
      }
    }
  }

  private val sequenceFormattings = List(BoldFormatting, ItalicsFormatting, LinkFormatting, UnderlinedFormatting, SubFormatting, SupFormatting, HighlightingFormatting)

  private def isComment(e: ParagraphElement): Boolean =
    if (e.getTextRun != null && e.getTextRun.getTextStyle != null) e.getTextRun.getTextStyle.getStrikethrough else false

  private def cleanText(str: String) = str.replace("\n", "")


  case class Context(inlineObjects: Map[String, InlineObject], footnotes: Map[String, List[IParagraph]])

  /**
   * text convertion: italics, bold, and links/references/citations are recognized and need to start/stop at word boundaries. strikethrough is ignored as comment at the character level.
   */

  private def convertParagraphText(paragraphElements: List[ParagraphElement], context: Context, outerFormatting: Set[SequenceFormatting] = Set()): List[IFormattedText] =
    if (paragraphElements.isEmpty) Nil
    else if (isComment(paragraphElements.head)) convertParagraphText(paragraphElements.tail, context)
    else if (paragraphElements.head.getFootnoteReference!=null) {
     IFootnote(context.footnotes(paragraphElements.head.getFootnoteReference.getFootnoteId)) ::  convertParagraphText(paragraphElements.tail, context)
    }    else {
      // if bold or italics, grab the longest subsequence with either formatting and process that recursively
      val longestSeqFormatting = sequenceFormattings.filterNot(outerFormatting.contains).filter(_.seqLength(paragraphElements) > 0).maxByOption(_.seqLength(paragraphElements))
      if (longestSeqFormatting.isDefined) {
        val (formattedHeads, tail) = longestSeqFormatting.get.split(paragraphElements)
        val formatter = longestSeqFormatting.get.getFormatter(paragraphElements.head)
        formatter(convertParagraphText(formattedHeads, context, outerFormatting + longestSeqFormatting.get)) ++
          convertParagraphText(tail, context, outerFormatting)
      } else {
        // process plain text element at head by processing tail and adding plain text to the front (avoid sequence of two plaintext elements by merging them instead)
        if (paragraphElements.head.getTextRun == null) convertParagraphText(paragraphElements.tail, context)
        else {
          val newText = cleanText(paragraphElements.head.getTextRun.getContent)
          convertParagraphText(paragraphElements.tail, context, outerFormatting) match {
            case IPlainText(t) :: tail => IPlainText(newText + t) :: tail
            case e => IPlainText(newText) :: e
          }
        }
      }
    }

  private def extractImage(context: Context, p: Paragraph): Option[IImage] = {
    val inlineObjects = p.getElements.asScala.filter(_.getInlineObjectElement != null)
    if (inlineObjects.isEmpty) return None
    if (inlineObjects.size > 1) {
      System.err.println(s"Multiple inline objects per paragraph not supported ${p}")
      return None
    }
    val text = p.getElements.asScala.map(e => {
      val t = e.getTextRun;
      if (t == null) "" else t.getContent
    }).mkString
    if (text.trim.nonEmpty) {
      System.err.println(s"Text in the same paragraph as inline object not supported \"$text\"")
      return None
    }
    val objectId = inlineObjects.head.getInlineObjectElement.getInlineObjectId
    val obj = context.inlineObjects(objectId)
    if (obj == null || obj.getInlineObjectProperties == null || obj.getInlineObjectProperties.getEmbeddedObject == null || obj.getInlineObjectProperties.getEmbeddedObject.getImageProperties == null) {
      System.err.println(s"Inline object $objectId not found or not an image: $obj")
      return None
    }
    val uri = obj.getInlineObjectProperties.getEmbeddedObject.getImageProperties.getContentUri
    val width = obj.getInlineObjectProperties.getEmbeddedObject.getSize.getWidth.getMagnitude.toInt
    val altText = obj.getInlineObjectProperties.getEmbeddedObject.getDescription

    if (obj.getInlineObjectProperties.getEmbeddedObject.getImageProperties.getCropProperties.size() > 0)
      System.err.println(s"    Warning: Image cropping not supported $uri")

    Some(IImage(objectId, uri, None, Option(altText), width))
  }

  /**
   * postprocessing steps:
   * * merges image with subsequent paragraph as caption if in italics
   */
  private def postprocessingDocumentElements(l: List[IDocumentElement]): List[IDocumentElement] = l match {
    // detect code fragments
    case (p@IParagraph(_, _)) :: tail if p.plainText.trim startsWith "```" =>
      var t = tail
      val text = p.plainText.replace('\u000B', '\n')
      val lang = text.trim.drop(3).takeWhile(_ != '\n').trim // text after ```
      var code = text.dropWhile(_ != '\n').drop(1).replaceAll("```\\s*$", "") // skip first line and remove closing ``` if there
      var end = text.trim.length > 3 && text.trim.endsWith("```")
      var caption: Option[IParagraph] = None
      while (t.nonEmpty && t.head.isInstanceOf[IParagraph] && !end) {
        val p = t.head.asInstanceOf[IParagraph]
        val text = p.plainText.replace('\u000B', '\n')
        end = text.trim.endsWith("```")
        code = (if (code.nonEmpty) code + "\n" else "") + text.replaceAll("```\\s*$", "")
        t = t.tail
      }
      if (end && t.nonEmpty && t.head.isInstanceOf[IParagraph]) {
        val p = t.head.asInstanceOf[IParagraph]
        if (p.content.size == 1 && p.content.head.isInstanceOf[IItalics]) {
          caption = Some(IParagraph(p.content.head.asInstanceOf[IItalics].elements))
          t = t.tail
        }
      }
      if (!end) {
        //not a valid code sequence
        p :: postprocessingDocumentElements(tail)
      } else {
        ICode(if (lang != "") Some(lang) else None, code, caption) :: postprocessingDocumentElements(t)
      }
    // image followed by italics paragraph is image with caption
    case IImage(id, uri, None, alt, width) :: IParagraph(inner, indexTerms) :: tail if inner.size == 1 && inner.head.isInstanceOf[IItalics] =>
      IImage(id, uri, Some(IParagraph(inner.head.asInstanceOf[IItalics].elements, indexTerms)), alt, width) :: postprocessingDocumentElements(tail)
    case head :: tail => head :: postprocessingDocumentElements(tail)
    case Nil => Nil
  }

  /**
   * deletes empty fragments and merges subsequent plain text fragments
   */
  private[converter] def postprocessingTextFragments(l: List[IFormattedText]): List[IFormattedText] = (l match {
    case IPlainText(c) :: Nil if c.trim.isEmpty => Nil
    case IPlainText(c) :: tail if c.isEmpty => postprocessingTextFragments(tail)
    case IUnderlined(i) :: tail =>
      val newI = postprocessingTextFragments(i)
      val newTail = postprocessingTextFragments(tail)
      if (newI.isEmpty)
        newTail else IUnderlined(newI) :: newTail
    case IBold(i) :: tail =>
      val newI = postprocessingTextFragments(i)
      val newTail = postprocessingTextFragments(tail)
      if (newI.isEmpty)
        newTail else IBold(newI) :: newTail
    case IItalics(i) :: tail =>
      val newI = postprocessingTextFragments(i)
      val newTail = postprocessingTextFragments(tail)
      if (newI.isEmpty)
        newTail else IItalics(newI) :: newTail
    case IHighlight(i, color) :: tail =>
      val newI = postprocessingTextFragments(i)
      val newTail = postprocessingTextFragments(tail)
      if (newI.isEmpty)
        newTail else IHighlight(newI, color) :: newTail
    case ISub(i) :: tail =>
      val newI = postprocessingTextFragments(i)
      val newTail = postprocessingTextFragments(tail)
      if (newI.isEmpty)
        newTail else ISub(newI) :: newTail
    case ISup(i) :: tail =>
      val newI = postprocessingTextFragments(i)
      val newTail = postprocessingTextFragments(tail)
      if (newI.isEmpty)
        newTail else ISup(newI) :: newTail
    case (i: IPlainText) :: tail => i :: postprocessingTextFragments(tail)
    case (i: IReference) :: tail => i :: postprocessingTextFragments(tail)
    case (i: ICitation) :: tail => i :: postprocessingTextFragments(tail)
    case (i: IFootnote) :: tail => i :: postprocessingTextFragments(tail)
    case IURL(link, inner) :: tail =>
      val newI = inner.map(postprocessingTextFragments)
      val newTail = postprocessingTextFragments(tail)
      if (newI.isDefined && newI.get.isEmpty)
        newTail else IURL(link, newI) :: newTail
    case Nil => Nil
  }) match {
    case IPlainText(c) :: IPlainText(c2) :: tail => IPlainText(c + c2) :: tail
    case e => e
  }

  private def isPaperpileRef(p: Paragraph): Boolean =
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



  private def getFootnotes(doc: Document): Map[String, List[IParagraph]] =
    if (doc.getFootnotes==null) Map() else
    (Map() ++ doc.getFootnotes.asScala).view.mapValues(f=>{
      val paragraphs = f.getContent.asScala.flatMap(se => Option(se.getParagraph)).toList
      paragraphs.flatMap(p=>convertTextParagraph(Context(Map(), Map()), p))
    }).toMap


  //detect light gray background as indexed keywords
  private def isIndex(e: ParagraphElement): Boolean =
    if (e.getTextRun != null && e.getTextRun.getTextStyle != null && e.getTextRun.getTextStyle.getBackgroundColor != null && e.getTextRun.getTextStyle.getBackgroundColor.getColor != null && e.getTextRun.getTextStyle.getBackgroundColor.getColor.getRgbColor != null) {
      val col = e.getTextRun.getTextStyle.getBackgroundColor.getColor.getRgbColor
      col.getRed > 0.69 && col.getRed < 0.91 && Math.abs(col.getRed - col.getBlue) < 0.1 && Math.abs(col.getRed - col.getGreen) < 0.1
    } else false


  private def findIndexTerms(p: Paragraph): List[String] = {
    var result: List[String] = Nil
    var els = p.getElements.asScala.toList
    var prefix = ""
    while (els.nonEmpty) {
      val el = els.head
      els = els.tail
      if (isIndex(el) && els.nonEmpty && isIndex(els.head))
        prefix = prefix + el.getTextRun.getContent
      else if (isIndex(el)) {
        result ::= prefix + el.getTextRun.getContent
        prefix = ""
      }
    }
    result
  }


}
