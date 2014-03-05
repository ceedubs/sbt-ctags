# SBT ctags #
SBT ctags is an SBT plugin that will generate ctags for your Scala project.

It unzips the source jars for your project dependencies and generates ctags for these dependency sources in addition to the Scala source of your project itself.

# Adding the dependency #
Add the following to `~/.sbt/0.13/plugins/plugins.sbt` (or wherever you like to configure your global SBT settings):
```scala
resolvers ++= Seq(
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

addSbtPlugin("net.ceedubs" %% "sbt-ctags" % "0.0.1-SNAPSHOT")
```

Alternatively you can add this plugin to individual SBT projects by adding those lines to `<project-dir>/project/plugins.sbt`.

# Using the plugin #

To generate ctags for a project, run `sbt gen-ctags` from the project's root directory. This will unzip dependency source jars into `target/sbt-ctags-dep-srcs` (configurable) and create a tags file (default location is `.tags` inside the root dir).

By default, the plugin assumes you have a `ctags` executable on your path that is syntax-compatible with [Exuberant Ctags](http://ctags.sourceforge.net/). If that's not the case or you would like to customize the way tags are generated, see [Configuration](#configuration)

# Using the tags #
Your text editor of choice that supports ctags will need to be configured to look for the generated `.tags` file. I use vim and this is accomplished by adding `set tags=./.tags,.tags,./tags,tags` to my `.vimrc`.

The Vim Tips Wiki has some useful information for [Browsing programs with tags](http://vim.wikia.com/wiki/Browsing_programs_with_tags)

# Configuration #
There are a number of configurable settings declared in [SbtCtags.scala](https://github.com/ceedubs/sbt-ctags/blob/master/src/main/scala/net/ceedubs/sbtctags/SbtCtags.scala). The best way to get to know what the configuration options are is probably to browse the `CtagsKeys` object within that file.

By default, the tags file is named `.tags` and is created at the project root through an external call `ctags` with Exuberant Ctags syntax. Since this represents a lot of assumptions about your system setup, this is a likely setting to be customized. For example, if you just want to use this plugin to unzip dependency sources so you can generate ctags outside of SBT, you could set `net.ceedubs.sbtctags.CtagsKeys.ctagsGeneration := { _ => () }` to make the generation of ctags a noop.

# Disclaimers and warnings #
Be very careful if you are going to change the `dependencySrcUnzipDir` setting. This directory is cleared every time the `gen-ctags` task runs.

This plugin makes some assumptions about your system and how you want tags to be generated. Hopefully the customizable settings make it easy for you to use to your liking. If not, I encourage you to send a pull request to make this plugin more flexible/useful/robust.

Currently I don't think this plugin handles projects with multiple modules well. I think multiple modules could be supported fairly easily, but I haven't needed this feature yet. Again, pull requests are encouraged!
