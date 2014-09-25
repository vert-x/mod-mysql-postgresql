import java.nio.charset.StandardCharsets

import sbt.Keys._
import sbt._

object Variables {
  val org = "io.vertx"
  val name = "mod-mysql-postgresql"
  val version = "0.3.1"
  val scalaVersion = "2.10.4"
  val crossScalaVersions = Seq("2.10.4", "2.11.2")
  val description = "Fully async MySQL / PostgreSQL module for Vert.x"

  val vertxVersion = "2.1.2"
  val testtoolsVersion = "2.0.3-final"
  val hamcrestVersion = "1.3"
  val junitInterfaceVersion = "0.10"
  val vertxLangScalaVersion = "1.1.0-M1"
  val asyncDriverVersion = "0.2.15"

  val pomExtra =
    <inceptionYear>2013</inceptionYear>
      <url>http://vertx.io</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <connection>scm:git:git://github.com/vert-x/mod-mysql-postgresql.git</connection>
        <developerConnection>scm:git:ssh://git@github.com/vert-x/mod-mysql-postgresql.git</developerConnection>
        <url>https://github.com/vert-x/mod-mysql-postgresql</url>
      </scm>
      <developers>
        <developer>
          <id>Narigo</id>
          <name>Joern Bernhardt</name>
          <email>jb@campudus.com</email>
        </developer>
        <developer>
          <id>Zwergal</id>
          <name>Max Stemplinger</name>
          <email>ms@campudus.com</email>
        </developer>
      </developers>

}

object Dependencies {

  import Variables._

  val test = List(
    "io.vertx" % "testtools" % testtoolsVersion % "test",
    "org.hamcrest" % "hamcrest-library" % hamcrestVersion % "test",
    "com.novocode" % "junit-interface" % junitInterfaceVersion % "test"
  )

  val compile = List(
    "io.vertx" % "vertx-core" % vertxVersion % "provided",
    "io.vertx" % "vertx-platform" % vertxVersion % "provided",
    "io.vertx" %% "lang-scala" % vertxLangScalaVersion % "provided",
    "com.github.mauricio" %% "postgresql-async" % asyncDriverVersion % "compile" excludeAll(
      ExclusionRule(organization = "org.scala-lang"),
      ExclusionRule(organization = "io.netty")
      ),
    "com.github.mauricio" %% "mysql-async" % asyncDriverVersion % "compile" excludeAll(
      ExclusionRule(organization = "org.scala-lang"),
      ExclusionRule(organization = "io.netty")
      )
  ) ::: test

}

object VertxScalaBuild extends Build {

  lazy val project = Project(
    "project",
    file("."),
    settings = Seq(
      organization := Variables.org,
      name := Variables.name,
      version := Variables.version,
      scalaVersion := Variables.scalaVersion,
      crossScalaVersions := Variables.crossScalaVersions,
      description := Variables.description,
      copyModTask,
      zipModTask,
      libraryDependencies := Dependencies.compile,
      //      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _),
      // Fork JVM to allow Scala in-flight compilation tests to load the Scala interpreter
      fork in Test := true,
      // Vert.x tests are not designed to run in paralell
      parallelExecution in Test := false,
      // Adjust test system properties so that scripts are found
      javaOptions in Test += "-Dvertx.test.resources=src/test/scripts",
      // Adjust test modules directory
      javaOptions in Test += "-Dvertx.mods=target/mods",
      // Set the module name for tests
      javaOptions in Test += s"-Dvertx.modulename=${organization.value}~${name.value}_${getMajor(scalaVersion.value)}~${version.value}",
      resourceGenerators in Compile += Def.task {
        val file = (resourceManaged in Compile).value / "langs.properties"
        val contents = s"scala=io.vertx~lang-scala_${getMajor(scalaVersion.value)}~${Variables.vertxLangScalaVersion}:org.vertx.scala.platform.impl.ScalaVerticleFactory\n.scala=scala\n"
        IO.write(file, contents, StandardCharsets.UTF_8)
        Seq(file)
      }.taskValue,
      copyMod <<= copyMod dependsOn (copyResources in Compile),
      (test in Test) <<= (test in Test) dependsOn copyMod,
      zipMod <<= zipMod dependsOn copyMod,
      (packageBin in Compile) <<= (packageBin in Compile) dependsOn copyMod,
      // Publishing settings
      publishMavenStyle := true,
      pomIncludeRepository := { _ => false},
      publishTo <<= version { (v: String) =>
        val sonatype = "https://oss.sonatype.org/"
        if (v.trim.endsWith("SNAPSHOT"))
          Some("Sonatype Snapshots" at sonatype + "content/repositories/snapshots")
        else
          Some("Sonatype Releases" at sonatype + "service/local/staging/deploy/maven2")
      },
      pomExtra := Variables.pomExtra
    )
  ).settings(addArtifact(Artifact(Variables.name, "zip", "zip", "mod"), zipMod).settings: _*)

  val copyMod = TaskKey[Unit]("copy-mod", "Assemble the module into the local mods directory")
  val zipMod = TaskKey[File]("zip-mod", "Package the module .zip file")

  lazy val copyModTask = copyMod := {
    implicit val log = streams.value.log
    val modOwner = organization.value
    val modName = name.value
    val modVersion = version.value
    val scalaMajor = getMajor(scalaVersion.value)
    val moduleName = s"$modOwner~${modName}_$scalaMajor~$modVersion"
    log.info("Create module " + moduleName)
    val moduleDir = target.value / "mods" / moduleName
    createDirectory(moduleDir)
    copyDirectory((classDirectory in Compile).value, moduleDir)
    copyDirectory((resourceDirectory in Compile).value, moduleDir)
    val libDir = moduleDir / "lib"
    createDirectory(libDir)
    // Get the runtime classpath to get all dependencies except provided ones
    (managedClasspath in Runtime).value foreach { classpathEntry =>
      copyClasspathFile(classpathEntry, libDir)
    }
  }

  lazy val zipModTask = zipMod := {
    implicit val log = streams.value.log
    val modOwner = organization.value
    val modName = name.value
    val modVersion = version.value
    val scalaMajor = getMajor(scalaVersion.value)
    val moduleName = s"$modOwner~${modName}_$scalaMajor~$modVersion"
    log.info("Create ZIP module " + moduleName)
    val moduleDir = target.value / "mods" / moduleName
    val zipFile = target.value / "zips" / s"$moduleName.zip"
    IO.zip(allSubpaths(moduleDir), zipFile)
    zipFile
  }

  private def getMajor(version: String): String = version.substring(0, version.lastIndexOf('.'))

  private def createDirectory(dir: File)(implicit log: Logger): Unit = {
    log.debug(s"Create directory $dir")
    IO.createDirectory(dir)
  }

  private def copyDirectory(source: File, target: File)(implicit log: Logger): Unit = {
    log.debug(s"Copy $source to $target")
    IO.copyDirectory(source, target, overwrite = true)
  }

  private def copyClasspathFile(cpEntry: Attributed[File], libDir: File)(implicit log: Logger): Unit = {
    val sourceFile = cpEntry.data
    val targetFile = libDir / sourceFile.getName
    log.debug(s"Copy $sourceFile to $targetFile")
    IO.copyFile(sourceFile, targetFile)
  }

}
