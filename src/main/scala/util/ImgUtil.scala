package util

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object ImgUtil {

  def loadImage(file: File) = ImageIO.read(file)

  def compareExact(img: BufferedImage, img2: BufferedImage): Boolean = {
    var diff =0

    if (img.getWidth!=img2.getWidth) return false
    if (img.getHeight!=img2.getHeight) return false
    for (x <- 0 until img.getWidth;
         y <- 0 until img.getHeight;
         if !img.getRGB(x,y).equals(img2.getRGB(x,y)))
          return false


    true
  }

  def compare(img: BufferedImage, img2: BufferedImage, threshold: Float=0.01f): Boolean = {
    var diff =0

    if (img.getWidth!=img2.getWidth) return false
    if (img.getHeight!=img2.getHeight) return false
    for (x <- 0 until img.getWidth)
      for (y <- 0 until img.getHeight)
        if (!img.getRGB(x,y).equals(img2.getRGB(x,y)))
          diff+=1
    diff < threshold*img.getWidth*img.getHeight
  }

  def createHistogram(img: BufferedImage): Array[Double] = {
    val histogram = new Array[Double](256)

    for (y <- 0 until img.getHeight; x <- 0 until img.getWidth) {
      val color = new Color(img.getRGB(x, y))
      val gray = (color.getRed * 0.3 + color.getGreen * 0.59 + color.getBlue * 0.11).toInt
      histogram(gray) += 1
    }

    histogram
  }

  def compareHistograms(histogram1: Array[Double], histogram2: Array[Double]): Double = {
    histogram1.zip(histogram2).map { case (h1, h2) => Math.min(h1, h2) }.sum
  }

}
