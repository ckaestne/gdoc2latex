name := "gdoc2latex"

version := "0.1"

scalaVersion := "2.13.6"

enablePlugins(JavaAppPackaging)
Compile / mainClass := Some("edu.cmu.ckaestne.gdoc2latex.cli.GDoc2LatexCLI")
Universal / executableScriptName := "gdoc2latex"


libraryDependencies += "com.google.api-client" % "google-api-client" % "1.33.0"
libraryDependencies += "com.google.oauth-client" % "google-oauth-client-jetty" % "1.32.1"
libraryDependencies += "com.google.auth" % "google-auth-library-oauth2-http" % "1.3.0"
libraryDependencies += "com.google.apis" % "google-api-services-docs" % "v1-rev20210707-1.32.1"
libraryDependencies += "com.google.apis" % "google-api-services-drive" % "v3-rev20211107-1.32.1"
libraryDependencies += "com.lihaoyi" %% "cask" % "0.7.3"
libraryDependencies += "com.lihaoyi" %% "scalatags" % "0.9.1"
libraryDependencies += "com.github.scopt" %% "scopt" % "4.0.1"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10" % "test"

