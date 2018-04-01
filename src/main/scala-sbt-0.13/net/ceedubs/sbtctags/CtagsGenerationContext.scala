package net.ceedubs.sbtctags

import sbt._
import java.io.File

final case class CtagsGenerationContext(
  ctagsParams: CtagsParams,
  srcDirs: Seq[File],
  buildStructure: BuildStructure,
  log: Logger)
