package edu.cmu.ckaestne.gdoc2latex.cli

import edu.cmu.ckaestne.gdoc2latex.converter.{Context, GDoc2LatexConverter}
import edu.cmu.ckaestne.gdoc2latex.util.GDocConnection
import scopt.OParser

import java.io.File
import java.nio.file.Files

object GDoc2LatexCLI extends App {

  case class Config(
                     documentid: String = "",
                     out: Option[File] = None,
                     template: Option[File] = None
                   )


  val builder = OParser.builder[Config]
  val parser1 = {
    import builder._
    OParser.sequence(
      programName("gdoc2latex"),
      opt[File]('o', "out")
        .valueName("<file>")
        .action((x, c) => c.copy(out = Some(x)))
        .text("Target path for the generated latex file"),
      opt[File]('t', "template")
        .valueName("<file>")
        .validate(f => if (f.exists()) success else failure("Template file does not exist"))
        .action((x, c) => c.copy(template = Some(x)))
        .text("Latex template in which to replace \\TITLE, \\ABSTRACT, and \\CONTENT"),
      help("help").text("prints this usage text"),
      arg[String]("<documentid>")
        .action((x, c) => c.copy(documentid = x))
        .text("ID from a Google Doc document"),

    )
  }

  // OParser.parse returns Option[Config]
  OParser.parse(parser1, args, Config()) match {
    case Some(config) =>
      val doc = GDocConnection.getDocument(config.documentid)

      val context = config.template.map(Context.fromFile).getOrElse(Context.defaultContext)
      val ldoc = new GDoc2LatexConverter().convert(doc)

      val latex = context.render(ldoc)

      if (config.out.isDefined)
        Files.write(config.out.get.toPath, latex.mainFileContent)
      else
        println(latex.mainFileString)
    case _ =>
  }

}
