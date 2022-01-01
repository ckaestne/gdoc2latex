package edu.cmu.ckaestne.gdoc2latex

import com.google.api.services.docs.v1.model.{Document, Paragraph, ParagraphElement}

import java.io.{File, FileWriter, PrintWriter}


object GDoc2Latex extends App {
  private val DOCUMENT_ID = "1d4yWGqfg9BvWYEHxV2AmiM6DQ-UQyqNLltn59jLx0E8"

  val doc = GDocConnection.getDocument(DOCUMENT_ID)
  val outw: PrintWriter =
    if (args.size >= 1) new PrintWriter(new FileWriter(new File(args(0))))
    else new PrintWriter(System.out);
  val outw2: PrintWriter =
    if (args.size == 2) new PrintWriter(new FileWriter(new File(args(1))))
    else new PrintWriter(System.out);


  val ldoc = new GDoc2LatexConverter().convert(doc)
  outw.print(ldoc.latexBody)

  outw2.println(s"\\newcommand\\papertitle{${ldoc.title}}")
  outw2.println(s"\\newcommand\\paperabstract{${ldoc.abstr}}")

  outw.close()
  outw2.close()
}
