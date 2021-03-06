import sbt._
import Project.Setting
import Keys._
import java.io.File
import com.joshcough.minecraft.Plugin._
import sbtassembly.Plugin._
import AssemblyKeys._

object build extends Build {
  type Sett = Project.Setting[_]

  val projectUrl = "https://github.com/joshcough/MinecraftPlugins"

  lazy val standardSettings: Seq[Sett] = join(
    Defaults.defaultSettings,
    bintray.Plugin.bintraySettings,
    libDeps(
      "javax.servlet"      % "servlet-api" % "2.5"        % "provided->default",
      "org.scalacheck"    %% "scalacheck"  % "1.10.0"     % "test",
      "org.bukkit"         % "bukkit" % "1.7.2-R0.2"
    ),
    Seq[Sett](
      organization := "com.joshcough",
      version := "0.3.2",
      scalaVersion := "2.10.3",
      licenses <++= version(v => Seq("MIT" -> url(projectUrl + "/blob/%s/LICENSE".format(v)))),
      publishMavenStyle := true,
      resolvers += Resolver.sonatypeRepo("snapshots"),
      resolvers += ("Bukkit" at "http://repo.bukkit.org/content/repositories/releases"),
      traceLevel := 10
      //,logLevel := Level.Warn
    )
  )

  def copyPluginToBukkitSettings(meta: Option[String]): Seq[Sett] = Seq[Sett](
    // make publish local also copy jars to my bukkit server :)
    publishLocal <<= (packagedArtifacts, publishLocal) map { case (r, _) =>
      r collectFirst { case (Artifact(_,"jar","jar", m, _, _, name), f) if m == meta =>
        println("copying " + f.name + " to bukkit server")
        IO.copyFile(f, new File("bukkit/plugins/" + f.name))
      }
    }
  )

  // this is just a convenience project
  // for me to easily publish my most used plugins to my bukkit server.
  // > sbt 'project commonPlugins' publishLocal
  lazy val commonPlugins = Project(
    id = "commonPlugins",
    base = file(".commonPlugins"),
    settings = standardSettings,
    aggregate = Seq(
      scalaLibPlugin,
      ermineLibPlugin,
      core,
      erminecraft,
      MultiPlayerCommands,
      PluginCommander,
      WorldEdit
    )
  )

  // this is the main project, that builds all subprojects.
  // it doesnt contain any code itself.
  lazy val all = Project(
    id = "all",
    base = file("."),
    settings = standardSettings,
    aggregate = Seq(
      scalaLibPlugin,
      ermineLibPlugin,
      netlogoLibPlugin,
      //mineLang,
      coreJava,
      core,
      erminecraft,
      //netlogoPlugin,
      examplesJava,
      microExample,
      Arena,
      BanArrows,
      BlockChanger,
      BlockChangerGold,
      Farmer,
      GetOffMyLawn,
      God,
      LightningArrows,
      MultiPlayerCommands,
      NoRain,
      PluginCommander,
      Shock,
      Thor,
      TreeDelogger,
      WorldEdit,
      YellowBrickRoad,
      ZombieApocalypse
    )
  )

  // this project supplies the scala language classes.
  // it is needed in the bukkit plugins dir to run any scala plugins.
  lazy val scalaLibPlugin = Project(
    id = "scalaLibPlugin",
    base = file("scala/scala-lib-plugin"),
    settings = join(
      standardSettings,
      named("scala-minecraft-scala-library"),
      assemblySettings,
      copyPluginToBukkitSettings(Some("assembly"))
    )
  )

  // the core plugin library
  lazy val core = Project(
    id = "core",
    base = file("scala/core"),
    settings = join(
      standardSettings,
      copyPluginToBukkitSettings(None),
      named("scala-minecraft-plugin-api"),
      libDeps("org.scalacheck" %% "scalacheck" % "1.10.0" % "test")
    )
  )

  // a special example project...
  lazy val microExample = Project(id = "microexample", base = file("scala/microexample"))

  // a whole pile of example projects
  lazy val Arena               = exampleProject("Arena")
  lazy val BanArrows           = exampleProject("BanArrows")
  lazy val BlockChanger        = exampleProject("BlockChanger")
  lazy val BlockChangerGold    = exampleProject("BlockChangerGold")
  lazy val Farmer              = exampleProject("Farmer")
  lazy val GetOffMyLawn        = exampleProject("GetOffMyLawn")
  lazy val God                 = exampleProject("God")
  lazy val LightningArrows     = exampleProject("LightningArrows")
  lazy val MultiPlayerCommands = exampleProject("MultiPlayerCommands")
  lazy val NoRain              = exampleProject("NoRain")
  lazy val PluginCommander     = exampleProject("PluginCommander")
  lazy val Shock               = exampleProject("Shock")
  lazy val Thor                = exampleProject("Thor")
  lazy val TreeDelogger        = exampleProject("TreeDelogger")
  lazy val WorldEdit           = exampleProject("WorldEdit")
  lazy val YellowBrickRoad     = exampleProject("YellowBrickRoad")
  lazy val ZombieApocalypse    = exampleProject("ZombieApocalypse")

  def exampleProject(exampleProjectName: String) = {
    val pluginClassname = "com.joshcough.minecraft.examples." + exampleProjectName
    Project(
      id = exampleProjectName,
      base = file("scala/examples/" + exampleProjectName),
      settings = join(
        standardSettings,
        named(exampleProjectName),
        pluginYmlSettings(pluginClassname, "JoshCough"),
        copyPluginToBukkitSettings(None)
      ),
      dependencies = Seq(core)
    )
  }

  // ErmineCraft stuff below.
  val repl = InputKey[Unit]("repl", "Run the Ermine read-eval-print loop")
  val allUnmanagedResourceDirectories = SettingKey[Seq[File]](
    "all-unmanaged-resource-directories",
    "unmanaged-resource-directories, transitively."
  )
  /** Multiply a setting across Compile, Test, Runtime. */
  def compileTestRuntime[A](f: Configuration => Setting[A]): SettingsDefinition =
    seq(f(Compile), f(Test), f(Runtime))
  lazy val ermineFileSettings = Defaults.defaultSettings ++ Seq[SettingsDefinition](
     compileTestRuntime(sc => classpathConfiguration in sc := sc)
    ,mainClass in (Compile, run) := Some("com.clarifi.reporting.ermine.session.Console")
    ,compileTestRuntime(sco => allUnmanagedResourceDirectories in sco <<=
      Defaults.inDependencies(unmanagedResourceDirectories in sco, _ => Seq.empty)
        (_.reverse.flatten))
    // Usually, resources end up in the classpath by virtue of `compile'
    // copying them into target/scala-*/classes, and from there into jar.  But
    // we want in development p(1) I can edit an Ermine module in src
    // resources, hit reload, and it's seen. So we (harmlessly) patch the src resources
    // dirs in *before* the classes dirs, so they will win in the classloader
    // lookup.
    ,compileTestRuntime(sco =>
      fullClasspath in sco <<= (allUnmanagedResourceDirectories in sco, fullClasspath in sco) map {
        (urd, fc) => Attributed.blankSeq(urd) ++ fc
      })
  ) flatMap (_.settings)

  lazy val ermineSettings = join(
    standardSettings,
    ermineFileSettings,
    libDeps("com.clarifi" %% "ermine-legacy" % "0.1"),
    Seq[Sett](fullRunInputTask(repl, Compile, "com.clarifi.reporting.ermine.session.Console"))
  )

  lazy val erminecraft = Project(
    id = "erminecraft",
    base = file("ermine/erminecraft"),
    settings = join(ermineSettings, named("erminecraft-plugin-api"), copyPluginToBukkitSettings(None)),
    dependencies = Seq(core)
  )

  lazy val Zap = exampleErmineProject("Zap")

  def exampleErmineProject(exampleProjectName: String) = {
    val pluginClassname = "com.joshcough.minecraft.ermine.examples." + exampleProjectName
    Project(
      id = exampleProjectName,
      base = file("ermine/examples/" + exampleProjectName),
      settings = join(
        ermineSettings,
        named(exampleProjectName),
        pluginYmlSettings(pluginClassname, "JoshCough"),
        copyPluginToBukkitSettings(None),
        Seq[Setting[_]](resourceGenerators in Compile <+= (baseDirectory, resourceManaged in Compile) map { (baseDir, outDir) =>
          IO.createDirectory(outDir / "modules")
          IO.copyDirectory(baseDir / "modules", outDir / "modules")
          (outDir / "modules").listFiles.toSeq
        })
      ),
      dependencies = Seq(core, erminecraft)
    )
  }

  // this project supplies the ermine language classes, and classes for all of ermine's dependencies.
  // it is needed in the bukkit plugins dir to run any ermine plugins.
  lazy val ermineLibPlugin = Project(
    id = "ermineLibPlugin",
    base = file("ermine/ermine-lib-plugin"),
    settings = join(
      standardSettings,
      assemblySettings,
      copyPluginToBukkitSettings(Some("assembly")),
      named("erminecraft-ermine-library"),
      libDeps(
        "com.clarifi" %% "ermine-legacy"     % "0.1",
        "org.scalaz"  %% "scalaz-core"       % "7.0.2",
        "org.scalaz"  %% "scalaz-concurrent" % "7.0.2",
        "org.scalaz"  %% "scalaz-effect"     % "7.0.2",
        "org.scalaz"  %% "scalaz-iterv"      % "7.0.2",
        "log4j"       %  "log4j"             % "1.2.14"
      )
    )
  )

  // two relatively unimportant projects
  // that show how to do all this scala stuff in java.
  // or, how the bukkit api should have been written (in java).
  // this backports most of my interesting features from scala to java.
  lazy val coreJava = Project(
    id = "core-java",
    base = file("other/core-java"),
    settings = standardSettings ++ named("java-minecraft-plugin-api")
  )

  lazy val examplesJava = Project(
    id = "examplesJava",
    base = file("other/examples-java"),
    settings = standardSettings ++ named("JCDC Plugin Factory Java Examples"),
    dependencies = Seq(coreJava)
  )

  // minelang is a plugin that contains a language i wrote that is much like clojure
  // and allows people to easily write plugins without having to deploy lots of crap.
  // however, this has more or less been replaced by erminecraft.
  lazy val mineLang = Project(
    id = "mineLang",
    base = file("other/minelang"),
    settings = join(
      standardSettings,
      pluginYmlSettings("com.joshcough.minecraft.MineLangPlugin", "JoshCough"),
      named("MineLang"),
      libDeps(
        "org.scala-lang" % "jline"   % "2.10.2",
        "org.clojure"    % "clojure" % "1.4.0"
      )
    ),
    dependencies = Seq(core)
  )

  lazy val netlogoPlugin = Project(
    id = "netLogoPlugin",
    base = file("other/netlogo"),
    settings = join(
      standardSettings,
      copyPluginToBukkitSettings(None),
      pluginYmlSettings("com.joshcough.minecraft.NetLogoPlugin", "JoshCough"),
      libDeps(
        "org.nlogo" % "NetLogoHeadless"  % "5.1.0-M2" from "http://ccl.northwestern.edu/netlogo/5.1.0-M2/NetLogoHeadless.jar",
        "de.kumpelblase2" % "remoteentities" % "1.6"  from "http://dev.bukkit.org/media/files/700/586/remoteentities-1.6.jar"
      ),
      Seq[Sett](resolvers ++= Seq("remoteentities-repo" at "http://repo.infinityblade.de/remoteentities/releases"))
    ),
    dependencies = Seq(core)
  )

  lazy val netlogoLibPlugin = Project(
    id = "netLogoLibPlugin",
    base = file("other/netlogo-lib-plugin"),
    settings = join(
      standardSettings,
      assemblySettings,
      named("netlogo-lib-plugin"),
      copyPluginToBukkitSettings(Some("assembly")),
      libDeps(
        "org.nlogo" % "NetLogoHeadless"  % "5.1.0-M2" from "http://ccl.northwestern.edu/netlogo/5.1.0-M2/NetLogoHeadless.jar",
        "asm" % "asm-all" % "3.3.1",
        "org.picocontainer" % "picocontainer" % "2.13.6"
      )
    )
  )

  def join(settings: Seq[Sett]*): Seq[Sett] = settings.flatten
  def named(pname: String): Seq[Sett] = Seq[Sett](name := pname)
  def libDeps(libDeps: sbt.ModuleID*) = Seq[Sett](libraryDependencies ++= libDeps)
}
