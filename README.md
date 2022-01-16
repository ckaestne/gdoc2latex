# gdoc2latex

It’s time to move beyond writing Latex for most papers. Google Docs provides a much better editing experience than Overleaf or local latex editors for many projects: A good spell and grammar checker, a great collaborative editor with decent commenting features, citations with [Paperpile](https://paperpile.com), and a visually much less cluttered interface. Yet, Latex is a good backend for producing high-quality PDFs in a final layout.

This project converts Google Doc documents to Latex and onward to PDFs. The document is edited in Google Docs and a PDF version can be viewed in parallel in the required paper template to judge layout and length.

## Features

This project reads the formatting from Google Docs and converts it to Latex code. It supports the following functionality:

* *Bold* and *italics* formatting is converted to `\textbf` and `\emph`
* *Links* are converted to `\url` and `\href`
* *Itemization* is supported and rendered as `\begin{compactitem}`
* *Citations* are supported through [Paperpile](https://paperpile.com/). The bibliography is rendered by Paperpile and Paperpile links are translated to `\cite` instructions. No use of Bibtex.
* *Strikethrough* formatting is considered as comments and omitted in Latex
* *Headings* in GDoc are converted to `\section`, `\subsection`, and `\subsubsection` in Latex
* Internal links to section headers are converted to `\ref` references (the link text is discarded).
* A heading formatted as *Title* replaces the `\TITLE` command in the Latex template
* A paragraph starting with “Abstract:” is used as the abstract in the Latex template
* Text is otherwise not transformed or escaped, so Latex commands for math or images can be inserted directly in the Google Doc document.
* The main template and images and other files to be included can be configured through a Google Drive folder.

Additional formatting conversion could be added as needed.

## Try it

Until I exceed the free limits of Heroku, here is a version deployed online to try: https://gdoc2latex.herokuapp.com/update/1yUWsgyIDd7_C7s2SghSAaaPxmKNE0_hlcqfgbUjVci8

This link uses [this document](https://docs.google.com/document/d/1yUWsgyIDd7_C7s2SghSAaaPxmKNE0_hlcqfgbUjVci8/edit#), but simply replace the document ID with a different one to try other documents.

Here is more complete example from a recent paper draft ([document](https://docs.google.com/document/d/1yZZqEWgR7C7DcrkJZeuSpXRDqCyndUP4o8pvXF-1jvc/edit) and [template folder](https://drive.google.com/drive/folders/1qY_DmTZhPDb0SnaL5S3dH-hvSKk8VV5A)): https://gdoc2latex.herokuapp.com/update/1yZZqEWgR7C7DcrkJZeuSpXRDqCyndUP4o8pvXF-1jvc/1qY_DmTZhPDb0SnaL5S3dH-hvSKk8VV5A



## Command-Line Use

If using the command-line interface rather than the more convenient server setup described below, follow these instructions.

**Installation:** This project is implemented in Scala. It can be compiled with [sbt](https://www.scala-sbt.org/). After installing *sbt*, simply call`sbt stage` to download dependencies and build the project. This will create an executable CLI tool  in `/target/universal/stage/bin`.

**Google API permissions:** Unfortunately, accessing Google’s APIs programmatically requires some setup. Go to the [Google Developer Console](https://console.cloud.google.com/) to create a project, enable the Google Docs and Google Drive APIs, and create a service account under “Credentials” (service accounts seem easier than OAuth2, but OAuth2 is possible with a few lines of code changes in `GDocConnection` too). The basic “Viewer Role” is sufficient for the service account. The console will provide a json file with credentials for downloads that should be placed in `credentials/api.json`.

**Use:** Simply execute the project with `gdoc2latex <documentid>`, where the document id is the long character sequence in the URL of a Google Doc document. The project will access the document and print the converted Latex code to standard out.

Using parameter `-o <file>` the output latex file can be specified. Using parameter `-t <file>` a Latex template can be provided in which `\TITLE`, `\ABSTRACT` and `\CONTENT` will be replaced with the converted text.

Note that the program will need permission to access the document. To this end, the document needs to be public (“share with everybody with this link” as “viewer”) or shared with the email of the service account.

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
* `http://<host:port>/clean/<docid>/<templateid>`:  Remove cached results to force a rebuild (should never be needed)

In all these commands, templateid is optional. It can either point to a Google Docs document of which the plain text is used as a template or to a Google Drive folder. The folder can contain a main.tex file as the template and possibly other files such as images. Subfolders are not supported.

**Installation and credentials:** The process to compile the project and provide a `credentials/api.json` file are the same as above. The server can be started with the `server` binary in `/target/universal/stage/bin` and will listen on `http://localhost:3000` by default.

The server expects that *latex* is installed locally. The server will store results in the `out` directory. It uses a temporary directory of the operating system to compile the generated latex. By default it calls `pdflatex --pdf --interaction=nonstopmode main.tex` twice with a 10 second timeout each.

**Docker and Heroku:** Optionally, the system can be easily deployed to Docker and Heroku. Due to needed credentials, no prebuild containers are provided, but the `Dockerfile` and the `build-and-deploy.sh` script should make this process hopefully fairly straightforward. The default Dockerfile installs all of tex-live which is huge and may not be needed.

