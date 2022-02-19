package book

import converter.Util
import edu.cmu.ckaestne.gdoc2latex.converter._
import edu.cmu.ckaestne.gdoc2latex.util.{GDocConnection, GDrawing}

import java.io.{BufferedWriter, File, FileWriter}
import java.net.URI
import java.nio.file.{Files, StandardCopyOption}
import scala.sys.process._


object SEAIBook extends App {

  case class Chapter(title: String, gdocId: String)

  case class Part(title: String, chapters: Chapter*)

  val drawings_directories = //List("1m5-_XW4pAWiaUqM8WKZMzGkBJOkPTEhX")
     List("142lDolwOzgd_SYqV_7r0Tq3m7mUvMRjw")

  val book_full = List(
    Part("Machine Learning in Production",
      Chapter("Introduction", "1BTLMV-BcUfKjpkdrG11CTHtzcergCX8GVc1jCXRUaMw"),
      Chapter("From models to systems", "1v0zy1RtV0nFqsIpjNKtbkVsRTdI6FMCjBNYrox6z_Q8"),
      Chapter("Machine learning for software engineers, in a nutshell", "137FEqJU1QdRBSFXb8AFMZz2ZnAim3Km9ImaBhTBL-Dc")),
    Part("Architecture and design",
      Chapter("Overview of architecture and design considerations of ML-enabled systems", "1YWJMNcVmIoxTbCiU9UZBEszS1f1eNAqkp_JSnakL5YM"),
      Chapter("Thinking like a software architect", "1p4MQ2YCbdbzLdnGLvChM61hSplM2eJ3bgw42AdLxtuw"),
      Chapter("Quality drivers in architectures of ML-enabled systems", "1z6zH9XZPb_0yKfG5pM6ieBfP68-veGwRvBp9TYA4iU8"),
      Chapter("Deploying a model", "1GuXYtKSLWDif_L-hCx9yw1gP5wVwhlJ-geto3Q3jA_o"),
      Chapter("Automating the ML pipeline", "1oyxA2h4Qkg2H7AWv2H0Qk9hLGdDg2JcnesGp-nD-caE"),
      Chapter("Scaling the system", "1U6uFl47FJbZMrvipxBMFlLFY8VgNjmJVqITzhcSmv9s"),
      Chapter("Planning for operations", "1vJe0LdmFMHVZ9gQjYsYpJPr4TRf33ZQW3aNdB8FKGOw")),
    Part("Quality assurance",
      Chapter("Model quality: Defining correctness and fit", "1PY6DqPNQjYdpYuHPGxu2r56QSO-ju7G-x2orzuUYH88"),
      Chapter("Model quality: Measuring prediction accuracy", "11q24jFET-UbdsRH7BxrAZ1mxwKU-DrVr-2vagCzM9js"),
      Chapter("Model quality: Slicing, capabilities, invariants, and other testing strategies", "1A-BYk4jjSsHE2mswtuoAAPqFnavUAbl6UGzdJcGVpmw"),
      Chapter("Data quality", "1QT3Z3nX3BCKYzFKK1dvHuWI-0Cqht6F1pvgXoRmAwmU"),
      //      Chapter("Integration and system testing", ""),
      //      Chapter("QA automation", ""),
      Chapter("Quality assurance in production", "1JtYRusDy4oWXSwZHQ7Itz2ZLtp0DB_aemkMGbscYjIU"),
      //      Chapter("Infrastructure quality", ""),
      //      Chapter("Debugging", "")),
    ),
    Part("Process and Teams",
      //      Chapter("Data science and software engineering process models", ""),
      //      Chapter("Interdisciplinary teams", ""),
      Chapter("Technical debt", "1LreeW9W07RtTpmBPSZGYFGFXC70BKjvUfPfhQxi1VuU"),
      //      Chapter("DevOps and MLOps culture", "")
    ),
    Part("Responsible ML Engineering",
      Chapter("Responsible Engineering", "1vP0eCbiPSO_52GLfzoN_wMHJlZe0v3b9hcYeQGzhREg"),
      Chapter("Versioning, provenance, and reproducibility", "11pW4tFSDBUkEcica5tYH9twWPKZmqgLunwxboNTLMfs"),
      Chapter("Interpretability and explainability", "1GeApP3DWWMqCzlEEoY8FxAVsjLRiukJeppuKy-2NiK4"),
      //      Chapter("Safety", ""),
      //      Chapter("Security and privacy", ""),
      //      Chapter("Fairness", ""),
      Chapter("Transparency and accountability", "1kIvB9btlo5SefPu4XZhg3dMvGqunEA8PQBTeJYBF4EE"))
  )

  val book = book_full
//    List(
//      Part("Machine Learning in Production",
//        Chapter("Machine learning for software engineers, in a nutshell", "137FEqJU1QdRBSFXb8AFMZz2ZnAim3Km9ImaBhTBL-Dc")),
//    )


  def bookToLatex(book: List[Part], drawings: List[GDrawing] = Nil) {

    //generate latex
    val targetDir = new File("book_latex")
    if (!targetDir.exists()) Files.createDirectory(targetDir.toPath)
    val mainFile = new File(targetDir, "book.tex")
    val mainFileWriter = new BufferedWriter(new FileWriter(mainFile))

    def dirName(title: String) = title.toLowerCase.replaceAll("\\W", "-").replace("--", "-")


    var headerImg: Option[String] = None

    def processChapter(adoc: IDocument): IDocument = {
      var doc = adoc
      if (doc.content.head.isInstanceOf[IParagraph])
        if (doc.content.head.asInstanceOf[IParagraph].plainText.startsWith("This chapter ") || doc.content.head.asInstanceOf[IParagraph].plainText.startsWith("This post "))
          doc = IDocument(doc.title, doc.abstr, doc.content.tail)
      //remove image at the very beginning
      if (doc.content.head.isInstanceOf[IImage])
        if (doc.content.head.asInstanceOf[IImage].caption.isEmpty) {
          headerImg = Some(doc.content.head.asInstanceOf[IImage].contentUri)
          doc = IDocument(doc.title, doc.abstr, doc.content.tail)
        }
      doc
    }


    for ((part, partidx) <- book.zipWithIndex) {
      println(s"Part $partidx: ${part.title}")
      mainFileWriter.write(s"\\part{${part.title}}\n")

      for ((chapter, chapteridx) <- part.chapters.zipWithIndex) {
        val chapterDir = new File(targetDir, s"${partidx + 1}-${chapteridx + 1}-${dirName(chapter.title)}")
        if (!chapterDir.exists()) Files.createDirectory(chapterDir.toPath)
        val latexFile = new File(chapterDir, dirName(chapter.title) + ".tex")
        val jsonFile = new File(chapterDir, dirName(chapter.title) + ".json")
        println(s"  Chapter $partidx.$chapteridx: ${chapter.title} -- ${latexFile.getPath}")

        mainFileWriter.write(s"\\input{${chapterDir.getName}/${latexFile.getName}}\n")

        val doc = GDocConnection.getDocument(chapter.gdocId)
        Files.writeString(jsonFile.toPath, doc.toPrettyString)

        //      println(doc)

        headerImg = None
        val ldoc = new LatexRenderer(ignoreImages = false, downloadImages = true, chapterDir, drawings).render(processChapter(new GDocParser().convert(doc)))

        var latex = s"\\graphicspath{ {${chapterDir.getName}/} }\n"
        if (headerImg.isDefined) {
          val h1 = new File(chapterDir, "header.jpg")
          val h2 = new File(chapterDir, "header_.jpg")
          val targetSize = "1638x732"
          Files.copy(new URI(headerImg.get).toURL().openConnection.getInputStream, h1.toPath, StandardCopyOption.REPLACE_EXISTING)
          s"convert ${h1.toPath} -resize $targetSize^ -gravity center -extent $targetSize ${h2.toPath}".!
          latex += "\\chapterimage{header_.jpg}\n"
        } else
          latex += "\\chapterimage{}\n"

        latex += s"\\chapter{${chapter.title}}\\label{${Util.textToId(chapter.title)}}\n\n" + ldoc.latexBody
        Files.writeString(latexFile.toPath, latex)
      }
    }

    mainFileWriter.close()
  }


  val drawings = drawings_directories.flatMap(GDocConnection.collectDrawings)

  bookToLatex(book, drawings)
}
