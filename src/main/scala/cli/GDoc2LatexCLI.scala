package edu.cmu.ckaestne.gdoc2latex.cli

import edu.cmu.ckaestne.gdoc2latex.converter.{GDocParser, IDocument, LatexContext, LatexRenderer, MarkdownRenderer}
import edu.cmu.ckaestne.gdoc2latex.util.GDocConnection
import scopt.OParser

import java.io.File
import java.nio.file.Files

object GDoc2LatexCLI extends App {

  case class Config(
                     documentid: String = "",
                     out: Option[File] = None,
                     template: Option[File] = None,
                     withSuggestions: Boolean = false,
                     withImages: Boolean = false,
                     markdown: Boolean = false
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
      opt[Unit]("with-suggestions").action((x,c)=>c.copy(withSuggestions = true)).text("Convert document with all suggestions accepted (default: false)"),
      opt[Unit]("with-images").action((x,c)=>c.copy(withImages = true)).text("Download images and place them in the same directory as the --out file (default: false)"),
      opt[Unit]("md").action((x,c)=>c.copy(markdown = true)).text("Convert document to Markdown instead of Latex (default: false)"),
      help("help").text("prints this usage text"),
      arg[String]("<documentid>")
        .action((x, c) => c.copy(documentid = x))
        .text("ID from a Google Doc document"),

    )
  }

  // OParser.parse returns Option[Config]
  OParser.parse(parser1, args, Config()) match {
    case Some(config) =>
      val doc = GDocConnection.getDocument(config.documentid, config.withSuggestions)
      val ldoc = new GDocParser().convert(doc)

      if (config.markdown)
        doMarkdown(config, ldoc)
      else doLatex(config, ldoc)
    case _ =>
  }

  private def doLatex(config: Config, ldoc: IDocument): Unit = {
    val context = config.template.map(LatexContext.fromFile).getOrElse(LatexContext.defaultContext)
    val latex = context.render(new LatexRenderer(ignoreImages = !config.withImages, downloadImages = config.withImages).render(ldoc))

    if (config.out.isDefined) {
      val outFilePath = config.out.get.toPath
      Files.write(outFilePath, latex.mainFileContent)
      if (config.withImages) {
        val imgDir = outFilePath.getParent
        for ((name, content) <- latex.files) {
          val file = imgDir.resolve(name)
          Files.write(file, content)
        }
      }
    } else
      println(latex.mainFileString)
  }


  private def doMarkdown(config: Config, ldoc: IDocument): Unit = {
    val md = new MarkdownRenderer().render(ldoc)

    if (config.out.isDefined) {
      val outFilePath = config.out.get.toPath
      Files.write(outFilePath, md.getBytes)
    } else
      println(md)
  }
}
