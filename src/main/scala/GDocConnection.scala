package edu.cmu.ckaestne.gdoc2latex
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets

import scala.jdk.CollectionConverters._
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.docs.v1.model.{Document, ParagraphElement, StructuralElement}
import com.google.api.services.docs.v1.{Docs, DocsScopes}
import com.google.auth.Credentials
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}

import java.io.{File, IOException, InputStreamReader}
import java.security.GeneralSecurityException
import java.util
import java.util.Collections

object GDocConnection {
  private val APPLICATION_NAME = "GDoc2Latex"
  private val JSON_FACTORY = JacksonFactory.getDefaultInstance
  /**
   * Global instance of the scopes required by this quickstart. If modifying these scopes, delete
   * your previously saved tokens/ folder.
   */
  private val SCOPES = Collections.singletonList(DocsScopes.DOCUMENTS_READONLY)

  //secret, do not share this file
  private val CREDENTIALS_FILE_PATH = "/gdoc2latex-d56ea8eb76c8.json"

  @throws[IOException]
  private def getCredentials(): Credentials = { // Load client secrets.
    val in = this.getClass.getResourceAsStream(CREDENTIALS_FILE_PATH)
    ServiceAccountCredentials.fromStream(in).createScoped(SCOPES)
  }

  @throws[IOException]
  @throws[GeneralSecurityException]
  def getDocument(documentId: String): Document = {
    val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
    val service = new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(getCredentials())).setApplicationName(APPLICATION_NAME).build
    service.documents.get(documentId).execute
  }
}
