import org.scalajs.linker.interface.{ModuleKind, ModuleSplitStyle}

ThisBuild / scalaVersion := "3.3.8"
ThisBuild / organization := "app.owlhours"
// braces only: indentation-based blocks do not compile (and .scalafmt.conf refuses them too)
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:imports", "-no-indent")

val V = new {
  val kebs            = "2.2.2"
  val munit           = "1.3.6"
  val munitScalaCheck = "1.3.1"
  val circe           = "0.14.17"
  val catsEffect      = "3.5.7"
  val calico          = "0.2.3"
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
    name                                   := "owlhours-domain",
    libraryDependencies += "pl.iterators" %%% "kebs-opaque" % V.kebs,
    testDeps
  )
// Scala.js gets a small java.time of its own (modules/domain/js), not scala-java-time, whose formatters and locale data were ~9 % of the bundle

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

lazy val app = project
  .in(file("modules/app"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(domain.js, model.js, catalogue.js, store.js)
  .settings(
    name                            := "owlhours-app",
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("owlhours.app")))
    },
    // munit on Node runs CommonJS; only the browser bundle is ES modules
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)
      .withModuleSplitStyle(ModuleSplitStyle.FewestModules)),
    // Time-zone tests (DST) run in a fixed zone; the code itself uses the browser's zone.
    Test / jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(
      org.scalajs.jsenv.nodejs.NodeJSEnv.Config().withEnv(Map("TZ" -> "Europe/Warsaw"))
    ),
    libraryDependencies += "com.armanbilge" %%% "calico" % V.calico,
    testDeps
  )

lazy val root = project
  .in(file("."))
  .aggregate(domain.jvm, domain.js, model.jvm, model.js, catalogue.jvm, catalogue.js, store.jvm, store.js, app)
  .settings(name := "owlhours", publish / skip := true)
