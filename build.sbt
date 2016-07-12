import UnidocKeys._
import FreeGen._
import ReleaseTransformations._
import OsgiKeys._

lazy val buildSettings = Seq(
  organization := "org.tpolecat",
  licenses ++= Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.10.6", scalaVersion.value) //, "2.12.0-M3")
)

lazy val commonSettings = Seq(
    scalacOptions ++= Seq(
      "-encoding", "UTF-8", // 2 args
      "-feature",
      "-deprecation",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:experimental.macros",
      "-unchecked",
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-dead-code",
      "-Ywarn-value-discard"
    ),
    scalacOptions in (Compile, doc) ++= Seq(
      "-groups",
      "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
      "-doc-source-url", "https://github.com/tpolecat/doobie/tree/v" + version.value + "€{FILE_PATH}.scala",
      "-skip-packages", "scalaz"
    ),
    libraryDependencies ++= macroParadise(scalaVersion.value) ++ Seq(
      "org.scalacheck" %% "scalacheck"  % "1.13.0" % "test",
      "org.specs2"     %% "specs2-core" % "3.7.1"  % "test"
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.7.1")
)

lazy val publishSettings = osgiSettings ++ Seq(
  exportPackage := Seq("doobie.*"),
  privatePackage := Seq(),
  dynamicImportPackage := Seq("*"),
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  homepage := Some(url("https://github.com/tpolecat/doobie")),
  pomIncludeRepository := Function.const(false),
  pomExtra := (
    <scm>
      <url>git@github.com:tpolecat/doobie.git</url>
      <connection>scm:git:git@github.com:tpolecat/doobie.git</connection>
    </scm>
    <developers>
      <developer>
        <id>tpolecat</id>
        <name>Rob Norris</name>
        <url>http://tpolecat.org</url>
      </developer>
    </developers>
  ),
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    ReleaseStep(action = Command.process("package", _)),
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    ReleaseStep(action = Command.process("publishSigned", _)),
    setNextVersion,
    commitNextVersion,
    ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
    pushChanges)
)

lazy val doobieSettings = buildSettings ++ commonSettings

lazy val doobie = project.in(file("."))
  .settings(doobieSettings)
  .settings(noPublishSettings)
  // .settings(unidocSettings)
  // .settings(unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(example, bench, docs))
  .dependsOn(core, core_cats) //, example, postgres, h2, hikari, specs2, docs, bench)
  .aggregate(core, core_cats) //, example, postgres, h2, hikari, specs2, docs, bench)
  .settings(freeGenSettings)
  .settings(
    freeGenDir := file("cartesius/core/src/main/scala/doobie/free"),
    freeGenClasses := {
      import java.sql._
      List[Class[_]](
        classOf[java.sql.NClob],
        classOf[java.sql.Blob],
        classOf[java.sql.Clob],
        classOf[java.sql.DatabaseMetaData],
        classOf[java.sql.Driver],
        classOf[java.sql.Ref],
        classOf[java.sql.SQLData],
        classOf[java.sql.SQLInput],
        classOf[java.sql.SQLOutput],
        classOf[java.sql.Connection],
        classOf[java.sql.Statement],
        classOf[java.sql.PreparedStatement],
        classOf[java.sql.CallableStatement],
        classOf[java.sql.ResultSet]
      )
    }
  )

def coreSettings(mod: String) = 
  doobieSettings  ++ 
  publishSettings ++ Seq(
    name := "doobie-" + mod,
    description := "Pure functional JDBC layer for Scala.",
    libraryDependencies ++= Seq(
      "org.scala-lang"    %  "scala-reflect"    % scalaVersion.value, // required for shapeless macros
      "com.chuusai"       %% "shapeless"        % "2.3.0"
    ),
    scalacOptions += "-Yno-predef",
    sourceGenerators in Compile += Def.task {
      val outDir = (sourceManaged in Compile).value / "doobie"
      val outFile = new File(outDir, "buildinfo.scala")
      outDir.mkdirs
      val v = version.value
      val t = System.currentTimeMillis
      IO.write(outFile,
        s"""|package doobie
            |
            |/** Auto-generated build information. */
            |object buildinfo {
            |  /** Current version of doobie ($v). */
            |  val version = "$v"
            |  /** Build date (${new java.util.Date(t)}). */
            |  val date    = new java.util.Date(${t}L)
            |}
            |""".stripMargin)
      Seq(outFile)
    }.taskValue
  )

lazy val core = project.in(file("modules/core"))
  .enablePlugins(SbtOsgi)
  .settings(
    cartesius(file("cartesius/core"), "scalaz"),
    coreSettings("core"),
    libraryDependencies ++= Seq(
      "org.scalaz"        %% "scalaz-core"      % "7.2.0",
      "org.scalaz"        %% "scalaz-effect"    % "7.2.0",
      "org.scalaz.stream" %% "scalaz-stream"    % "0.8a",
      "com.h2database"    %  "h2"               % "1.3.170" % "test"
    )
  )

lazy val core_cats = project.in(file("modules/core-cats"))
  .enablePlugins(SbtOsgi)
  .settings(
    cartesius(file("cartesius/core"), "cats"),
    coreSettings("core-cats"),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats" % "0.6.0",
      "org.postgresql" %  "postgresql"   % "9.4-1201-jdbc41"
    )
  )

// lazy val example = project.in(file("modules/example"))
//   .settings(doobieSettings)
//   .settings(libraryDependencies ++= Seq(
//       "com.h2database" %  "h2"         % "1.3.170",
//       "org.scalacheck" %% "scalacheck" % "1.13.0" % "test"
//     )
//   )
//   .settings(scalacOptions += "-deprecation")
//   .settings(noPublishSettings)
//   .dependsOn(core, postgres, specs2, hikari, h2)

// lazy val postgres = project.in(file("modules/postgresql"))
//   .enablePlugins(SbtOsgi)
//   .settings(name := "doobie-contrib-postgresql")
//   .settings(description := "PostgreSQL support for doobie.")
//   .settings(doobieSettings ++ publishSettings)
//   .settings(
//     libraryDependencies ++= Seq(
//       "org.postgresql" %  "postgresql"   % "9.4-1201-jdbc41",
//       "org.postgis"    %  "postgis-jdbc" % "1.3.3" exclude("org.postgis", "postgis-stubs")
//     )
//   )
//   .settings(
//     initialCommands := """
//       import scalaz._,Scalaz._
//       import scalaz.concurrent.Task
//       import doobie.imports._
//       import doobie.contrib.postgresql.pgtypes._
//       val xa: Transactor[Task] = DriverManagerTransactor[Task]("org.postgresql.Driver", "jdbc:postgresql:world", "postgres", "")
//       import xa.yolo._
//       import org.postgis._
//       import org.postgresql.util._
//       import org.postgresql.geometric._
//       """
//   )
//   .dependsOn(core)

// lazy val h2 = project.in(file("modules/h2"))
//   .enablePlugins(SbtOsgi)
//   .settings(name := "doobie-contrib-h2")
//   .settings(description := "H2 support for doobie.")
//   .settings(doobieSettings ++ publishSettings)
//   .settings(libraryDependencies += "com.h2database" % "h2"  % "1.3.170")
//   .dependsOn(core)

// lazy val hikari = project.in(file("modules/hikari"))
//   .enablePlugins(SbtOsgi)
//   .settings(name := "doobie-contrib-hikari")
//   .settings(description := "Hikari support for doobie.")
//   .settings(doobieSettings ++ publishSettings)
//   .settings(libraryDependencies += "com.zaxxer" % "HikariCP-java6" % "2.2.5")
//   .dependsOn(core)

// lazy val specs2 = project.in(file("modules/specs2"))
//   .enablePlugins(SbtOsgi)
//   .settings(name := "doobie-contrib-specs2")
//   .settings(description := "Specs2 support for doobie.")
//   .settings(doobieSettings ++ publishSettings)
//   .settings(libraryDependencies += "org.specs2" %% "specs2-core" % "3.7.1")
//   .dependsOn(core)

// lazy val docs = project.in(file("modules/doc"))
//   .settings(doobieSettings)
//   .settings(noPublishSettings)
//   .settings(tutSettings)
//   .settings(
//     initialCommands := """
//       import doobie.imports._, scalaz._, Scalaz._, scalaz.concurrent.Task
//       val xa = DriverManagerTransactor[Task](
//         "org.postgresql.Driver", "jdbc:postgresql:world", "postgres", ""
//       )
//       """,
//     ctut := {
//       val src = crossTarget.value / "tut"
//       val dst = file("../tpolecat.github.io/_doobie-" + version.value + "/")
//       if (!src.isDirectory) {
//         println("Input directory " + src + " not found.")
//       } else if (!dst.isDirectory) {
//         println("Output directory " + dst + " not found.")
//       } else {
//         println("Copying to " + dst.getPath)
//         val map = src.listFiles.filter(_.getName.endsWith(".md")).map(f => (f, new File(dst, f.getName)))
//         IO.copy(map, overwrite = true, preserveLastModified = false)
//       }
//     }
//   )
//   .settings(docSkipScala212Settings)
//   .dependsOn(core, postgres, specs2, hikari, h2)

// lazy val bench = project.in(file("modules/bench"))
//   .settings(doobieSettings)
//   .settings(noPublishSettings)
//   .dependsOn(core, postgres)


// Workaround to avoid cyclic dependency
// TODO remove after tut-core and argonaut for Scala 2.12 is released
lazy val tuut = taskKey[Seq[(File, String)]]("Temporary task to conditionally skip tut")

// Temporarily skip tut for Scala 2.12
// TODO remove after tut-core and argonaut for Scala 2.12 is released
lazy val docSkipScala212Settings = Seq(
  libraryDependencies ++= {
    if (scalaVersion.value startsWith "2.12") Nil
    else Seq("io.argonaut" %% "argonaut" % "6.2-M1")
  },
  tuut := Def.taskDyn {
    if (scalaVersion.value startsWith "2.12")
      Def.task(Seq.empty[(File, String)])
    else
      Def.task(tut.value)
  }.value
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

def macroParadise(v: String): List[ModuleID] =
  if (v.startsWith("2.10")) List(compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full))
  else Nil

lazy val ctut = taskKey[Unit]("Copy tut output to blog repo nearby.")

