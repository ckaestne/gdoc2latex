package converter

import edu.cmu.ckaestne.gdoc2latex.converter.{ImageNames, Util}
import edu.cmu.ckaestne.gdoc2latex.util.GDrawing
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils

import java.io.{ByteArrayInputStream, File, InputStream}
import java.net.URI
import java.nio.file.Path
import java.util

abstract class AbstractRenderer(drawings: List[GDrawing] = Nil, imgDir: File) {

  protected def findDrawing(pngContent: Array[Byte]): Option[GDrawing] =
    drawings.find(x => util.Arrays.equals(x.contentPNG, pngContent))



  protected def resolveImageUri(id: String, uri: String): Option[(Path, InputStream)] = {
    val connection = new URI(uri).toURL.openConnection
    val mimeType = connection.getContentType
    if (!supportedMime.contains(mimeType)) {
      System.err.println(s"    unsupported image format \"$mimeType\" from $uri")
      return None
    }
    val content = IOUtils.toByteArray(connection.getInputStream)
    val drawing = findDrawing(content)

    if (drawing.isDefined) {
      val (extension, content) = getDrawingContent(drawing.get)
      val filePath = new File(imgDir, getImageFilename(id, drawing.get.name, extension)).toPath
      Some((filePath, new ByteArrayInputStream(content)))
    } else {
      val filename = ImageNames.getName(DigestUtils.md5Hex(id))
      val filePath = new File(imgDir, getImageFilename(id, filename, supportedMime(mimeType))).toPath
      Some((filePath, new ByteArrayInputStream(content)))
    }
  }

  protected def getDrawingContent(drawing: GDrawing): (String, Array[Byte])

  protected val supportedMime: Map[String, String] = Map("image/jpeg"->".jpg", "image/png"->".png")

  protected def getImageFilename(id: String, name: String, extension: String): String = Util.textToId(name) + extension

}
