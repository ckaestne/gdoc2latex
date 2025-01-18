package edu.cmu.ckaestne.gdoc2latex.converter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class PaperpileTest extends AnyFlatSpec with should.Matchers {

  //https://paperpile.com/c/ZwuhRx/9v5Wx

  val text1 = List(IPlainText("[1]"))
  val text2 = List(IPlainText("[1, 2]"))


  "Paperpile parser" should "handle simple cases" in {
    val citations = new PaperpileConverter().convertLink("https://paperpile.com/c/ZwuhRx/9v5Wx",text1)

    citations should be(List(ICitation(List("9v5Wx"), text1)))
  }

  // https://paperpile.com/c/ZwuhRx/9iyEm+yUaq4+2z8DC
  "Paperpile parser" should "handle multiple citations" in {
    val citations = new PaperpileConverter().convertLink(
      "https://paperpile.com/c/ZwuhRx/9iyEm+yUaq4+2z8DC",text2)

    citations should be(List(ICitation(List("9iyEm","yUaq4","2z8DC"), text2)))
  }

  "Paperpile parser" should "handle citation with page number" in {
    val citations = new PaperpileConverter().convertLink(
      "https://paperpile.com/c/ZwuhRx/zVvHb/?locator=84",text2)

    citations should be(List(ICitation(List("zVvHb"), text2, Some("p.~84"))))
  }
  "Paperpile parser" should "handle citation with page range" in {
    val citations = new PaperpileConverter().convertLink(
      "https://paperpile.com/c/ZwuhRx/zVvHb/?locator=84--86",text2)

    citations should be(List(ICitation(List("zVvHb"), text2, Some("pp.~84--86"))))
  }
  "Paperpile parser" should "handle citation with page list" in {
    val citations = new PaperpileConverter().convertLink(
      "https://paperpile.com/c/ZwuhRx/zVvHb/?locator=84%2C86",text2)

    citations should be(List(ICitation(List("zVvHb"), text2, Some("pp.~84,86"))))
  }

  "Paperpile parser" should "handle citation with chapter number" in {
    val citations = new PaperpileConverter().convertLink(
      "https://paperpile.com/c/ZwuhRx/zVvHb/?locator_label=chapter&locator=84",text2)

    citations should be(List(ICitation(List("zVvHb"), text2, Some("ch.~84"))))
  }

  "Paperpile parser" should "handle citation with other number" in {
    val citations = new PaperpileConverter().convertLink(
      "https://paperpile.com/c/ZwuhRx/zVvHb/?locator_label=section&locator=84",text2)

    citations should be(List(ICitation(List("zVvHb"), text2, Some("section~84"))))
  }
  "Paperpile parser" should "reject multiple citations with page numbers" in {
    val citations = new PaperpileConverter().convertLink(
      "https://paperpile.com/c/ZwuhRx/zVvHb+V1hyG/?locator=84--86,",text2)

    assert(citations.head.isInstanceOf[ICitation])
    assert(citations.head.asInstanceOf[ICitation].citations.size==1)
    citations.head.asInstanceOf[ICitation].citations.head should startWith("\\error")
  }
  "Paperpile parser" should "reject multiple citations with page numbers each" in {
    val citations = new PaperpileConverter().convertLink(
      "https://paperpile.com/c/ZwuhRx/zVvHb+V1hyG/?locator=84--86,3&locator_label=page,chapter",text2)

    assert(citations.head.isInstanceOf[ICitation])
    assert(citations.head.asInstanceOf[ICitation].citations.size==1)
    citations.head.asInstanceOf[ICitation].citations.head should startWith("\\error")
  }


  "Paperpile parser" should "reject unsupported prefixes and postfixes" in {
    val citations = new PaperpileConverter().convertLink(
      "https://paperpile.com/c/ZwuhRx/zVvHb/?prefix=a&suffix=b",text2)

    assert(citations.head.isInstanceOf[ICitation])
    assert(citations.head.asInstanceOf[ICitation].citations.size==1)
    citations.head.asInstanceOf[ICitation].citations.head should startWith("\\error")
  }

  "Paperpile parser" should "reject unsupported surpress author format" in {
    val citations = new PaperpileConverter().convertLink(
      "https://paperpile.com/c/ZwuhRx/zVvHb/?noauthor=1",text2)

    assert(citations.head.isInstanceOf[ICitation])
    assert(citations.head.asInstanceOf[ICitation].citations.size==1)
    citations.head.asInstanceOf[ICitation].citations.head should startWith("\\error")
  }



  // https://paperpile.com/c/ZwuhRx/9iyEm+yUaq4+2z8DC/?noauthor=1,0,0&prefix=,1,&suffix=,2,&locator_label=page,page,chapter&locator=,,2

}
