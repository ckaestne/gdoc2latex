package edu.cmu.ckaestne.gdoc2latex.converter

import edu.cmu.ckaestne.gdoc2latex.converter._
import edu.cmu.ckaestne.gdoc2latex.util.GDocConnection
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should


class CoversionTest extends AnyFlatSpec with should.Matchers {

  private val testDocumentId1 = "1Axg8EbLN-dW-j5PXACfg48RFYL59DOatQ7bvKG3Cfag" // https://docs.google.com/document/d/1Axg8EbLN-dW-j5PXACfg48RFYL59DOatQ7bvKG3Cfag/edit
  private val testDocumentId2 = "1XU1nq_xb3ZYA4j2H0QqRC1t7w2YMaIWcYkIfVM0gakM" // https://docs.google.com/document/d/1XU1nq_xb3ZYA4j2H0QqRC1t7w2YMaIWcYkIfVM0gakM/edit#

  private val gdoc1 = GDocConnection.getDocument(testDocumentId1)
  private val parsed1 = new GDocParser().convert(gdoc1)
  private val gdoc2 = GDocConnection.getDocument(testDocumentId2)
  private val parsed2 = new GDocParser().convert(gdoc2)

  private def plainParagraph(s: String) = IParagraph(List(IPlainText(s)))

  "Test GDocs" should "successfully be downloaded" in {
    assert(gdoc1!=null)
    assert(gdoc2!=null)
  }

  "Title" should "be recognized" in {
    assert(parsed1.title==plainParagraph("TitleA"))
  }
  it should "default to document title" in {
    assert(parsed2.title==plainParagraph("Gdoc2latex-Test2"))
  }

  "Abstract" should "be recognized" ignore {
    assert(parsed1.abstr == Some(List(plainParagraph("This is the abstract."), plainParagraph("This is the second paragraph of the abstract."))))
  }
  it should "be none if none provided" in {
    assert(parsed2.abstr.isEmpty)
  }

  private def checkHeading(e: IDocumentElement, lvl: Int, content: IParagraph) : Boolean = if (e.isInstanceOf[IHeading]) {
    val h = e.asInstanceOf[IHeading]
    h.level==lvl && h.text==content
  } else false
  private def checkHeading(e: IDocumentElement, lvl: Int, content: String) : Boolean = if (e.isInstanceOf[IHeading]) {
    val h = e.asInstanceOf[IHeading]
    h.level==lvl && h.text.plainText==content
  } else false

  "Headings" should "be recognized" in {
    val firstHeading = parsed1.content.find(_.isInstanceOf[IHeading])
    assert(firstHeading.isDefined)
    assert(checkHeading(firstHeading.get, 1, plainParagraph("Heading 1A")))
  }

  it should "be recognized for 3 levels" in {
    val first4Headings = parsed1.content.filter(_.isInstanceOf[IHeading]).take(4)
    assert(first4Headings.length==4)
    assert(checkHeading(first4Headings(0), 1, plainParagraph("Heading 1A")))
    assert(checkHeading(first4Headings(1), 2, plainParagraph("Heading 2A")))
    assert(checkHeading(first4Headings(2), 3, plainParagraph("Heading 3A")))
    assert(checkHeading(first4Headings(3), 1, plainParagraph("Heading 1B")))
  }

  private def getElementsInSection(doc: IDocument, lvl: Int, sectionTitle: String): List[IDocumentElement] = {
    val skipToHeading = doc.content.dropWhile(!checkHeading(_, lvl, sectionTitle)).drop(1)
    skipToHeading.takeWhile(!_.isInstanceOf[IHeading])
  }

  "Paragraphs" should "be separated by line breaks" in {
    val paragraphs = getElementsInSection(parsed1,1, "Paragraph Separation")
    assert(paragraphs==List(plainParagraph("Text1x"), plainParagraph("Text2x")))
  }

  it should "not be included if empty" in {
    val paragraphs = getElementsInSection(parsed1,1, "Paragraph Empty")
    assert(paragraphs==List())
  }

  "Text formatting" should "recognize bold" in {
    val paragraphs = getElementsInSection(parsed1,1, "Paragraph Style")
    assert(paragraphs(0) == IParagraph(List(IPlainText("This is "), IBold(List(IPlainText("bold"))), IPlainText("."))))
  }
  it should "recognize italics" in {
    val paragraphs = getElementsInSection(parsed1,1, "Paragraph Style")
    assert(paragraphs(1) == IParagraph(List(IPlainText("This is "), IItalics(List(IPlainText("italics"))), IPlainText("."))))
  }
  it should "ignore crossed out text" in {
    val paragraphs = getElementsInSection(parsed1,1, "Paragraph Style")
    assert(paragraphs(2) == IParagraph(List(IPlainText("This is ."))))
  }
  it should "recognize formatted sequences" in {
    val paragraphs = getElementsInSection(parsed1,1, "Paragraph Style")
    assert(paragraphs(3) == IParagraph(List(IPlainText("This is "), IBold(List(IPlainText("a bold sequence"))), IPlainText("."))))
  }
  it should "recognize nested formatting" in {
    val paragraphs = getElementsInSection(parsed1,1, "Paragraph Style")
    assert(paragraphs(4) == IParagraph(List(IPlainText("This "), IBold(List(IPlainText("is "), IItalics(List(IPlainText("italics"))), IPlainText(" in bold"))), IPlainText("."))))
  }
  it should "recognize nested formatting 2" in {
    val paragraphs = getElementsInSection(parsed1,1, "Paragraph Style")
    assert(paragraphs(5) == IParagraph(List(IPlainText("This "), IItalics(List(IPlainText("is "), IBold(List(IPlainText("bold"))), IPlainText(" in italics"))), IPlainText("."))))
  }
  it should "ignore formatting within words" ignore {
    val paragraphs = getElementsInSection(parsed1,1, "Formatting Issues")
    assert(paragraphs(0) == plainParagraph("This is invalid bold."))
  }
  it should "ignore formatting not at word boundaries" ignore {
    val paragraphs = getElementsInSection(parsed1,1, "Formatting Issues")
    assert(paragraphs(1) == plainParagraph("This is invalid italics."))
  }
  it should "accept crossed out within words" in {
    val paragraphs = getElementsInSection(parsed1,1, "Formatting Issues")
    assert(paragraphs(2) == plainParagraph("This is fe."))
  }
  it should "ignore formatting of whitespace only" in {
    val paragraphs = getElementsInSection(parsed1,1, "Formatting Issues")
    assert(paragraphs(3) == plainParagraph("This is bold whitespace."))
  }

  it should "move formatting around words" in {
    val paragraphs = getElementsInSection(parsed1,1, "Formatting at Word Boundaries")
    assert(paragraphs(0) == IParagraph(List(IPlainText("This is a "), IBold(List(IPlainText("bold"))), IPlainText(" word."))))
  }
  it should "move formatting around words2" in {
    val paragraphs = getElementsInSection(parsed1,1, "Formatting at Word Boundaries")
    assert(paragraphs(1) == IParagraph(List(IPlainText("This is a "), IBold(List(IPlainText("bold"))), IPlainText(" word."))))
  }
  it should "move formatting around words3" in {
    val paragraphs = getElementsInSection(parsed1,1, "Formatting at Word Boundaries")
    assert(paragraphs(2) == IParagraph(List(IPlainText("This is a "), IBold(List(IPlainText("bold"))), IPlainText(" word."))))
  }
  it should "include punctuation in formatting" in {
    val paragraphs = getElementsInSection(parsed1,1, "Formatting at Word Boundaries")
    assert(paragraphs(3) == IParagraph(List(IPlainText("This is "), IBold(List(IPlainText("bold."))))))
  }
  //

}
