package net.ceedubs.sbtctags

import sbt._
import sbt.std.TaskStreams
import java.io.File

final case class CtagsProject(srcDirs: Seq[File], buildStructure: BuildStructure, logger: Logger)

object SbtCtags extends Plugin {

  object CtagsKeys {
    val dependencySrcUnzipDir = SettingKey[File]("ctags-dependency-src-unzip-dir", "The directory into which the dependency source jars should be unzipped. WARNING: when gen-ctags is run, this directory will be deleted, so DO NOT change this setting to an important directory.")
    val ctagsGeneration = SettingKey[CtagsProject => Unit]("ctags-generation", "A function that takes some data about the project and creates a tag file. By default it makes an external ctags call.")
    val ctagsSrcDirs = SettingKey[Seq[File]]("ctags-src-dirs", "The directories upon which ctags should be run")

    val genCtags = TaskKey[Unit]("gen-ctags", "Unzip source jars of dependencies and generate ctags files for them")
  }

  override lazy val projectSettings = Seq(
      CtagsKeys.dependencySrcUnzipDir <<= Keys.target (_ / "sbt-ctags-dep-srcs"),
      CtagsKeys.ctagsGeneration := { project =>
        val dirArgs = project.srcDirs.map(_.getAbsolutePath)
        val ctagsCmd = s"ctags --exclude=.git --exclude=log --languages=scala -f .tags -R ${dirArgs.mkString(" ")}"
        Process(ctagsCmd, Some(new File(project.buildStructure.root)), Seq.empty: _*).!
      },
      CtagsKeys.ctagsSrcDirs <<= (Keys.scalaSource in Compile, CtagsKeys.dependencySrcUnzipDir){ (srcDir, depSrcDir) =>
        Seq(srcDir, depSrcDir)
      },
      CtagsKeys.genCtags <<= (Keys.state, CtagsKeys.dependencySrcUnzipDir, CtagsKeys.ctagsGeneration, CtagsKeys.ctagsSrcDirs, Keys.streams) map genCtags
  )

  def genCtags(state: State, dependencySrcUnzipDir: File, ctagsGeneration: CtagsProject => Unit, ctagsSrcDirs: Seq[File], streams: TaskStreams[_]) {
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
          val existingCtagsSrcDirs = ctagsSrcDirs.filter(_.exists)
          streams.log.debug(s"existing ctags src dirs: $existingCtagsSrcDirs")
          ctagsGeneration(CtagsProject(existingCtagsSrcDirs, buildStruct, streams.log))
          state
        case x =>
          streams.log.error(s"error trying to update classifiers to find source jars: $x")
          state
      }
    })
  }

}
