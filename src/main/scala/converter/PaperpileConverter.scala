package edu.cmu.ckaestne.gdoc2latex.converter

import org.apache.http.client.utils.URLEncodedUtils

import java.net.URI
import java.nio.charset.Charset
import scala.jdk.CollectionConverters.CollectionHasAsScala


class PaperpileConverter {

  def convertLink(link: String, inner: List[IFormattedText]): List[IFormattedText] = {
    assert(link.startsWith("https://paperpile.com/c/"))

    val uri = new URI(link)
    val path = uri.getPath
    assert(Set(3,4) contains path.count(_ == '/'))
    val citations = path.drop(3).split("/").drop(1).head.split("\\+").toList

    val params = URLEncodedUtils.parse(uri, Charset.forName("UTF-8")).asScala

    if (citations.size!=1) {
      if (params.nonEmpty)
      return List(ICitation(
          List("\\error{Modifiers for citations only supported for single citation}"),
          inner, None))
      else return List(ICitation(citations, inner))
    } else {
      //single citation
      val pages = params.find(_.getName == "locator").map(_.getValue) match {
        case Some(locator) => locator
        case None => ""
      }
      val isPageNr = pages.matches("\\d+")
      var prefix = if (pages.isEmpty) None else
        params.find(_.getName == "locator_label").map(_.getValue) match {
        case Some("chapter") => Some("ch.~"+pages)
        case Some(x) if x!="page" => Some(x+"~"+pages)
        case None => Some((if (isPageNr)"p.~" else "pp.~")+pages)
      }
      prefix = params.find(_.getName == "suffix").map(_.getValue) match {
          case Some(p) => Some(prefix.map(_+" ").getOrElse("")+p)
          case None => prefix
      }
      if (params.count(_.getName == "prefix")>0)
        return List(ICitation(          List("\\error{Prefix for citations not supported}"),          inner, None))
      if (params.count(_.getName == "noauthor")>0)
        return List(ICitation(          List("\\error{Noauthor citations not supported}"),          inner, None))
      return List(ICitation(citations, inner, prefix))
    }

  }

}
