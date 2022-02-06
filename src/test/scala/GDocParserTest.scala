package edu.cmu.ckaestne.gdoc2latex.converter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should


class GDocParserTest extends AnyFlatSpec with should.Matchers {

  val p = new GDocParser()
  "trimFrontEnd helper" should "do nothing on word boundaries" in {
    assert(p.trimFrontEnd(List(IPlainText("foo"))) == ("", List(IPlainText("foo")), ""))
  }
  it should "trim whitespace on front" in {
    assert(p.trimFrontEnd(List(IPlainText(" foo"))) == (" ", List(IPlainText("foo")), ""))
  }
  it should "trim whitespace at the end" in {
    assert(p.trimFrontEnd(List(IPlainText("foo "))) == ("", List(IPlainText("foo")), " "))
  }

  "simplifyIFormattedText helper" should "concat plain texts" in {
    assert(p.simplifyIFormattedText(List(IPlainText("a"), IPlainText("b"))) ==
      List(IPlainText("ab")))
  }
  it should "concat remove empty bold" in {
    assert(p.simplifyIFormattedText(List(IPlainText("a"), IBold(List()), IPlainText("b"))) ==
      List(IPlainText("ab")))
  }
  it should "concat remove empty bold string" in {
    assert(p.simplifyIFormattedText(List(IPlainText("a"), IBold(List(IPlainText(""))), IPlainText("b"))) ==
      List(IPlainText("ab")))
  }
}
