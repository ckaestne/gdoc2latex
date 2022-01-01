package edu.cmu.ckaestne.gdoc2latex.server

import cask.Response
import cask.model.StaticFile
import edu.cmu.ckaestne.gdoc2latex.{GDoc2LatexConverter, GDocConnection, Template}
import scalatags.Text.all._

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.io.Source


case class Routes() extends cask.MainRoutes {

  private def tt(s: String) = span(s, style := "font-family: Consolas,\"Courier New\",monospace")

  @cask.get("/")
  def usage() = {
    println("responding.")
    doctype("html")(
      html(
        body(
          h1("GDoc2Latex"),
          p("This project will convert a Google Docs document into Latex. " +
            "All documents are identified by an ID of 44 characters that can be found in the URL of the document. " +
            "This project can only access public documents or those that are shared with ", tt("heroku-server@gdoc2latex.iam.gserviceaccount.com"), "."),
          p("Optionally a second Google Docs document can be provided that provides the Latex main file in which ", tt("\\CONTENT"), " will be replaced by the body of the document, ", tt("\\TITLE"), " with the title, and ", tt("\\ABSTRACT"), " with the abstract."),
          p("The following end points are supported: ",
            ul(
              li(tt("/latex/$DOCID/$TEMPLATEID"), ": Shows the converted Latex document. If the document id for the template is not provided a default template will be used.")
            )
          )
        )
      )
    )
  }

  @cask.get("/latex/:gdocId")
  def latex(gdocId: String): doctype = {
    latex(gdocId, "")
  }

  @cask.get("/latex/:gdocId/:templateId")
  def latex(gdocId: String, templateId: String): doctype = {
    val result = getLatex(gdocId, templateId)
    doctype("html")(
      html(
        body(
          pre(result)
        )
      )
    )
  }

  val targetDirectory: Path = Path.of("out")
  if (!targetDirectory.toFile.exists())
    Files.createDirectory(targetDirectory)


  private def getLatex(gdocId: String, templateId: String): String = {
    println(s"loading gdoc $gdocId, template $templateId")
    val template =
      if (templateId == null || templateId.isEmpty)
        Template.defaultTemplate
      else Template.loadGdoc(templateId)


    val doc = GDocConnection.getDocument(gdocId)
    val ldoc = new GDoc2LatexConverter().convert(doc)
    template.render(ldoc)
  }


  @cask.get("/update/:gdocId")
  def update(gdocId: String) = {
    val latex = getLatex(gdocId, "")
    updatePDF(gdocId, latex)
    returnPDF(gdocId)
  }


  @cask.get("/pdf/:gdocId")
  def pdf(gdocId: String) = {
    returnPDF(gdocId)
  }


  @cask.get("/log/:gdocId")
  def log(gdocId: String) = {
    returnLog(gdocId)
  }

  def returnPDF(gdocId: String): Response[Response.Data] = {
    val pdf = targetDirectory.resolve(gdocId + ".pdf")
    val log = targetDirectory.resolve(gdocId + ".log")
    if (Files.exists(pdf))
      StaticFile(pdf.toString, Seq())
    else if (Files.exists(log))
      doctype("html")(
        html(
          body(
            h1("Latex failed"),
            h2("Log:"),
            pre(Files.readString(targetDirectory.resolve(gdocId + ".log")))
          )
        )
      )
    else
      doctype("html")(
        html(
          body(
            h1("No result")
          )
        )
      )
  }

  def returnLog(gdocId: String): Response[Response.Data] = {
    val log = targetDirectory.resolve(gdocId + ".log")
    if (Files.exists(log))
      doctype("html")(
        html(
          body(
            h1("Log:"),
            pre(Files.readString(targetDirectory.resolve(gdocId + ".log")))
          )
        )
      )
    else
      doctype("html")(
        html(
          body(
            h1("No log")
          )
        )
      )
  }

  private def updatePDF(gdocId: String, latex: String): Unit = {
    val workingDirectory = Files.createTempDirectory("gdoc2latex")
    val texFile = workingDirectory.resolve("main.tex")
    Files.writeString(texFile, latex)
    val process = new ProcessBuilder("latexmk", "--pdf", "--interaction=nonstopmode", "main.tex")
      .directory(workingDirectory.toFile).start()
    val out = process.getInputStream
    val err = process.getErrorStream
    val success = process.waitFor() == 0
    val log = Source.fromInputStream(out).getLines().mkString("\n")
    val errorLog = Source.fromInputStream(err).getLines().mkString("\n")

    if (success)
      Files.move(workingDirectory.resolve("main.pdf"), targetDirectory.resolve(gdocId + ".pdf"), StandardCopyOption.REPLACE_EXISTING)
    else
      Files.deleteIfExists(targetDirectory.resolve(gdocId + ".pdf"))
    Files.writeString(targetDirectory.resolve(gdocId + ".tex"), latex)
    Files.writeString(targetDirectory.resolve(gdocId + ".log"), log)
    Files.writeString(targetDirectory.resolve(gdocId + ".err"), errorLog)

    for (file <- workingDirectory.toFile.listFiles())
      file.delete()
    Files.delete(workingDirectory)
  }

  initialize()

}

object Server extends cask.Main {
  var _port: Int = 3000

  override def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      println("Missing argument for the port")
    if (args.size >= 1)
      _port = args(0).toInt

    println(s"Starting the server on port ${_port}")
    super.main(args)
  }

  val allRoutes = Seq(Routes())

  override def port = _port

  override def host: String = "0.0.0.0"
}