import scala.jdk.CollectionConverters._
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.docs.v1.model.{ParagraphElement, StructuralElement}
import com.google.api.services.docs.v1.{Docs, DocsScopes}

import java.io.{File, IOException, InputStreamReader}
import java.security.GeneralSecurityException
import java.util
import java.util.Collections

object GDocConnection {
  private val APPLICATION_NAME = "Google Docs API Extract Guide"
  private val JSON_FACTORY = JacksonFactory.getDefaultInstance
  private val TOKENS_DIRECTORY_PATH = "tokens"
  private val DOCUMENT_ID = "12c4SkEdo72PwHWcXLh9l9N5m2IxpaDWK0qCFlG_Fmhg"
  /**
   * Global instance of the scopes required by this quickstart. If modifying these scopes, delete
   * your previously saved tokens/ folder.
   */
  private val SCOPES = Collections.singletonList(DocsScopes.DOCUMENTS_READONLY)
  private val CREDENTIALS_FILE_PATH = "client_secret_728363515906-qge3rgq2d00r7kv2rliiq9rabm0u94b8.apps.googleusercontent.com.json"

  /**
   * Creates an authorized Credential object.
   *
   * @param HTTP_TRANSPORT The network HTTP Transport.
   * @return An authorized Credential object.
   * @throws IOException If the credentials.json file cannot be found.
   */
  @throws[IOException]
  private def getCredentials(HTTP_TRANSPORT: NetHttpTransport) = { // Load client secrets.
    val in = this.getClass.getResourceAsStream(CREDENTIALS_FILE_PATH)
    val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in))
    // Build flow and trigger user authorization request.
    val flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES).setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH))).setAccessType("offline").build
    val receiver = new LocalServerReceiver.Builder().setPort(8888).build
    new AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
  }

//  /**
//   * Returns the text in the given ParagraphElement.
//   *
//   * @param element a ParagraphElement from a Google Doc
//   */
//  private def readParagraphElement(element: ParagraphElement): String = {
//    val run = element.getTextRun
//    if (run == null || run.getContent == null) { // The TextRun can be null if there is an inline object.
//      return ""
//    }
//    run.getContent
//  }
//
//  /**
//   * Recurses through a list of Structural Elements to read a document's text where text may be in
//   * nested elements.
//   *
//   * @param elements a list of Structural Elements
//   */
//  private def readStructuralElements(elements: util.List[StructuralElement]) = {
//    val sb = new StringBuilder
//    for (element <- elements.asScala) {
//      if (element.getParagraph != null) {
//        for (paragraphElement <- element.getParagraph.getElements.asScala) {
//          sb.append(readParagraphElement(paragraphElement))
//        }
//      }
//      else if (element.getTable != null) { // The text in table cells are in nested Structural Elements and tables may be
//        // nested.
//        for (row <- element.getTable.getTableRows.asScala) {
//          for (cell <- row.getTableCells.asScala) {
//            sb.append(readStructuralElements(cell.getContent))
//          }
//        }
//      }
//      else if (element.getTableOfContents != null) { // The text in the TOC is also in a Structural Element.
//        sb.append(readStructuralElements(element.getTableOfContents.getContent))
//      }
//    }
//    sb.toString
//  }

  //    public static void main(String... args) throws IOException, GeneralSecurityException {
  //        // Build a new authorized API client service.
  //        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
  //        Docs service =
  //                new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
  //                        .setApplicationName(APPLICATION_NAME)
  //                        .build();
  //
  //        Document doc = service.documents().get(DOCUMENT_ID).execute();
  //        new GDoc2Latex().process(doc);
  ////        System.out.println(readStructuralElements(doc.getBody().getContent()));
  //    }
  @throws[IOException]
  @throws[GeneralSecurityException]
  def getDocument() = {
    val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
    val service = new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT)).setApplicationName(APPLICATION_NAME).build
    service.documents.get(DOCUMENT_ID).execute
  }
}
