package edu.cmu.ckaestne.gdoc2latex.util

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.docs.v1.model.Document
import com.google.api.services.docs.v1.{Docs, DocsScopes}
import com.google.api.services.drive.{Drive, DriveScopes}
import com.google.auth.Credentials
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials

import java.io.{ByteArrayOutputStream, FileInputStream, IOException}
import java.security.GeneralSecurityException
import scala.collection.mutable
import scala.jdk.CollectionConverters._

object GDocConnection {
  private val APPLICATION_NAME = "GDoc2Latex"
  private val JSON_FACTORY = GsonFactory.getDefaultInstance

  private val SCOPES = List(DocsScopes.DOCUMENTS_READONLY, DriveScopes.DRIVE_READONLY)

  //secret, do not share this file
  private val CREDENTIALS_FILE_PATH = "credentials/api.json"

  lazy val serviceAccountCredentials = ServiceAccountCredentials.fromStream(new FileInputStream(CREDENTIALS_FILE_PATH))

  lazy val credentials: Credentials = serviceAccountCredentials.createScoped(SCOPES.asJava)


  lazy val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
  lazy val driveService = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credentials)).setApplicationName(APPLICATION_NAME).build
  lazy val docService = new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credentials)).setApplicationName(APPLICATION_NAME).build


  @throws[IOException]
  @throws[GeneralSecurityException]
  def getDocument(documentId: String): Document = {
    docService.documents.get(documentId).execute
  }

  def getFileOrDirectory(id: String): List[GDriveFile] = {
    val files = getDirectory(id)
    if (files.isEmpty) {
      List(GDriveFile(id, "main.tex", 0, loadGDocPlain(id)))
    } else files
  }

  def getDirectory(directoryId: String): List[GDriveFile] = {
    println(s"loading directory $directoryId")
    val fileList = driveService.files().list().setQ(s"'$directoryId' in parents").setFields("files(id, name, mimeType, version)").execute()
    println(s"loading ${fileList.getFiles.size()} files")
    for (file <- fileList.getFiles.asScala.toList) yield {
      val load = if (file.getMimeType == "application/vnd.google-apps.document") loadGDocPlain _
      else loadBinaryFile _

      GDriveContentCache.getFile(file.getId, file.getName, file.getVersion, load)
    }

  }

  def collectDrawings(directoryId: String): List[GDrawing] = {
    println(s"collecting drawings in directory $directoryId")
    var result: List[GDrawing] = Nil
    val fileList = driveService.files().list().setQ(s"'$directoryId' in parents").setFields("files(id, name, mimeType, version)").execute()
    for (file <- fileList.getFiles.asScala.toList) {
      if (file.getMimeType == "application/vnd.google-apps.folder") {
        result ++= collectDrawings(file.getId)
      }
      if (file.getMimeType == "application/vnd.google-apps.drawing") {
        println(s"  downloading drawing ${file.getName}")
        val (png, pdf, svg) = loadGDrawingContent(file.getId)
        result ::= GDrawing(file.getId, file.getName, file.getVersion, png, pdf, svg)
      }
    }
    result
  }

  private def loadBinaryFile(fileId: String): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    val file = driveService.files().get(fileId).executeMediaAndDownloadTo(output)
    output.toByteArray
  }

  private def loadGDocPlain(fileId: String): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    val file = driveService.files().`export`(fileId, "text/plain").executeMediaAndDownloadTo(output)
    output.toByteArray
  }

  private def loadGDrawingContent(fileId: String): (Array[Byte], () => Array[Byte], () => Array[Byte]) = {
    val pdf = exportMedia(fileId, "application/pdf")
    val png = exportMedia(fileId, "image/png")
    val svg = exportMedia(fileId, "image/svg")
    (png(), pdf, svg)
  }

  private def exportMedia(fileId: String, format: String): () => Array[Byte] = () => {
    val output = new ByteArrayOutputStream()
    driveService.files().export(fileId, format).executeMediaAndDownloadTo(output)
    output.toByteArray
  }

}

object GDriveContentCache {

  private val cache: mutable.Map[String, GDriveFile] = mutable.Map()
  private val queue: mutable.ArrayDeque[String] = mutable.ArrayDeque() // queue of accessed file names, last accessed first
  private val MAX_CACHE_SIZE = 500

  private def enqueue(id: String): Unit = {
    val oldPos = queue.indexOf(id)
    if (oldPos >= 0) queue.remove(oldPos)
    queue.addOne(id)
    while (queue.size > MAX_CACHE_SIZE) {
      val id = queue.removeHead(false)
      cache.remove(id)
    }
  }

  def getFile(id: String, name: String, version: Long, load: (String) => Array[Byte]): GDriveFile = {
    val cachedFile = this.cache.get(id)
    if (cachedFile.isDefined && cachedFile.get.version == version) {
      println(s"loading file $id from cache")
      enqueue(id)
      cachedFile.get
    } else {
      println(s"downloading file $id")
      val content = load(id)
      val newFile = GDriveFile(id, name, version, content)
      cache.put(id, newFile)
      enqueue(id)
      newFile
    }
  }
}

case class GDriveDirectory(id: String, files: List[GDriveFile])

case class GDriveFile(id: String, name: String, version: Long, content: Array[Byte])

case class GDrawing(id: String, name: String, version: Long, contentPNG: Array[Byte], contentPDF_ : () => Array[Byte], contentSVG_ : () => Array[Byte]) {
  lazy val contentPDF: Array[Byte] = contentPDF_()
  lazy val contentSVG: Array[Byte] = contentSVG_()
}

