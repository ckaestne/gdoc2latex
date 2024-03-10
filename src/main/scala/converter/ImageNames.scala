package edu.cmu.ckaestne.gdoc2latex.converter

import java.io.File

object ImageNames {
  val filename = ".imagenames"
  val names: Map[String, String] =
    if (new File(filename).exists())
      scala.io.Source.fromFile(filename).getLines().map(_.split(",")).filter(_.length == 2).map(x => (x(0) -> x(1))).toMap
    else Map()

  def getName(fileId: String): String =
    names.getOrElse(fileId, fileId)
}