package edu.cmu.ckaestne.gdoc2latex

import com.google.api.services.docs.v1.model.{Document, StructuralElement}
import com.google.api.services.docs.v1.model.ParagraphElement
import com.google.api.services.docs.v1.model.TextRun

import scala.io.Source
import scala.jdk.CollectionConverters._

object Template {
  lazy val defaultTemplate =
    new Template(Source.fromInputStream(this.getClass.getResourceAsStream("/default_template.tex")).getLines().mkString("\n"))

  def loadGdoc(gdocId: String): Template = {
    val doc = GDocConnection.getDocument(gdocId)
    new Template(getPlainText(doc))
  }

  private def getPlainText(doc: Document): String = {
    val elements = doc.getBody.getContent
    val sb: StringBuilder = new StringBuilder
    for (element <- elements.asScala) {
      if (element.getParagraph() != null) {
        for (paragraphElement <- element.getParagraph.getElements.asScala) {
          sb.append(readParagraphElement(paragraphElement))
        }
      }
    }
    sb.toString
  }


  private def readParagraphElement(element: ParagraphElement): String = {
    val run = element.getTextRun
    if (run == null || run.getContent == null) { // The TextRun can be null if there is an inline object.
      return ""
    }
    run.getContent
  }
}

class Template(templateContent: String) {
  def render(doc: LatexDoc): String = {
    return templateContent.replace("\\TITLE", doc.title)
      .replace("\\ABSTRACT", doc.abstr)
      .replace("\\CONTENT", doc.latexBody)
  }
}
