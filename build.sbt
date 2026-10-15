ThisBuild / scalaVersion := "3.3.8"
ThisBuild / organization := "app.owlhours"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:imports")

val V = new {
  val kebs            = "2.2.2"
  val munit           = "1.3.6"
  val munitScalaCheck = "1.3.1"
  val scalaJavaTime   = "2.7.0"
  val circe           = "0.14.17"
  val catsEffect      = "3.5.7"
}

val testDeps = Def.settings(
  libraryDependencies ++= Seq(
    "org.scalameta" %%% "munit"            % V.munit           % Test,
    "org.scalameta" %%% "munit-scalacheck" % V.munitScalaCheck % Test
  )
)

lazy val domain = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/domain"))
  .settings(
    name := "owlhours-domain",
    libraryDependencies += "pl.iterators" %%% "kebs-opaque" % V.kebs,
    testDeps
  )
  .jsSettings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % V.scalaJavaTime)

lazy val model = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/model"))
  .dependsOn(domain)
  .settings(name := "owlhours-model", testDeps)
  .jvmSettings(libraryDependencies += "io.circe" %% "circe-parser" % V.circe % Test)

lazy val catalogue = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/catalogue"))
  .dependsOn(domain)
  .settings(
    name := "owlhours-catalogue",
    libraryDependencies ++= Seq(
      "io.circe"     %%% "circe-core"   % V.circe,
      "io.circe"     %%% "circe-parser" % V.circe,
      "pl.iterators" %%% "kebs-circe"   % V.kebs
    ),
    testDeps
  )
  .jvmSettings(
    // the shipped data file is read by the JVM data-completeness suite
    Test / unmanagedResourceDirectories += (ThisBuild / baseDirectory).value / "modules" / "catalogue" / "data"
  )

lazy val store = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/store"))
  .dependsOn(domain)
  .settings(
    name := "owlhours-store",
    libraryDependencies ++= Seq(
      "io.circe"      %%% "circe-core"   % V.circe,
      "io.circe"      %%% "circe-parser" % V.circe,
      "pl.iterators"  %%% "kebs-circe"   % V.kebs,
      "org.typelevel" %%% "cats-effect"  % V.catsEffect
    ),
    testDeps
  )

lazy val root = project
  .in(file("."))
  .aggregate(domain.jvm, domain.js, model.jvm, model.js, catalogue.jvm, catalogue.js, store.jvm, store.js)
  .settings(name := "owlhours", publish / skip := true)
