name := "Leptovasi"

ThisBuild / scalaVersion := "3.4.2"

// Used for scala fix
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

ThisBuild / scalafixOnCompile := true
ThisBuild / scalafmtOnCompile := true

enablePlugins(JavaAppPackaging)

Universal / packageName := name.value
Universal / topLevelDirectory := None

javacOptions  ++= Seq("-Xlint", "-encoding", "UTF-8")
scalacOptions ++= Seq(
  "-explain",                      // Explain errors in more detail.
  "-explain-types",                // Explain type errors in more detail.
  "-indent",                       // Allow significant indentation.
  "-new-syntax",                   // Require `then` and `do` in control expressions.
  "-feature",                      // Emit warning and location for usages of features that should be imported explicitly.
  "-source:future",                // better-monadic-for
  "-language:higherKinds",         // Allow higher-kinded types
  "-language:implicitConversions", // Allow implicit conversions
  "-deprecation",                  // Emit warning and location for usages of deprecated APIs.
  "-Wunused:all",                  // Emit warnings for unused imports, local definitions, explicit parameters implicit, parameters method, parameters
  "-Wvalue-discard",               // Emit warnings for discarded non unit values
  "-Xcheck-macros",
)

libraryDependencies ++= Seq(
  hid4java,
  jnaPlatform,
  // Core
  circe,
  circeParser,
  fs2,
  fs2IO,
  catsEffect,
  catsRetry,
  // Logging
  log4cats,
  logbackClassic,
  // Config
  pureconfig,
  pureconfigCE,
)

lazy val catsEffect     = "org.typelevel"         %% "cats-effect"            % "3.5.0"
lazy val catsRetry      = "com.github.cb372"      %% "cats-retry"             % "3.1.3"
lazy val circe          = "io.circe"              %% "circe-core"             % "0.14.7"
lazy val circeParser    = circe.organization      %% "circe-parser"           % circe.revision
lazy val fs2            = "co.fs2"                %% "fs2-core"               % "3.10.2"
lazy val fs2IO          = fs2.organization        %% "fs2-io"                 % fs2.revision
lazy val hid4java       = "org.hid4java"           % "hid4java"               % "0.8.0"
lazy val log4cats       = "org.typelevel"         %% "log4cats-slf4j"         % "2.6.0"
lazy val logbackClassic = "ch.qos.logback"         % "logback-classic"        % "1.5.6"
lazy val pureconfig     = "com.github.pureconfig" %% "pureconfig-core"        % "0.17.7"
lazy val pureconfigCE   = pureconfig.organization %% "pureconfig-cats-effect" % pureconfig.revision
lazy val jnaPlatform    = "net.java.dev.jna"       % "jna-platform"           % "5.14.0"
