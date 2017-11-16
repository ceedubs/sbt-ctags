package net.ceedubs.sbtctags

import sbt.Logger
import sbt.internal.BuildStructure
import java.io.File

final case class CtagsGenerationContext(
  ctagsParams: CtagsParams,
  srcDirs: Seq[File],
  buildStructure: BuildStructure,
  log: Logger)
