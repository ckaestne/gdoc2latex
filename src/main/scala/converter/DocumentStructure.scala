package edu.cmu.ckaestne.gdoc2latex.converter


/**
 * Internal document structure to separate concerns.
 */


/**
 * text (equivalent to span tags in html)
 *
 * Formatted text should not end with a line break. Line breaks are controlled only by paragraphs
 */
sealed trait IFormattedText {
  def trimNonEmpty: Boolean = getPlainText().trim.nonEmpty

  def getPlainText(): String
}

case class IPlainText(text: String) extends IFormattedText {
  def getPlainText() = text
}

sealed trait ISimpleFormattedSequence extends IFormattedText {
  def elements: List[IFormattedText]
  private val plain = elements.map(_.getPlainText()).mkString
  if (plain.nonEmpty && plain.head == ' ')
    System.err.println(s"Sequence starts with whitespace: \"$plain\"")
  if (plain.nonEmpty && plain.last == ' ')
    System.err.println(s"Sequence ends with whitespace: \"$plain\"")

  def getPlainText() = plain
}

/**
 * bold/italic sequences should always start with a letter, not whitespace or punctuation
 */
case class IBold(elements: List[IFormattedText]) extends ISimpleFormattedSequence

case class IItalics(elements: List[IFormattedText]) extends ISimpleFormattedSequence

case class IUnderlined(elements: List[IFormattedText]) extends ISimpleFormattedSequence

case class IHighlight(elements: List[IFormattedText], color: String) extends ISimpleFormattedSequence

case class ISub(elements: List[IFormattedText]) extends ISimpleFormattedSequence
case class ISup(elements: List[IFormattedText]) extends ISimpleFormattedSequence

case class IReference(anchor: String) extends IFormattedText {
  def getPlainText() = "$REF"
}

case class ICitation(citations: List[String], text: List[IFormattedText], prefix: Option[String]=None) extends IFormattedText {
  def getPlainText() = text.map(_.getPlainText()).mkString
}

case class IURL(link: String, inner: Option[List[IFormattedText]]) extends IFormattedText {
  def getPlainText() = inner.map(_.map(_.getPlainText()).mkString).getOrElse(link)
}

case class IFootnote(elements: List[IParagraph]) extends IFormattedText {
  def getPlainText() = "Footnote: "+elements.map(_.plainText).mkString
}


/**
 * heading or paragraph or similar toplevel structure
 */
sealed trait IDocumentElement

/**
 * a paragraph is a piece of formatted text without line breaks -- could describe a paragraph, a heading, or any other sequence of formatted text
 */
case class IParagraph(content: List[IFormattedText], indexTerms: List[String]=Nil) extends IDocumentElement {
  def trimNonEmpty: Boolean = plainText.trim.nonEmpty

  def plainText = content.map(_.getPlainText()).mkString
}

case class IBulletList(bullets: List[IParagraph]) extends IDocumentElement

case class IBibliography(items: List[(String /*key*/ , String /*itemNrText*/, IParagraph)]) extends IDocumentElement

case class IHeading(level: Int, id: Option[String], text: IParagraph) extends IDocumentElement

case class IImage(id: String, contentUri: String, caption: Option[IParagraph], altText: Option[String], widthPt: Int) extends IDocumentElement

case class ICode(language: Option[String], code: String, caption: Option[IParagraph]) extends IDocumentElement

case class IDocument(title: IParagraph, abstr: Option[List[IParagraph]], content: List[IDocumentElement])

