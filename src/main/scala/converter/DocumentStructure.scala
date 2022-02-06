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

/**
 * bold/italic sequences should always start with a letter, not whitespace or punctuation
 */
case class IBold(elements: List[IFormattedText]) extends IFormattedText {
  private val plain = elements.map(_.getPlainText()).mkString
  if (plain.nonEmpty && ! "\\w".r.matches(plain.take(1)))
    System.err.println(s"Bold sequence starts with nonword character: \"$plain\"")
  if (plain.nonEmpty && plain.last == ' ')
    System.err.println(s"Bold sequence ends with whitespace: \"$plain\"")

  def getPlainText() = plain
}

case class IItalics(elements: List[IFormattedText]) extends IFormattedText {
  private val plain = elements.map(_.getPlainText()).mkString
  if (plain.nonEmpty && ! "\\w".r.matches(plain.take(1)))
    System.err.println(s"Italics sequence starts with nonword character: \"$plain\"")
  if (plain.nonEmpty && plain.last == ' ')
    System.err.println(s"Italics sequence ends with whitespace: \"$plain\"")
  def getPlainText() = plain
}

case class IReference(anchor: String) extends IFormattedText {
  def getPlainText() = "$REF"
}

case class ICitation(citations: List[String]) extends IFormattedText {
  def getPlainText() = "$CITE"
}

case class IURL(link: String, inner: Option[List[IFormattedText]]) extends IFormattedText {
  def getPlainText() = inner.map(_.map(_.getPlainText()).mkString).getOrElse(link)
}


/**
 * heading or paragraph or similar toplevel structure
 */
sealed trait IDocumentElement

/**
 * a paragraph is a piece of formatted text without line breaks -- could describe a paragraph, a heading, or any other sequence of formatted text
 */
case class IParagraph(content: List[IFormattedText]) extends IDocumentElement {
  def trimNonEmpty: Boolean = plainText.trim.nonEmpty

  def plainText = content.map(_.getPlainText()).mkString
}

case class IBulletList(bullets: List[IParagraph]) extends IDocumentElement

case class IBibliography(items: List[(String /*key*/ , IParagraph)]) extends IDocumentElement

case class IHeading(level: Int, id: Option[String], text: IParagraph) extends IDocumentElement

case class IDocument(title: IParagraph, abstr: Option[List[IParagraph]], content: List[IDocumentElement])