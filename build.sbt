name := "gdoc2latex"

version := "0.1"

scalaVersion := "2.13.6"

enablePlugins(JavaAppPackaging)
//mainClass in Compile := Some("edu.cmu.ckaestne.gdoc2latex.server.Server")
//discoveredMainClasses in Compile := Seq()


libraryDependencies += "com.google.api-client" % "google-api-client" % "1.30.1"
libraryDependencies += "com.google.oauth-client" % "google-oauth-client-jetty" % "1.30.1"
libraryDependencies += "com.google.auth" % "google-auth-library-oauth2-http" % "1.3.0"
libraryDependencies += "com.google.apis" % "google-api-services-docs" % "v1-rev20190827-1.30.1"
libraryDependencies += "com.lihaoyi" %% "cask" % "0.7.3"
libraryDependencies += "com.lihaoyi" %% "scalatags" % "0.9.1"
