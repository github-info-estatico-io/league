version := "0.1.0"

scalaVersion := "2.13.8"

Global / excludeLintKeys += root / idePackagePrefix

// Allows for System.exit() without shutting down the sbt shell.
run / fork := true

val circeVersion = "0.14.2"

lazy val root = (project in file("."))
  .settings(
    name := "league",
    idePackagePrefix := Some("io.estatico.league"),
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-Wconf:cat=other-match-analysis:error",
      "-unchecked",
      "-feature",
      "-deprecation"
    ),
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "fastparse" % "2.3.3",
      "junit" % "junit" % "4.13.2" % Test,
      "com.novocode" % "junit-interface" % "0.11" % Test,
      "io.circe" %% "circe-core" % circeVersion % Test,
      "io.circe" %% "circe-generic" % circeVersion % Test,
      "io.circe" %% "circe-parser" % circeVersion % Test
    )
  )
