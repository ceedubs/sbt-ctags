package net.ceedubs.sbtctags

import java.io.File

import sbt._
import sbt.std.TaskStreams

final case class CtagsParams(
  executable: String,
  excludes: Seq[String],
  languages: Seq[String],
  tagFileName: String,
  useRelativePaths: Boolean,
  extraArgs: Seq[String])


object CtagsKeys {
  val dependencySrcUnzipDir = SettingKey[File]("ctags-dependency-src-unzip-dir", "The directory into which the dependency source jars should be unzipped. WARNING: when gen-ctags is run, this directory will be deleted, so DO NOT change this setting to an important directory.")
  val ctagsParams = SettingKey[CtagsParams]("ctags-params", "Parameters ctag generation")
  val ctagsSrcDirs = SettingKey[Seq[File]]("ctags-src-dirs", "The directories upon which ctags should be run")
  val ctagsSrcFileFilter = SettingKey[NameFilter]("ctags-src-file-filter", "A filter for dependency files that should be considered")
  val ctagsGeneration = SettingKey[CtagsGenerationContext => Unit]("ctags-generation", "A function that takes a context object and creates a tag file. By default it makes an external ctags call.")

  val genCtags = TaskKey[Unit]("gen-ctags", "Unzip source jars of dependencies and generate ctags files for them")
}


object SbtCtags extends AutoPlugin {

  object autoImport {
    val SbtCtagsKeys = CtagsKeys
  }

  import autoImport._

  override def requires = plugins.JvmPlugin

  override def trigger = allRequirements

  override lazy val projectSettings = Seq(
      CtagsKeys.dependencySrcUnzipDir := (Keys.target.value / "sbt-ctags-dep-srcs"),

    CtagsKeys.ctagsParams in ThisBuild := defaultCtagsParams,

      CtagsKeys.ctagsSrcFileFilter :=
        CtagsKeys.ctagsParams.value.languages.
          foldLeft(NameFilter.fnToNameFilter(_ => false))((filter, lang) =>
            filter | GlobFilter(s"*.$lang")),

      CtagsKeys.ctagsSrcDirs :=
        Seq((Keys.scalaSource in Compile).value,
            (Keys.scalaSource in Test).value,
            (Keys.javaSource in Compile).value,
            (Keys.javaSource in Test).value,
            CtagsKeys.dependencySrcUnzipDir.value),

      CtagsKeys.ctagsGeneration in ThisBuild := defaultCtagsGeneration(Keys.baseDirectory.value) _,

      CtagsKeys.genCtags := genCtags(Keys.thisProjectRef.value,
                                     Keys.state.value,
                                     CtagsKeys.dependencySrcUnzipDir.value,
                                     CtagsKeys.ctagsParams.value,
                                     CtagsKeys.ctagsSrcFileFilter.value,
                                     CtagsKeys.ctagsGeneration.value,
                                     CtagsKeys.ctagsSrcDirs.value,
                                     Keys.streams.value)
  )

  val defaultCtagsParams: CtagsParams = CtagsParams(
        executable = "ctags",
        excludes = Seq("log"),
        languages = Seq("scala", "java"),
        tagFileName = "tags",
        useRelativePaths = false,
        extraArgs = Seq.empty)

  def defaultCtagsGeneration(base: File)(context: CtagsGenerationContext) {
    val ctagsParams = context.ctagsParams

    val toPath: File => String = file =>
      if(ctagsParams.useRelativePaths)
        file.relativeTo(base).fold(file.getAbsolutePath)(_.getPath)
      else file.getAbsolutePath

    val dirArgs = context.srcDirs.map(toPath).mkString(" ")
    val excludeArgs = ctagsParams.excludes.map(x => s"--exclude=$x").mkString(" ")
    val languagesArgs = if (ctagsParams.languages.isEmpty) "" else s"--languages=${ctagsParams.languages.mkString(",")}"
    val extraArgs = {
      val args =
        if(ctagsParams.useRelativePaths)
          "--tag-relative=yes" +: ctagsParams.extraArgs
        else ctagsParams.extraArgs
      args.mkString(" ")
    }
    // will look something like "ctags --exclude=.git --exclude=log --languages=scala -f .tags -R src/main/scala target/sbt-ctags-dep-srcs"
    val ctagsCmd = s"${ctagsParams.executable} $excludeArgs $languagesArgs -f ${ctagsParams.tagFileName} $extraArgs -R $dirArgs"
    context.log.info(s"Running this command to generate ctags: $ctagsCmd")
    Proc.execProcess(ctagsCmd, Some(new File(context.buildStructure.root)))
  }

  def genCtags(projectRef: ProjectRef, state: State, dependencySrcUnzipDir: File, ctagsParams: CtagsParams, srcFileFilter: NameFilter, ctagsGeneration: CtagsGenerationContext => Unit, ctagsSrcDirs: Seq[File], streams: TaskStreams[_]): Unit = {
    // TODO this could be pretty bad if someone overrides the install dir with an important dir
    val extracted = Project.extract(state)
    val buildStruct = extracted.structure
    val log = streams.log
    val project = Project.getProject(projectRef, buildStruct)

    log.info(s"Processing project named: ${project.get.id}")
    EvaluateTask(buildStruct, Keys.updateClassifiers, state, projectRef).fold(state)(Function.tupled { (state, result) =>
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

          val tagPath = s"${project.get.base.getAbsolutePath}/.${ctagsParams.tagFileName}"
          val projectCtagParams = ctagsParams.copy(tagFileName = tagPath)
          ctagsGeneration(CtagsGenerationContext(projectCtagParams, existingCtagsSrcDirs, buildStruct, streams.log))
          state
        case x =>
          log.error(s"error trying to update classifiers to find source jars: $x")
          state
      }
    })
  }
}
