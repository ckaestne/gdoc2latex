package edu.cmu.ckaestne.gdoc2latex.converter

object Util {
  def textToId(s: String): String = s.toLowerCase().replaceAll("\\W", "-").replace("--", "-")

}
