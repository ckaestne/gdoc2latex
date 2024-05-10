package util

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequest
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.docs.v1.{Docs, DocsScopes}
import com.google.api.services.docs.v1.model.{BatchUpdateDocumentRequest, Color, Document, OptionalColor, ParagraphElement, Request, RgbColor, TextStyle, UpdateTextStyleRequest}
import com.google.api.services.drive.{Drive, DriveScopes}
import com.google.auth.Credentials
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials
import edu.cmu.ckaestne.gdoc2latex.util.GDocConnection
import edu.cmu.ckaestne.gdoc2latex.util.GDocConnection.docService

import java.io.{FileInputStream, IOException}
import java.security.GeneralSecurityException
import scala.jdk.CollectionConverters.{CollectionHasAsScala, SeqHasAsJava}


object GDocEditorConnection {
  private val APPLICATION_NAME = "GDoc2Latex-editor"
  private val JSON_FACTORY = GsonFactory.getDefaultInstance

  private val SCOPES = List(DocsScopes.DOCUMENTS, DriveScopes.DRIVE, DriveScopes.DRIVE_FILE)

  //secret, do not share this file
  private val CREDENTIALS_FILE_PATH = "credentials/editor.json"

  lazy val serviceAccountCredentials = ServiceAccountCredentials.fromStream(new FileInputStream(CREDENTIALS_FILE_PATH))

  lazy val credentials: Credentials = serviceAccountCredentials.createScoped(SCOPES.asJava)

  class ExtendedTimeoutHttpCredentialsAdapter(credentials: Credentials) extends HttpCredentialsAdapter(credentials) {
    override def initialize(request: HttpRequest): Unit = {
      super.initialize(request)
      request.setReadTimeout(60000)
    }
  }

  lazy val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
  lazy val driveService = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, new ExtendedTimeoutHttpCredentialsAdapter(credentials)).setApplicationName(APPLICATION_NAME).build
  lazy val docService = new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, new ExtendedTimeoutHttpCredentialsAdapter(credentials)).setApplicationName(APPLICATION_NAME).build

  @throws[IOException]
  @throws[GeneralSecurityException]
  def getDocument(documentId: String, withSuggestions: Boolean = false): Document = {
    docService.documents.get(documentId).setSuggestionsViewMode(if (withSuggestions) "PREVIEW_SUGGESTIONS_ACCEPTED" else "PREVIEW_WITHOUT_SUGGESTIONS").execute
  }
}

object Book {

  case class Chapter(title: String, gdocId: String, alternativeTitles: List[String] = Nil)

  case class Part(title: String, chapters: Chapter*)

  val book_full = List(
    Part("Setting the Stage",
      Chapter("Introduction", "1BTLMV-BcUfKjpkdrG11CTHtzcergCX8GVc1jCXRUaMw"),
      Chapter("From Models to Systems", "1v0zy1RtV0nFqsIpjNKtbkVsRTdI6FMCjBNYrox6z_Q8"),
      Chapter("Machine Learning for Software Engineers, in a Nutshell", "137FEqJU1QdRBSFXb8AFMZz2ZnAim3Km9ImaBhTBL-Dc")
    ),
    Part("Requirements Engineering",
      Chapter("When to use Machine Learning", "1q2URdkjm7yq07xslRqkGejJ9RZ4_7hCsCuA55b3gvKw"),
      Chapter("Setting and Measuring Goals", "1y1glrj0SxYg5BjHkUsY5jj7VvSnlB_KhhWTgrS-tIv8", List("Goals and Success Measures")),
      Chapter("Gathering Requirements", "1rO9vXq_olvvsmipG1Wo3ZVYYuJcsR-Gxf3n6KXyP1gM"),
      Chapter("Planning for Mistakes", "1YDk_tnfO3CXCzjThDArx7GXbdL6uC4v4xamiN0-ccYo")
    ),
    Part("Architecture and Design",
      Chapter("Thinking like a Software Architect", "1p4MQ2YCbdbzLdnGLvChM61hSplM2eJ3bgw42AdLxtuw"),
      Chapter("Quality Attributes of ML Components", "1z6zH9XZPb_0yKfG5pM6ieBfP68-veGwRvBp9TYA4iU8", List("Quality Drivers")),
      Chapter("Deploying a Model", "1GuXYtKSLWDif_L-hCx9yw1gP5wVwhlJ-geto3Q3jA_o", List("Deploying the Model")),
      Chapter("Automating the Pipeline", "1oyxA2h4Qkg2H7AWv2H0Qk9hLGdDg2JcnesGp-nD-caE", List("Automating the ML Pipeline")),
      Chapter("Scaling the System", "1U6uFl47FJbZMrvipxBMFlLFY8VgNjmJVqITzhcSmv9s"),
      Chapter("Planning for Operations", "1vJe0LdmFMHVZ9gQjYsYpJPr4TRf33ZQW3aNdB8FKGOw")
    ),
    Part("Quality Assurance",
      Chapter("Quality Assurance Basics", "1auo5Md5Bvz87M3E_U0RJno9l93XzQacX0kdZ6kLYOLQ", List("Basics of Quality Assurance")),
      Chapter("Model Quality", "1xTmMpMeHEWsyAKY8n_48UwZmw6S621t8b00GGt6ILiA"),
      Chapter("Data Quality", "1QT3Z3nX3BCKYzFKK1dvHuWI-0Cqht6F1pvgXoRmAwmU"),
      Chapter("Pipeline Quality", "1lPZw6f3xf6OfBVH_7la6o9_3_pv3ytuie0Xrh2c1q3M", List("ML Pipeline Quality")),
      Chapter("System Quality", "1E0D_NZMtxe4VBS3fg4lEVixp_MOEIa09ywFL-P_AlLs", List("Integration and System Testing")),
      Chapter("Testing and Experimenting in Production", "1d3cosuMlZbsIPc2M1c_zqT7lgTsVAmbRM0qs9QwM7fg", List("Testing in Production", "Quality Assurance in Production")),
    ),
    Part("Process and Teams",
      Chapter("Data Science and Software Engineering Process Models", "1aItRZ0dN5jTOt2knzaO7iVkJOeOE3NScEuIMCCF1dso", List("Process Models")),
      Chapter("Interdisciplinary Teams", "1J674cxmyTtB9anonKvBtj25gKunMXo1uqKvfk_6bDnI"),
      Chapter("Technical Debt", "1LreeW9W07RtTpmBPSZGYFGFXC70BKjvUfPfhQxi1VuU"),
    ),
    Part("Responsible ML Engineering",
      Chapter("Responsible Engineering", "1vP0eCbiPSO_52GLfzoN_wMHJlZe0v3b9hcYeQGzhREg"),
      Chapter("Versioning, Provenance, and Reproducibility", "11pW4tFSDBUkEcica5tYH9twWPKZmqgLunwxboNTLMfs"),
      Chapter("Explainability", "1GeApP3DWWMqCzlEEoY8FxAVsjLRiukJeppuKy-2NiK4", List("Interpretability and Explainability")),
      Chapter("Fairness", "1WbltZe010r212sEsUjZgcHJgT5qeofwCGmYVEorhnIk"),
      Chapter("Safety", "1JlZyPxL_S9zov_ZpJtb-w3mCQ7W0voRTcTDdEa5En-4"),
      Chapter("Security and Privacy", "1uBFR-1uIqMTl5NN_mkIiWiruRZgOwBKBQKCdjWl8ipM"),
      Chapter("Transparency and Accountability", "1kIvB9btlo5SefPu4XZhg3dMvGqunEA8PQBTeJYBF4EE")
    )
  )
}

/**
 * convert underlined text into text with grey background color, unless the underlined text is a link or a chapter reference
 */
object IndexingUtil extends App {

  def updateIndexingFormatting(docId: String) {
    val doc = GDocEditorConnection.getDocument(docId, false)

    var updateList: List[(Int, Int, TextStyle)] = List()
    var lastEl: List[ParagraphElement] = List()

    def isChaptersLink(lastEl: List[ParagraphElement]): Boolean = lastEl.nonEmpty && (
      lastEl.head.getTextRun.getContent.trim.toLowerCase.endsWith("chapter") ||
        lastEl.head.getTextRun.getContent.trim.toLowerCase.endsWith("chapters") ||
        (lastEl.head.getTextRun.getContent.trim.toLowerCase.endsWith("and") && lastEl.tail.head.getTextRun.getTextStyle.getUnderline) ||
          (lastEl.head.getTextRun.getContent.trim == "," && lastEl.tail.head.getTextRun.getTextStyle.getUnderline)
      )

    for (sel <- doc.getBody.getContent.asScala;
         if sel.getParagraph != null;
         el <- sel.getParagraph.getElements.asScala;
         if el.getTextRun != null
         if el.getTextRun.getTextStyle.getLink == null) {

      if (el.getTextRun.getTextStyle.getUnderline && lastEl != null && !isChaptersLink(lastEl)) {
        println(el.getTextRun.getContent)
        updateList ::= (el.getStartIndex, el.getEndIndex, el.getTextRun.getTextStyle)
      }

      lastEl ::= el
    }

    if (updateList.isEmpty) {
      println("nothing to update")
      return
    }
    val bgColor = new OptionalColor().setColor(new Color().setRgbColor(new RgbColor().setRed(0.85f).setGreen(0.85f).setBlue(0.85f)))

    val updateRequests = updateList.map(e =>
      new UpdateTextStyleRequest()
        .setRange(new com.google.api.services.docs.v1.model.Range().setStartIndex(e._1).setEndIndex(e._2))
        .setTextStyle(e._3.clone().setUnderline(false).setBackgroundColor(bgColor))
        .setFields("*")
    ).reverse
    val batchRequest = new BatchUpdateDocumentRequest().setRequests(updateRequests.map(x => new Request().setUpdateTextStyle(x)).asJava)

    println(GsonFactory.getDefaultInstance.toString(batchRequest))

    val r = GDocEditorConnection.docService.documents().batchUpdate(docId, batchRequest).execute()

    println(GsonFactory.getDefaultInstance.toString(r))
  }

  Book.book_full.flatMap(_.chapters).drop(7+6+9).map(_.gdocId).foreach(updateIndexingFormatting)
//
//  updateIndexingFormatting(
//    "1s108vGsb19TXdWpCI03nKaogjSpyonuRMvUzw9yjoWY"
//  )
}
