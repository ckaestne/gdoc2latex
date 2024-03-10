package edu.cmu.ckaestne.gdoc2latex.util

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequest
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.docs.v1.model.Document
import com.google.api.services.docs.v1.{Docs, DocsScopes}
import com.google.api.services.drive.{Drive, DriveScopes}
import com.google.auth.Credentials
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream, FileOutputStream, IOException, ObjectInputStream, ObjectOutputStream}
import java.net.SocketTimeoutException
import java.security.GeneralSecurityException
import javax.imageio.ImageIO
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

  def getFileOrDirectory(id: String): List[GDriveFile] = {
    val files = getDirectory(id)
    if (files.isEmpty) {
      List(GDriveFile(id, "main.tex", -1, loadGDocPlain(id, -1)))
    } else files
  }

  def getDirectory(directoryId: String): List[GDriveFile] = {
    println(s"loading directory $directoryId")
    val fileList = driveService.files().list().setQ(s"'$directoryId' in parents").setFields("files(id, name, mimeType, version)").execute()
    println(s"loading ${fileList.getFiles.size()} files")
    for (file <- fileList.getFiles.asScala.toList) yield {
      val content = if (file.getMimeType == "application/vnd.google-apps.document") loadGDocPlain(file.getId, file.getVersion)
      else loadBinaryFile(file.getId, file.getVersion)

      GDriveFile(file.getId, file.getName, file.getVersion, content)
    }

  }

  def collectDrawings(directoryId: String): List[GDrawing] = {
    println(s"indexing drawings in directory $directoryId")
    var result: List[GDrawing] = Nil
    val fileList = driveService.files().list().setQ(s"'$directoryId' in parents").setFields("files(id, name, mimeType, version)").execute()
    for (file <- fileList.getFiles.asScala.toList) {
      if (file.getMimeType == "application/vnd.google-apps.folder") {
        result ++= collectDrawings(file.getId)
      }
      if (file.getMimeType == "application/vnd.google-apps.drawing") {
        //println(s"  indexing drawing ${file.getName}")
        val (png, pdf, svg) = indexGDrawingContent(file.getId, file.getVersion)
        val img = ImageIO.read(new ByteArrayInputStream(png()))
        result ::= GDrawing(file.getId, file.getName, file.getVersion, (img.getWidth, img.getHeight), png, pdf, svg)
      }
    }
    result
  }

  /** note, version is only used for caching, not for the actual request */
  private def loadBinaryFile(fileId: String, version: Long): Array[Byte] =
    RemoteObjectCache.getOrLoad(fileId, version, () => {
      val output = new ByteArrayOutputStream()
      driveService.files().get(fileId).executeMediaAndDownloadTo(output)
      output.toByteArray
    })

  /** note, version is only used for caching, not for the actual request */
  private def loadGDocPlain(fileId: String, version: Long): Array[Byte] =
    RemoteObjectCache.getOrLoad(fileId, version, () => {
      val output = new ByteArrayOutputStream()
      driveService.files().`export`(fileId, "text/plain").executeMediaAndDownloadTo(output)
      output.toByteArray
    })

  private def indexGDrawingContent(fileId: String, version: Long): (() => Array[Byte], () => Array[Byte], () => Array[Byte]) = {
    val pdf = exportMedia(fileId, version, "application/pdf")
    val png = exportMedia(fileId, version, "image/png")
    val svg = exportMedia(fileId, version, "image/svg+xml")
    (png, pdf, svg)
  }

  private def exportMedia(fileId: String, version: Long, format: String): () => Array[Byte] =
    () => RemoteObjectCache.getOrLoad(fileId + "%" + format, version, () => {
      val output = new ByteArrayOutputStream()
      try {
//        println(s"    downloading media $fileId, $format")
        driveService.files().export(fileId, format).executeMediaAndDownloadTo(output)
      } catch {
        case e: SocketTimeoutException =>
          System.err.println(s"    Failed to download $fileId, $format: " + e.getMessage)
        case e: Exception =>
          System.err.println(s"    Failed to download $fileId, $format: " + e.getMessage)
          throw e
      }
      output.toByteArray
    })


  def lastUpdatedOn(fileId: String): DateTime = {
    driveService.files().get(fileId).execute().getModifiedTime
  }

  def persistCache() {
    RemoteObjectCache.storeCache()
  }

  def clearCache(): Unit = {
    RemoteObjectCache.clear()
  }

}

//object GDriveContentCache {
//
////  private val cache: mutable.Map[String, GDriveFile] = mutable.Map()
////  private val queue: mutable.ArrayDeque[String] = mutable.ArrayDeque() // queue of accessed file names, last accessed first
////  private val MAX_CACHE_SIZE = 500
////
////  private def enqueue(id: String): Unit = {
////    val oldPos = queue.indexOf(id)
////    if (oldPos >= 0) queue.remove(oldPos)
////    queue.addOne(id)
////    while (queue.size > MAX_CACHE_SIZE) {
////      val id = queue.removeHead(false)
////      cache.remove(id)
////    }
////  }
//
//  def getFile(id: String, name: String, version: Long, load: (String) => Array[Byte]): GDriveFile = {
//    val cachedFile = this.cache.get(id)
//    if (cachedFile.isDefined && cachedFile.get.version == version) {
//      println(s"loading file $id from cache")
//      enqueue(id)
//      cachedFile.get
//    } else {
//      println(s"downloading file $id")
//      val content = load(id)
//      val newFile = GDriveFile(id, name, version, content)
//      cache.put(id, newFile)
//      enqueue(id)
//      newFile
//    }
//  }
//}

case class GDriveDirectory(id: String, files: List[GDriveFile])

case class GDriveFile(id: String, name: String, version: Long, content: Array[Byte])

case class GDrawing(id: String, name: String, version: Long, size: (Int, Int), contentPNG_ : () => Array[Byte], contentPDF_ : () => Array[Byte], contentSVG_ : () => Array[Byte]) {
  lazy val contentPNG: Array[Byte] = contentPNG_()
  lazy val contentPDF: Array[Byte] = contentPDF_()
  lazy val contentSVG: Array[Byte] = contentSVG_()
}


object RemoteObjectCache {

  private val cacheFileName = ".remoteObjectCache"
  private var cache: Map[String, CachedContent] = loadCache()

  def loadCache(): Map[String, CachedContent] = {
    try {
      val ois = new ObjectInputStream(new FileInputStream(cacheFileName))
      val nr = ois.readLong()
      println(s"loading $nr cached objects")
      val entries: Seq[(String, CachedContent)] = for (i <- 0 until nr.toInt) yield {
        val c = ois.readObject.asInstanceOf[CachedContent]
        (c.id -> c)
      }
      ois.close
      entries.toMap
    } catch {
      case o: IOException =>
        println("no cache/unable to load")
        return Map()
    }
  }

  def storeCache(): Unit = {
    val oos = new ObjectOutputStream(new FileOutputStream(cacheFileName))
    val objs = cache.values.toList
    oos.writeLong(objs.size)
    for (obj <- objs)
      oos.writeObject(obj)
    oos.close
  }

  def get(id: String, version: Long): Option[Array[Byte]] = if (version < 0) None else
    cache.get(id).filter(_.version == version).map(_.content)


  def getOrLoad(id: String, version: Long, load: () => Array[Byte]): Array[Byte] = if (version < 0) load() else {
    val old = get(id, version)
    if (old.isEmpty) {
      val v = load()
      put(id, version, v)
      v
    } else old.get

  }


  def put(id: String, version: Long, content: Array[Byte]): Unit = {
    cache = cache + (id -> CachedContent(id, version, content))
  }

  def clear(): Unit = {
    cache=Map()
  }

  @SerialVersionUID(54)
  case class CachedContent(id: String, version: Long, content: Array[Byte])

}
