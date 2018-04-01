package net.ceedubs.sbtctags

import java.io.File
import sys.process._

object Proc {

  def execProcess(command: String, cwd: Option[File]): Int = {
    Process(command, cwd).!
  }
}

