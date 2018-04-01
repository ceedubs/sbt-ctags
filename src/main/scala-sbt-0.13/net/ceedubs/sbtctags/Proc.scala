package net.ceedubs.sbtctags

import sbt._

object Proc {

  def execProcess(command: String, cwd: Option[File]): Int = {
    Process(command, cwd).!
  }
}

