# Abandoned #

This project has been abandoned. I haven't used it or maintained it in years. I recommend using [Metals](https://scalameta.org/metals/) (which is much more feature-rich) instead.

Any issues created in this repository will probably be ignored. Feel free to fork this project in accordance with its [license](LICENSE).

# SBT ctags #

SBT ctags is an SBT plugin that will generate ctags for your Scala project.

It unzips the source jars for your project dependencies and generates ctags for these dependency sources in addition to the Scala/Java source of your project itself.

# Release notes #

Just want to find out what's new in the most recent version? Check out the [release notes](https://github.com/ceedubs/sbt-ctags/tree/master/release-notes).

# Setting it up #

## Adding the plugin dependency ##

Add the following to `~/.sbt/1.0/plugins/plugins.sbt` (or wherever you like to configure your global SBT settings):
```scala
resolvers ++= Seq(
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

addSbtPlugin("net.ceedubs" %% "sbt-ctags" % "0.3.0")
```

Alternatively you can add this plugin to individual SBT projects by adding those lines to `<project-dir>/project/plugins.sbt`.

## Configuring ctags ##

By default, the plugin assumes you have a `ctags` executable on your path that is syntax-compatible with [Exuberant Ctags](http://ctags.sourceforge.net/). Some systems will already have a version of `ctags` installed that isn't compatible with this syntax. If you get errors and you are on Mac OS X, you might want to try `brew install ctags`.

If you'd rather go the advanced route and customize the way tags are generated, see [Configuration](#configuration).

By default ctags will not index scala files.  One such `~/.ctags` configuration which enables indexing might look like:

```shell
--langdef=scala
--langmap=scala:.scala
--regex-scala=/^[ \t]*((abstract|final|sealed|implicit|lazy)[ \t]*)*(private|protected)?[ \t]*class[ \t]+([a-zA-Z0-9_]+)/\4/c,classes/
--regex-scala=/^[ \t]*((abstract|final|sealed|implicit|lazy)[ \t]*)*(private|protected)?[ \t]*object[ \t]+([a-zA-Z0-9_]+)/\4/c,objects/
--regex-scala=/^[ \t]*((abstract|final|sealed|implicit|lazy)[ \t]*)*(private|protected)?[ \t]*case class[ \t]+([a-zA-Z0-9_]+)/\4/c,case classes/
--regex-scala=/^[ \t]*((abstract|final|sealed|implicit|lazy)[ \t]*)*(private|protected)?[ \t]*case object[ \t]+([a-zA-Z0-9_]+)/\4/c,case objects/
--regex-scala=/^[ \t]*((abstract|final|sealed|implicit|lazy)[ \t]*)*(private|protected)?[ \t]*trait[ \t]+([a-zA-Z0-9_]+)/\4/t,traits/
--regex-scala=/^[ \t]*type[ \t]+([a-zA-Z0-9_]+)/\1/T,types/
--regex-scala=/^[ \t]*((abstract|final|sealed|implicit|lazy)[ \t]*)*def[ \t]+([a-zA-Z0-9_]+)/\3/m,methods/
--regex-scala=/^[ \t]*((abstract|final|sealed|implicit|lazy)[ \t]*)*val[ \t]+([a-zA-Z0-9_]+)/\3/l,constants/
--regex-scala=/^[ \t]*((abstract|final|sealed|implicit|lazy)[ \t]*)*var[ \t]+([a-zA-Z0-9_]+)/\3/l,variables/
--regex-scala=/^[ \t]*package[ \t]+([a-zA-Z0-9_.]+)/\1/p,packages/
```

This was taken from the excellent blog post [Editing Scala with vim](http://leonard.io/blog/2013/04/editing-scala-with-vim/) by Leonard Ehrenfried.

# Using the plugin #

To generate ctags for a project, run `sbt gen-ctags` from the project's root directory. This will unzip dependency source jars into `target/sbt-ctags-dep-srcs` (configurable) and create a tags file (default location is `.tags` inside the root dir).

# Using the tags #

Your text editor of choice that supports ctags will need to be configured to look for the generated `.tags` file (the file name may be different depending on your plugin configuration). I use vim and this is accomplished by adding `set tags=./.tags,.tags,./tags,tags` to my `.vimrc`.

The Vim Tips Wiki has some useful information for [Browsing programs with tags](http://vim.wikia.com/wiki/Browsing_programs_with_tags)

Emacswiki has some useful information for [navigating using tags](http://www.emacswiki.org/emacs/EmacsTags)

# Configuration #

There are a number of configurable settings declared in [SbtCtags.scala](https://github.com/ceedubs/sbt-ctags/blob/master/src/main/scala/net/ceedubs/sbtctags/SbtCtags.scala). The best way to get to know what the configuration options are is probably to browse the `CtagsKeys` object within that file.

I would suggest putting your sbt-ctags configuration in `~/.sbt/1.0/sbt-ctags.sbt` or something similar.

## Languages ##

By default, sbt-ctags generates tag files for both Java and Scala source. If you prefer to generate tags only for Scala source, you can add the following to your sbt-ctags file:

```scala
import net.ceedubs.sbtctags.CtagsKeys

CtagsKeys.ctagsParams ~= (_.copy(languages = Seq("scala")))
```

## Emacs ##

By default, the tags file is named `.tags` and is created at the project root through an external call `ctags` with Exuberant Ctags syntax.

If you want the tags file to be named `TAGS` and to be in Emacs format, you could set the following to your sbt-ctags file:

```scala
CtagsKeys.ctagsParams ~= (default => default.copy(tagFileName = "TAGS", extraArgs = "-e" +: default.extraArgs))
```

## Preventing tag file generation

If you just want to use this plugin to unzip dependency sources so you can generate ctags outside of SBT, you could set `net.ceedubs.sbtctags.CtagsKeys.ctagsGeneration := { _ => () }` to make the generation of ctags a noop.

## Relative paths

If you need/want to have relative paths in your `.tags` file, set the following to your sbt-ctags file:

```scala
CtagsKeys.ctagsParams ~= (_.copy(
  useRelativePaths = true))
```

# Disclaimers and warnings #
Be very careful if you are going to change the `dependencySrcUnzipDir` setting. This directory is cleared every time the `gen-ctags` task runs.

This plugin makes some assumptions about your system and how you want tags to be generated. Hopefully the customizable settings make it easy for you to use to your liking. If not, I encourage you to send a pull request to make this plugin more flexible/useful/robust.
