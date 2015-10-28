package net.ceedubs.sbtctags

import sbt._
import Keys._
import sbt.std.TaskStreams
import java.io.File

final case class CtagsGenerationContext(
  ctagsParams: CtagsParams,
  srcDirs: Seq[File],
  buildStructure: BuildStructure,
  log: Logger)

final case class CtagsParams(
  executable: String,
  excludes: Seq[String],
  languages: Seq[String],
  tagFileName: String,
  extraArgs: Seq[String])

object SbtCtags extends AutoPlugin {

  object autoImport {
    val dependencySrcUnzipDir = SettingKey[File]("ctags-dependency-src-unzip-dir", "The directory into which the dependency source jars should be unzipped. WARNING: when gen-ctags is run, this directory will be deleted, so DO NOT change this setting to an important directory.")
    val ctagsParams = SettingKey[CtagsParams]("ctags-params", "Parameters ctag generation")
    val ctagsSrcDirs = SettingKey[Seq[File]]("ctags-src-dirs", "The directories upon which ctags should be run")
    val ctagsSrcFileFilter = SettingKey[NameFilter]("ctags-src-file-filter", "A filter for dependency files that should be considered")
    val ctagsGeneration = SettingKey[CtagsGenerationContext => Unit]("ctags-generation", "A function that takes a context object and creates a tag file. By default it makes an external ctags call.")

    val genCtags = TaskKey[Unit]("gen-ctags", "Unzip source jars of dependencies and generate ctags files for them")
  }

  import autoImport._

  override def trigger = allRequirements

  override val projectSettings = Seq(
      dependencySrcUnzipDir <<= target (_ / "sbt-ctags-dep-srcs"),

      ctagsParams := defaultCtagsParams,

      ctagsSrcFileFilter <<= ctagsParams(_.languages.foldLeft(NameFilter.fnToNameFilter(_ => false))((filter, lang) => filter | GlobFilter(s"*.$lang"))),

      ctagsSrcDirs <<= (scalaSource in Compile, scalaSource in Test, javaSource in Compile, javaSource in Test, dependencySrcUnzipDir){ (srcDir, testDir, javaSrcDir, javaTestDir, depSrcDir) =>
        Seq(srcDir, testDir, javaSrcDir, javaTestDir, depSrcDir)
      },

      ctagsGeneration := CtagsGen.defaultCtagsGeneration,

      genCtags <<= (state, dependencySrcUnzipDir, ctagsParams, ctagsSrcFileFilter, ctagsGeneration, ctagsSrcDirs, streams).map(CtagsGen.generateCtags)
  )

  val defaultCtagsParams = CtagsParams(
        executable = "ctags",
        excludes = Seq("log"),
        languages = Seq("scala", "java"),
        tagFileName = ".tags",
        extraArgs = Seq.empty)
}

object CtagsGen {

  def defaultCtagsGeneration(context: CtagsGenerationContext) {
    val ctagsParams = context.ctagsParams
    val dirArgs = context.srcDirs.map(_.getAbsolutePath).mkString(" ")
    val excludeArgs = ctagsParams.excludes.map(x => s"--exclude=$x").mkString(" ")
    val languagesArgs = if (ctagsParams.languages.isEmpty) "" else s"--languages=${ctagsParams.languages.mkString(",")}"
    val extraArgs = ctagsParams.extraArgs.mkString(" ")
    // will look something like "ctags --exclude=.git --exclude=log --languages=scala -f .tags -R src/main/scala target/sbt-ctags-dep-srcs"
    val ctagsCmd = s"${ctagsParams.executable} $excludeArgs $languagesArgs -f ${ctagsParams.tagFileName} $extraArgs -R $dirArgs"
    context.log.info(s"running this command to generate ctags: $ctagsCmd")
    Process(ctagsCmd, Some(new File(context.buildStructure.root)), Seq.empty: _*).!
  }

  def generateCtags(state: State, dependencySrcUnzipDir: File, ctagsParams: CtagsParams, srcFileFilter: NameFilter, ctagsGeneration: CtagsGenerationContext => Unit, ctagsSrcDirs: Seq[File], streams: TaskStreams[_]) {
    val extracted = Project.extract(state)
    val buildStruct = extracted.structure
    val log = streams.log
    log.info(s"Grabbing all dependency source jars. This may take a while if you don't have them in your Ivy cache")
    EvaluateTask(buildStruct, updateClassifiers, state, extracted.currentRef).fold(state)(Function.tupled { (state, result) =>
      result match {
        case Value(updateReport) =>
          log.info(s"Clearing $dependencySrcUnzipDir")
          // TODO this could be pretty bad if someone overrides the install dir with an important dir
          sbt.IO.delete(dependencySrcUnzipDir)
          log.info(s"Unzipping dependency source jars into $dependencySrcUnzipDir")
          for {
            configuration <- updateReport.configurations
            module <- configuration.modules
            sourceArtifact <- module.artifacts if sourceArtifact._1.`type` == "src"
          } {
            val (artifact, file) = sourceArtifact
            sbt.IO.unzip(file, dependencySrcUnzipDir, srcFileFilter)
          }
          val existingCtagsSrcDirs = ctagsSrcDirs.filter(_.exists)
          log.debug(s"existing ctags src dirs: $existingCtagsSrcDirs")
          log.info(s"Generating tag file")
          ctagsGeneration(CtagsGenerationContext(ctagsParams, existingCtagsSrcDirs, buildStruct, streams.log))
          state
        case x =>
          log.error(s"error trying to update classifiers to find source jars: $x")
          state
      }
    })
  }
}
