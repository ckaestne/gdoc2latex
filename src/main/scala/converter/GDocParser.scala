package edu.cmu.ckaestne.gdoc2latex.converter

import com.google.api.services.docs.v1.model.{Document, Paragraph, ParagraphElement}

import scala.jdk.CollectionConverters._


class GDocParser {


  private def convertParagraph(p: Paragraph): Option[IParagraph] =
    if (p.getElements == null) None
    else Some(IParagraph(simplifyIFormattedText(convertParagraphText(p.getElements.asScala.toList))))


  private sealed trait SequenceFormatting {
    def hasFormatting(e: ParagraphElement): Boolean

    def continuesFormatting(from: ParagraphElement, to: ParagraphElement) = hasFormatting(from) && hasFormatting(to)

    def seqLength(seq: List[ParagraphElement]): Int = if (seq.isEmpty || !hasFormatting(seq.head)) 0 else {
      var result = 1
      val head = seq.head
      var tail = seq.tail
      while (tail.nonEmpty && continuesFormatting(head, tail.head)) {
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

  private object ItalicsFormatting extends SequenceFormatting {
    override def hasFormatting(e: ParagraphElement): Boolean =
      if (e.getTextRun != null && e.getTextRun.getTextStyle != null) e.getTextRun.getTextStyle.getItalic else false

    def getFormatter(e: ParagraphElement): List[IFormattedText] => List[IFormattedText] = applyAfterTrim(IItalics.apply)
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
        List(ICitation(refs.toList))
      } else {
        if (link == inner.map(_.getPlainText()).mkString)
          List(IURL(link, None))
        else List(IURL(link, Some(inner)))
      }
    }
  }

  private val sequenceFormattings = List(BoldFormatting, ItalicsFormatting, LinkFormatting)

  private def isComment(e: ParagraphElement): Boolean =
    if (e.getTextRun != null && e.getTextRun.getTextStyle != null) e.getTextRun.getTextStyle.getStrikethrough else false

  private def cleanText(str: String) = str.replace("\n", "")

  /**
   * text convertion: italics, bold, and links/references/citations are recognized and need to start/stop at word boundaries. strikethrough is ignored as comment at the character level.
   */

  private def convertParagraphText(paragraphElements: List[ParagraphElement], outerFormatting: Set[SequenceFormatting] = Set()): List[IFormattedText] =
    if (paragraphElements.isEmpty) Nil
    else if (isComment(paragraphElements.head)) convertParagraphText(paragraphElements.tail)
    else {
      // if bold or italics, grab the longest subsequence with either formatting and process that recursively
      val longestSeqFormatting = sequenceFormattings.filterNot(outerFormatting.contains).filter(_.seqLength(paragraphElements) > 0).maxByOption(_.seqLength(paragraphElements))
      if (longestSeqFormatting.isDefined) {
        val (formattedHeads, tail) = longestSeqFormatting.get.split(paragraphElements)
        val formatter = longestSeqFormatting.get.getFormatter(paragraphElements.head)
        formatter(convertParagraphText(formattedHeads, outerFormatting + longestSeqFormatting.get)) ++
          convertParagraphText(tail, outerFormatting)
      } else {
        // process plain text element at head by processing tail and adding plain text to the front (avoid sequence of two plaintext elements by merging them instead)
        if (paragraphElements.head.getTextRun == null) convertParagraphText(paragraphElements.tail)
        else {
          val newText = cleanText(paragraphElements.head.getTextRun.getContent)
          convertParagraphText(paragraphElements.tail, outerFormatting) match {
            case IPlainText(t) :: tail => IPlainText(newText + t) :: tail
            case e => IPlainText(newText) :: e
          }
        }
      }
    }


  private[converter] def simplifyIFormattedText(l: List[IFormattedText]): List[IFormattedText] = (l match {
    case IPlainText(c) :: tail if c.isEmpty => simplifyIFormattedText(tail)
    case IBold(i) :: tail =>
      val newI = simplifyIFormattedText(i)
      val newTail = simplifyIFormattedText(tail)
      if (newI.isEmpty)
        newTail else IBold(newI) :: newTail
    case IItalics(i) :: tail =>
      val newI = simplifyIFormattedText(i)
      val newTail = simplifyIFormattedText(tail)
      if (newI.isEmpty)
        newTail else IItalics(newI) :: newTail
    case (i: IPlainText) :: tail => i :: simplifyIFormattedText(tail)
    case (i: IReference) :: tail => i :: simplifyIFormattedText(tail)
    case (i: ICitation) :: tail => i :: simplifyIFormattedText(tail)
    case IURL(link, inner) :: tail =>
      val newI = inner.map(simplifyIFormattedText)
      val newTail = simplifyIFormattedText(tail)
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


  def convert(doc: Document): IDocument = {
    val body = doc.getBody
    var title: IParagraph = IParagraph(List(IPlainText(Option(doc.getTitle).getOrElse(""))))
    var abstr: Option[List[IParagraph]] = None

    val paragraphs = body.getContent.asScala.flatMap(se => Option(se.getParagraph)).toList

    val (bibliographyParagraphs, mainParagraphs) = paragraphs.partition(isPaperpileRef)

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

    for (paragraph <- mainParagraphs.reverse; p <- convertParagraph(paragraph); if p.trimNonEmpty) {
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
      } else
        System.err.println("unknown style: " + namedStyle)
    }

    val bibitems: List[(String, IParagraph)] =
      for (bibitem <- bibliographyParagraphs;
           p <- convertParagraph(bibitem); if p.trimNonEmpty;
           f = bibitem.getElements.asScala.toList.filter(hasPaperpileRefLink); if f.nonEmpty) yield {
        val id = getPaperpileRefLink(f.head).get
        val text = IParagraph(simplifyIFormattedText(convertParagraphText(f, Set())))
        (id, text)
      }
    if (bibitems.nonEmpty)
      mainContent :+= IBibliography(bibitems)


    IDocument(title, abstr, mainContent)
  }


}
