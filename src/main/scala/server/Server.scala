package edu.cmu.ckaestne.gdoc2latex.server

import cask.Response
import cask.model.StaticFile
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import edu.cmu.ckaestne.gdoc2latex.util.GDocConnection
import edu.cmu.ckaestne.gdoc2latex.{Context, GDoc2LatexConverter, LatexInput}
import scalatags.Text.all._

import java.io.{BufferedReader, File, InputStreamReader}
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.stream.Collectors

case class GDocId(docId: String, templateId: Option[String]) {
  def toParam = docId + templateId.map("/" + _).getOrElse("")
}

object GDocId {
  def from(gdocId: String, templateId: String) =
    GDocId(gdocId, if (templateId != null && templateId != "") Some(templateId) else None)
}

object GDoc2LatexWorker {
  val targetDirectory: Path = Path.of("out")
  if (!targetDirectory.toFile.exists())
    Files.createDirectory(targetDirectory)

  def rawPath(gdocId: GDocId) = targetDirectory.resolve(gdocId.docId + ".json")

  def pdfPath(gdocId: GDocId) = targetDirectory.resolve(gdocId.docId + ".pdf")

  def texPath(gdocId: GDocId) = targetDirectory.resolve(gdocId.docId + ".tex")

  def logPath(gdocId: GDocId) = targetDirectory.resolve(gdocId.docId + ".log")

  def errPath(gdocId: GDocId) = targetDirectory.resolve(gdocId.docId + ".err")


  def getLatex(gdocId: GDocId): LatexInput = {
    println(s"loading gdoc $gdocId")
    val context = gdocId.templateId.map(Context.fromGoogleId).getOrElse(Context.defaultContext)

    val doc = GDocConnection.getDocument(gdocId.docId)

    //keep raw json output from gdoc for debugging
    gdocId.docId.synchronized {
      Files.writeString(rawPath(gdocId), doc.toString)
    }

    val ldoc = new GDoc2LatexConverter().convert(doc)
    context.render(ldoc)
  }

  /**
   * return the last compiled pdf if any
   */
  def getLastPDF(gdocId: GDocId): Option[File] = {
    val path = pdfPath(gdocId)
    if (path.toFile.exists())
      Some(path.toFile)
    else None
  }

  /**
   * return the last latex log, if any
   */
  def getLastLog(gdocId: GDocId): Option[File] = {
    val path = logPath(gdocId)
    if (path.toFile.exists())
      Some(path.toFile)
    else None
  }

  /**
   * get the latest document version and compile a PDF if anything has changed
   */
  def updatePDF(gdocId: GDocId): Option[File /*PDF*/ ] = {
    val input = getLatex(gdocId)

    gdocId.docId.synchronized {
      if (texPath(gdocId).toFile.exists()) {
        val oldTex = Files.readAllBytes(texPath(gdocId))
        if (oldTex == input.mainFileContent) {
          println("file has not changed")
          return getLastPDF(gdocId)
        }
      }
    }

    updatePDFInternal(gdocId, input)
  }


  /**
   * compile latex in a temporary directory and copy resulting files
   *
   * @param gdocId
   * @param latex
   * @return
   */
  private def updatePDFInternal(gdocId: GDocId, input: LatexInput): Option[File /*PDF*/ ] = {
    val workingDirectory = Files.createTempDirectory("gdoc2latex")
    for ((name, content) <- input.files) {
      val file = workingDirectory.resolve(name)
      Files.write(file, content)
    }
    val cmd = Seq("timeout", "60s", "latexmk", "--pdf", "--interaction=nonstopmode", "main.tex")
    val process = new ProcessBuilder(cmd: _*)
      .directory(workingDirectory.toFile).start()
    val out = process.getInputStream
    val err = process.getErrorStream
    val success = process.waitFor() == 0
    val log = new BufferedReader(new InputStreamReader(out)).lines().collect(Collectors.joining("\n"))
    val errorLog = new BufferedReader(new InputStreamReader(err)).lines().collect(Collectors.joining("\n"))

    gdocId.docId.synchronized {
      if (success)
        Files.move(workingDirectory.resolve("main.pdf"), pdfPath(gdocId), StandardCopyOption.REPLACE_EXISTING)
      else
        Files.deleteIfExists(pdfPath(gdocId))
      Files.write(texPath(gdocId), input.mainFileContent)
      Files.writeString(logPath(gdocId), "> " + cmd.mkString(" ") + "\n\n" + log)
      Files.writeString(errPath(gdocId), errorLog)
    }

    for (file <- workingDirectory.toFile.listFiles())
      file.delete()
    Files.delete(workingDirectory)
    if (success) Some(pdfPath(gdocId).toFile) else None
  }

  def clean(gdocId: GDocId) = {
    for (file <- List(
      rawPath(gdocId),
      pdfPath(gdocId),
      texPath(gdocId),
      logPath(gdocId),
      errPath(gdocId)
    ))
      if (Files.exists(file))
        Files.delete(file)
  }

}


case class Routes() extends cask.MainRoutes {

  type Resp = Response[Response.Data]
  val SERVICE_ACCOUNT_EMAIL = GDocConnection.serviceAccountCredentials.getClientEmail

  private def tt(s: String) = span(s, style := "font-family: Consolas,\"Courier New\",monospace")

  private def it(s: String) = span(s, style := "font-style: italic")

  private def printUsage() =
    htmlResp("GDoc2Latex",
      h1("GDoc2Latex"),
      p("This project will convert a Google Docs document into Latex. " +
        "All documents are identified by an ID of 44 characters that can be found in the URL of the document. " +
        "This project can only access public documents or those that are shared with ", tt(SERVICE_ACCOUNT_EMAIL), "."),
      p("Optionally a second Google Docs document can be provided that provides the Latex main file in which ", tt("\\CONTENT"), " will be replaced by the body of the document, ", tt("\\TITLE"), " with the title, and ", tt("\\ABSTRACT"), " with the abstract."),
      p("The following end points are supported: ",
        ul(
          li(tt("/latex/$DOCID/$TEMPLATEID"), ": Shows the converted Latex document. If the document id for the template is not provided a default template will be used.")
        )
      )
    )

  def handleErrors(f: () => Resp): Resp = {
    try {
      return f()
    } catch {
      case e: GoogleJsonResponseException =>
        val code = e.getDetails.getCode
        val message = e.getDetails.getMessage
        htmlResp("Error",
          h1("Cannot read requested document"),
          p("Error message: ", it(message), s", error code $code"),
          p("Try making the document public or share it with ", tt(SERVICE_ACCOUNT_EMAIL)))
      case e: Exception =>
        e.printStackTrace()
        Response("Failed " + e.getMessage, 400)
    }
  }

  @cask.get("/")
  def usage(): Resp =
    printUsage()

  @cask.get("/latex/:gdocId")
  def latex(gdocId: String): Resp = {
    latex(gdocId, "")
  }

  @cask.get("/latex/:gdocId/:templateId")
  def latex(gdocId: String, templateId: String): Resp = handleErrors(() => {
    val id = GDocId.from(gdocId, templateId)
    val input = GDoc2LatexWorker.getLatex(id)
    val t = "Latex for \"" + input.title + "\""
    htmlResp(t,
      h1(t),
      pre(input.mainFileString)
    )
  })

  private def htmlResp(title: String, bodycontent: Tag*): Resp =
    Response(doctype("html")(
      html(
        head(tag("title")(title), meta(charset := "UTF-8")),
        body(bodycontent: _*)
      )
    ))

  @cask.get("/update/:gdocId")
  def update(gdocId: String): Resp = update(gdocId, "")

  @cask.get("/update/:gdocId/:templateId")
  def update(gdocId: String, templateId: String): Resp = handleErrors(() => {
    val id = GDocId.from(gdocId, templateId)
    val pdf = GDoc2LatexWorker.updatePDF(id)
    respondWithPDF(id, pdf)
  })

  @cask.get("/clean/:gdocId")
  def clean(gdocId: String): Resp = clean(gdocId, "")

  @cask.get("/clean/:gdocId/:templateId")
  def clean(gdocId: String, templateId: String): Resp = handleErrors(() => {
    val id = GDocId.from(gdocId, templateId)
    GDoc2LatexWorker.clean(id)
    htmlResp("Cleaned",
      h1(s"Cleaned all files for $gdocId"),
      p(a("build again", href := "/update/" + id.toParam))
    )
  })


  def respondWithPDF(gdocId: GDocId, pdf: Option[File]): Resp = {
    if (pdf.isDefined)
      StaticFile(pdf.get.getAbsolutePath, Seq())
    else {
      htmlResp("No PDF",
        h1("No PDF produced"),
        p("For details, see ",
          a("last latex log", href := "/log/" + gdocId.toParam),
          " or ",
          a("generated latex", href := "/latex/" + gdocId.toParam),
          ". After changes, try to ",
          a("build again", href := "/update/" + gdocId.toParam),
          ".")
      )
    }

  }

  @cask.get("/pdf/:gdocId")
  def pdf(gdocId: String): Resp = pdf(gdocId, "")

  @cask.get("/pdf/:gdocId/:templateId")
  def pdf(gdocId: String, templateId: String): Resp = {
    val id = GDocId.from(gdocId, templateId)
    val pdf = GDoc2LatexWorker.getLastPDF(id)
    respondWithPDF(id, pdf)
  }

  @cask.get("/log/:gdocId")
  def log(gdocId: String): Resp = log(gdocId, "")

  @cask.get("/log/:gdocId/:templateId")
  def log(gdocId: String, templateId: String): Resp = {
    val id = GDocId.from(gdocId, templateId)
    returnLog(id)
  }


  def returnLog(gdocId: GDocId): Resp = gdocId.docId.synchronized {
    val log = GDoc2LatexWorker.getLastLog(gdocId)
    if (log.nonEmpty)
      htmlResp("Log",
        h1("Log:"),
        pre(Files.readString(log.get.toPath))
      )
    else
      htmlResp("Log",
        h1("No log"),
        p("Try to ",
          a("build again", href := "/update/" + gdocId.toParam),
          ".")
      )
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