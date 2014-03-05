package net.ceedubs.sbtctags

import sbt._
import java.io.File

object SbtCtags extends Plugin {

  object CtagsKeys {
    val dependencySrcUnzipDir = SettingKey[File]("dependency-src-unzip-dir", "The directory into which the dependency source jars should be unzipped")
    val ctagsExecutable = SettingKey[Option[String]]("ctags-executable", "The command to run to generate ctags")
    val ctagsSrcDirs = SettingKey[Seq[File]]("ctags-src-dirs", "The directories upon which ctags should be run")

    val genCtags = TaskKey[Unit]("gen-ctags", "Unzip source jars of dependencies and generate ctags files for them")
  }

  override lazy val projectSettings = Seq(
      CtagsKeys.dependencySrcUnzipDir <<= Keys.target (_ / "sbt-ctags-dep-srcs"),
      CtagsKeys.ctagsExecutable := Some("ctags -R --exclude=.git --exclude=log --languages=scala -f .tags"),
      CtagsKeys.ctagsSrcDirs <<= (Keys.scalaSource in Compile, CtagsKeys.dependencySrcUnzipDir){ (srcDir, depSrcDir) =>
        val res = Seq(srcDir, depSrcDir)
        println(s"ctags src dirs: $res")
        res
      },
      CtagsKeys.genCtags <<= (Keys.state, CtagsKeys.dependencySrcUnzipDir, CtagsKeys.ctagsExecutable, CtagsKeys.ctagsSrcDirs) map genCtags)

  def genCtags(state: State, dependencySrcUnzipDir: File, ctagsExecutable: Option[String], ctagsSrcDirs: Seq[File]) {
    // TODO this could be pretty bad if someone overrides the install dir with an important dir
    val extracted = Project.extract(state)
    val buildStruct = extracted.structure
    EvaluateTask(buildStruct, Keys.updateClassifiers, state, extracted.currentRef).fold(state)(Function.tupled { (state, result) =>
      result match {
        case Value(updateReport) =>
          sbt.IO.delete(dependencySrcUnzipDir)
          for {
            configuration <- updateReport.configurations
            module <- configuration.modules
            sourceArtifact <- module.artifacts if sourceArtifact._1.`type` == "src"
          } {
            val (artifact, file) = sourceArtifact
            sbt.IO.unzip(file, dependencySrcUnzipDir, "*.scala")
          }
          ctagsExecutable foreach { ctagsCmd =>
            val existingCtagsSrcDirs = ctagsSrcDirs.filter(_.exists)
            println(s"existing ctags src dirs: $existingCtagsSrcDirs")
            val cmdWithDirs = s"$ctagsCmd ${existingCtagsSrcDirs.map(_.getAbsolutePath).mkString(" ")}"
            Process(cmdWithDirs, Some(new File(buildStruct.root)), Seq.empty: _*).!
          }
          state
        case _ =>
          println("error trying to update classifiers to find source jars")
          state
      }
    })
  }

}
