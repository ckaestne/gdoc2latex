# gdoc2latex

It’s time to move beyond writing Latex for most papers. Google Docs provides a much better editing experience than Overleaf or local latex editors for many projects: A good spell and grammar checker, a great collaborative editor with decent commenting features, citations with [Paperpile](https://paperpile.com), and a visually much less cluttered interface. Yet, Latex is a good backend for producing high-quality PDFs in a final layout.

This project converts Google Doc documents to Latex and onward to PDFs. The document is edited in Google Docs and a PDF version can be viewed in parallel in the required paper template to judge layout and length.

## Features (Latex)

This project reads the formatting from Google Docs and converts it to Latex code. It supports the following functionality:

* *Bold* and *italics* formatting is converted to `\textbf` and `\emph`
* *Links* are converted to `\url` and `\href`
* *Itemization* is supported and rendered as `\begin{compactitem}`
* *Citations* are supported through [Paperpile](https://paperpile.com/). See details below.
* *Strikethrough* formatting is considered as comments and omitted in Latex
* *Headings* in GDoc are converted to `\section`, `\subsection`, and `\subsubsection` in Latex
* *Footnotes* should work as expected
* *Subfix* and *superfix* formatting renders as `$_\text{...}` and `$^\text{...}`
* *Images* embedded in the document as the only element of a paragraph are downloaded as .png file and embedded in the Latex output with `\begin{figure}[h!tp]\centering\includegraphics[width=...]{...png}\caption{...}\alt{...}\end{figure}`. If the paragraph after the image is formatted in *italics* it is interpreted as the image caption. Alt text of the image in the Google doc is converted to `\alt{...}` if available. Images are scaled down relatively if they are scaled in the Google doc. 
* Text with grey background color is interpreted as an *index* term and produces an `\index` entry. Text formatted grey and strikethrough will produce an index entry without having the text in the document too.
* Internal links to section headers are converted to `\ref` references (the link text is discarded).
* A heading formatted as *Title* replaces the `\TITLE` command in the Latex template
* A paragraph starting with “Abstract:” is used as the abstract in the Latex template
* Text is otherwise not transformed or escaped, so Latex commands for math or images can be inserted directly in the Google Doc document.
* The main template and images and other files to be included can be configured through a Google Drive folder.

Additional formatting conversion could be added as needed.



### Paperpile citations/references

[Paperpile](https://paperpile.com/) can be used for citations. 
Bibtex is not used, references and formatting entirely relies on Paperpile.

Citations generate `\gencite{keys}{text}` where `keys` are paperpile-internal IDs and `text` is the original text in the document, e.g., "[1, 3]" or "(Foo et al., 2004)" depending on formatting used.

Bibliography entries are rendered as `\genbibitem{key}{prefix}{text}` in a `thebibliography` environment where `key` is the Paperpile-internal key, `prefix` is an optional element of a list such as "[3]", and `text` is the bibliography entry generated by Paperpile.
 

To use normal numeric citations in Latex use the following two definition for the generated macros, which generate `\bibitem` and `\cite` macros that Latex can then resolve to numbers:
```latex
\newcommand\gencite[2]{\cite{#1}}
\newcommand\genbibitem[3]{\bibitem{#1} #3}
```

For any other format like *natbib* or *alpha* let Paperpile do all the formatting and ignore the internal Ids, for example with the following macro definitions (the square-braket parameter for bibitem is not used but needed so that natbib does not complain):
```latex
\newcommand\gencite[2]{#2}
\newcommand\genbibitem[3]{\bibitem #3}
```

If for some reason the *natbib library needs to be included in the document, generate the bibitem with fake (not used) author and year strings:
```latex
\usepackage{natbib}
\newcommand\gencite[2]{#2}
\newcommand\genbibitem[3]{\bibitem[A(2020)] #3}
```

Only the first solution will create links within the document (if enabled). Creating links for other citation styles is not easily possible if multiple references may be used in the same citation, since Paperpile does not map references to individual text fragments of the citation and the text separators (brackets, commas or semicolons, etc) differ between citation styles. Some postprocessing of the keys and text in `\gencite` may be possible to pair them up for a given citation style. 

## Markdown

Conversion to markdown are also supported with mostly the same features as for Latex, but without section references, citations, images, and footnotes.


## Try it

Until I exceed the free limits of fly.io, here is a version deployed online to try: https://gdoc2latex.fly.dev/update/1yUWsgyIDd7_C7s2SghSAaaPxmKNE0_hlcqfgbUjVci8

This link uses [this document](https://docs.google.com/document/d/1yUWsgyIDd7_C7s2SghSAaaPxmKNE0_hlcqfgbUjVci8/edit#), but simply replace the document ID with a different one to try other documents.

Here is more complete example from a recent paper draft ([document](https://docs.google.com/document/d/1yZZqEWgR7C7DcrkJZeuSpXRDqCyndUP4o8pvXF-1jvc/edit) and [template folder](https://drive.google.com/drive/folders/1qY_DmTZhPDb0SnaL5S3dH-hvSKk8VV5A)): https://gdoc2latex.fly.dev/update/1yZZqEWgR7C7DcrkJZeuSpXRDqCyndUP4o8pvXF-1jvc/1qY_DmTZhPDb0SnaL5S3dH-hvSKk8VV5A



## Command-Line Use

If using the command-line interface rather than the more convenient server setup described below, follow these instructions.

**Installation:** This project is implemented in Scala. It can be compiled with [sbt](https://www.scala-sbt.org/). After installing *sbt*, simply call`sbt stage` to download dependencies and build the project. This will create an executable CLI tool  in `/target/universal/stage/bin`.

**Google API permissions:** Unfortunately, accessing Google’s APIs programmatically requires some setup. Go to the [Google Developer Console](https://console.cloud.google.com/) to create a project, enable the Google Docs and Google Drive APIs, and create a service account under “Credentials” (service accounts seem easier than OAuth2, but OAuth2 is possible with a few lines of code changes in `GDocConnection` too). The basic “Viewer Role” is sufficient for the service account. The console will provide a json file with credentials for downloads that should be placed in `credentials/api.json`.

**Use:** Simply execute the project with `gdoc2latex <documentid>`, where the document id is the long character sequence in the URL of a Google Doc document. The project will access the document and print the converted Latex code to standard out.

Using parameter `-o <file>` the output latex file can be specified. Using parameter `-t <file>` a Latex template can be provided in which `\TITLE`, `\ABSTRACT` and `\CONTENT` will be replaced with the converted text.

Note that the program will need permission to access the document. To this end, the document needs to be public (“share with everybody with this link” as “commenter”) or shared with the email of the service account.

Typically one would automate all steps with something like

```sh
gdoc2latex <documentid> -o paper/main.tex -t paper/template.tex
cd paper
latexmk --pdf --interaction=nonstopmode main.tex
cd ..

```

## Server

The more convenient way of using this project is hosting it as a service. This way, it is easy to open the Google Doc in one browser window and refresh the PDF in a different one by refreshing the page, possibly side by side. When using Firefox rather than Chrome the PDF reader even remembers the position within the PDF.

The project contains a `Server` class for this purpose that responds to GET requests as follows:

* `http://<host:port>/update/<docid>/<templateid>`: Convert the document identified by `docid` and return a PDF
* `http://<host:port>/latex/<docid>/<templateid>`:  Show the generated latex document
* `http://<host:port>/pdf/<docid>/<templateid>`:  Show the last PDF without rebuilding or updating it
* `http://<host:port>/log/<docid>/<templateid>`:  Show the latex output from the last 
* `http://<host:port>/clean/<docid>/<templateid>`:  Remove cached results to force a rebuild (should never be needed, but may be useful if updates to the template do not propagate)

In all these commands, templateid is optional. It can either point to a Google Docs document of which the plain text is used as a template or to a Google Drive folder. The folder can contain a main.tex file as the template and possibly other files such as images. Subfolders are not supported.

**Installation and credentials:** The process to compile the project and provide a `credentials/api.json` file are the same as above. The server can be started with the `server` binary in `/target/universal/stage/bin` and will listen on `http://localhost:3000` by default.

The server expects that *latex* is installed locally. The server will store results in the `out` directory. It uses a temporary directory of the operating system to compile the generated latex. By default it calls `pdflatex --pdf --interaction=nonstopmode main.tex` twice with a 10 second timeout each.

**Docker and Heroku/Fly.io:** Optionally, the system can be easily deployed to Docker and a hosting service like Heroku or Fly.io. Due to needed credentials, no prebuild containers are provided, but the `Dockerfile` and the `build-and-deploy.sh` script should make this process hopefully fairly straightforward. The default Dockerfile uses the full texlive installation which is huge and may not be needed.

