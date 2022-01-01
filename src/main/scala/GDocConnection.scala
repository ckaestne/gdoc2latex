package edu.cmu.ckaestne.gdoc2latex
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
  private val APPLICATION_NAME = "GDoc2Latex"
  private val JSON_FACTORY = JacksonFactory.getDefaultInstance
  private val TOKENS_DIRECTORY_PATH = "tokens"
  /**
   * Global instance of the scopes required by this quickstart. If modifying these scopes, delete
   * your previously saved tokens/ folder.
   */
  private val SCOPES = Collections.singletonList(DocsScopes.DOCUMENTS_READONLY)

  //this is not considered private for desktop applications and shipped as secret with this app:
  private val CREDENTIALS_FILE_PATH = "/client_secret_728363515906-0cnss9311fvilo2l4ngorsnl5m49pmso.apps.googleusercontent.com.json"

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

  @throws[IOException]
  @throws[GeneralSecurityException]
  def getDocument(documentId: String) = {
    val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
    val service = new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT)).setApplicationName(APPLICATION_NAME).build
    service.documents.get(documentId).execute
  }
}
