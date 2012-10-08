package jcdc.pluginfactory.examples

import scala.collection.JavaConversions._
import jcdc.pluginfactory._
import org.bukkit.{Location, Material}
import org.bukkit.ChatColor._
import org.bukkit.entity.Player
import Material._
import java.io.File
import scala.io.Source

class WorldEdit extends ListenersPlugin
  with CommandsPlugin with SingleClassDBPlugin[Script] {

  val dbClass = classOf[Script]

  val corners = collection.mutable.Map[Player, List[Location]]().withDefaultValue(Nil)

  val listeners = List(
    OnLeftClickBlock((p, e)  => if(p isHoldingA WOOD_AXE) { setFirstPos (p, e.loc); e.cancel }),
    OnRightClickBlock((p, e) => if(p isHoldingA WOOD_AXE) { setSecondPos(p, e.loc) })
  )

  val commands = List(
    Command("test-script", "run the test script", noArgs(WorldEditInterp.apply(_, testScript))),
    Command("code-book-example", "get a 'code book' example", args(anyString.?){ case (p, title) =>
      p.inventory addItem Book(author = p, title, pages =
        """
         ((change grass diamond_block)
          (change dirt  gold_block)
          (change stone iron_block))
        """.trim
      )
    }),
    Command("run-book", "run the code in a book", noArgs(p =>
      ScriptRunner.runBook(p, Book.fromHand(p)))
    ),
    Command("make-script", "build a script", args(anyString ~ slurp){ case (p, title ~ code) =>
      val script = createScript(p, title, code)
      p ! s"$script"
      db.insert(script)
    }),
    Command("show-script", "show the code in a script", args(anyString){ case (p, title) =>
      db.firstWhere(Map("player" -> p.name, "title" -> title)).
        fold(p ! s"unknown script: $title")(s => p ! s"$s")
    }),
    Command("show-scripts", "show the code in a script", noArgs(p =>
      db.findAll.foreach(s => p ! s"$s")
    )),
    Command("run-script", "run the code in a script", args(anyString){ case (p, title) =>
      db.firstWhere(Map("player" -> p.name, "title" -> title)).
        fold(p ! s"unknown script: $title")(s => ScriptRunner.runScript(p, s))
    }),
    Command("goto", "Teleport!", args(location){ case (you, loc) => you teleport loc(you.world) }),
    Command("wand", "Get a WorldEdit wand.", noArgs(_.loc.dropItem(WOOD_AXE))),
    Command("pos1", "Set the first position",  args(location.?){ case (p, loc) =>
      setFirstPos(p, loc.fold(p.loc)(_(p.world)))
    }),
    Command("pos2", "Set the second position",  args(location.?){ case (p, loc) =>
      setSecondPos(p, loc.fold(p.loc)(_(p.world)))
    }),
    Command("cube-to",  "Set both positions",  args(location ~ location.?){
      case (p, loc1 ~ loc2) =>
        setFirstPos (p, loc1(p.world))
        setSecondPos(p, loc2.fold(p.loc)(_(p.world)))
    }),
    Command("between",  "Set both positions",  args(location ~ "-" ~ location){
      case (p, loc1 ~ _ ~ loc2) =>
        setFirstPos (p, loc1(p.world))
        setSecondPos(p, loc2(p.world))
        p.teleport(loc1(p.world))
    }),
    Command("erase", "Set all the selected blocks to air.", noArgs(cube(_).eraseAll)),
    Command(
      name = "set", desc = "Set all the selected blocks to the given material type.",
      body = args(material){ case (p, m) => for(b <- cube(p)) b changeTo m }
    ),
    Command(
      name = "change",
      desc = "Change all the selected blocks of the first material type to the second material type.",
      body = args(material ~ material){
        case (p, oldM ~ newM) => for(b <- cube(p); if(b is oldM)) b changeTo newM
      }
    ),
    Command(
      name = "find",
      desc = "Checks if your cube contains any of the given material, and tells where.",
      body = args(material){ case (p, m) =>
        cube(p).find(_ is m).fold(
          s"No $m found in your cube!")(b => s"$m found at ${b.loc.xyz}")
      }
    ),
    Command(
      name = "fib-tower",
      desc = "create a tower from the fib numbers",
      body = args(int ~ material){ case (p, i ~ m) =>
        lazy val fibs: Stream[Int] = 0 #:: 1 #:: fibs.zip(fibs.tail).map{case (i,j) => i+j}
        for {
          (startBlock,n) <- p.world.fromX(p.loc).zip(fibs take i)
          towerBlock     <- startBlock.andBlocksAbove take n
        } towerBlock changeTo m
      }
    ),
    Command(
      name = "walls",
      desc = "Create walls with the given material type.",
      body = args(material) { case (p, m) => cube(p).walls.foreach(_ changeTo m) }
    ),
    Command(
      name = "empty-tower",
      desc = "Create walls and floor with the given material type, and set everything inside to air.",
      body = args(material) { case (p, m) =>
        val c = cube(p)
        for(b <- cube(p)) if (c.onWall(b) or c.onFloor(b)) b changeTo m else b.erase
      }
    ),
    Command(
      name = "dig",
      desc = "Dig",
      body = args(oddNum ~ int) { case (p, radius ~ depth) =>
        val b = radius / 2
        val (x, y, z) = p.loc.xyzd
        Cube(p.world(x + b, y, z + b), p.world(x - b, y - depth, z - b)).eraseAll
      }
    )
  )

  def cube(p:Player): Cube = {
    corners.get(p).filter(_.size == 2).
      fold({p ! "Both corners must be set!"; Cube(p.world(0,0,0),p.world(0,0,0))})(ls =>
        Cube(ls(0), ls(1))
    )
  }

  def setFirstPos(p:Player,loc: Location): Unit = {
    corners.update(p, List(loc))
    p ! s"first corner set to: ${loc.xyz}"
  }

  def setSecondPos(p:Player,loc2: Location): Unit = corners(p) match {
    case loc1 :: _ =>
      corners.update(p, List(loc1, loc2))
      p ! s"second corner set to: ${loc2.xyz}"
    case Nil =>
      p ! "set corner one first! (with a left click)"
  }

  object ScriptRunner{
    def run(p:Player, lines:Seq[String]): Unit = for {
      commandAndArgs <- lines.map(_.trim).filter(_.nonEmpty)
      x      = commandAndArgs.split(" ").map(_.trim).filter(_.nonEmpty)
      cmd    = x.head
      args   = x.tail
    } runCommand(p, cmd, args)
    def runScript(p:Player, script:Script): Unit = run(p, script.commands)
    def runBook(p:Player, b:Book): Unit =
      run(p, b.pages.flatMap(_.split("\n").map(_.trim).filter(_.nonEmpty)))
  }

  def createScript(p: Player, title:String, commands:String): Script = {
    val s = new Script(); s.player = p.name; s.title = title; s.commandsString = commands; s
  }

  implicit class RichPlayerParser(p:Player){
    def parse(code:String): List[WENode] = io.Reader.read(code) match {
      case x :: xs => (x :: xs) map parseExpr
      case _ => sys error s"bad code: $code"
    }
    def parseExpr(a:Any): WENode = a match {
      case List('goto, loc)       => Goto(parseLoc(loc))
      case List('set, m)          => SetMaterial(parseMaterial(m))
      case List('change, m1, m2)  => Change(parseMaterial(m1), parseMaterial(m2))
      case List('pos1, loc)       => Pos1(parseLoc(loc))
      case List('pos2, loc)       => Pos2(parseLoc(loc))
      case List('corners, l1, l2) => Corners(Cube(parseLoc(l1), parseLoc(l2)))
      case List('walls, m)        => SetWalls(parseMaterial(m))
      case List('floor, m)        => SetFloor(parseMaterial(m))
      case _      => sys error s"bad expression: $a"
    }
    def parseLoc(a:Any): Location = a match {
      case 'origin     => p.world.getHighestBlockAt(p.world(0,4,0))
      case 'XYZ        => p.loc
      case List(x,y,z) => new Location(p.world, parseSingleCoor(x),parseSingleCoor(y),parseSingleCoor(z))
      case _           => sys error  s"bad location: $a"
    }
    def parseSingleCoor(a:Any):Int = a match {
      case i: Int => i
      case 'X     => p.x
      case 'Y     => p.y
      case 'Z     => p.z
      case 'MAXY  => 255
      case 'MINY  => 0
      case List('+, x, y) => parseSingleCoor(x) + parseSingleCoor(y)
      case List('-, x, y) => parseSingleCoor(x) - parseSingleCoor(y)
      case _      => sys error s"bad coordinate: $a"
    }
    def parseMaterial(a:Any) = material(a.toString.drop(1)).get
  }

  trait WENode
  case class Corners(c:Cube)  extends WENode
  case class Goto(l:Location) extends WENode
  case class Pos1(l:Location) extends WENode
  case class Pos2(l:Location) extends WENode
  case class SetMaterial(m:Material) extends WENode
  case class Change(m1:Material, m2:Material) extends WENode
  case class SetWalls(m:Material) extends WENode
  case class SetFloor(m:Material) extends WENode

  object WorldEditInterp{
    def apply(p:Player, w:WENode): Unit = w match {
      case Goto(l) => p teleport l
      case Pos1(l) => setFirstPos(p, l)
      case Pos2(l) => setSecondPos(p, l)
      case Corners(Cube(l1, l2)) => { setFirstPos (p, l1); setSecondPos(p, l2) }
      case SetMaterial(m) => cube(p).foreach(_ changeTo m)
      case Change(oldM, newM) => for(b <- cube(p); if(b is oldM)) b changeTo newM
      case SetWalls(m) => cube(p).walls.foreach(_ changeTo m)
      case SetFloor(m) => cube(p).floor.foreach(_ changeTo m)
    }
    def apply(p:Player, nodes:List[WENode]): Unit = nodes.foreach(apply(p, _))
    def apply(p:Player, code:String): Unit = attempt(p, { println(code); apply(p, p.parse(code)) })
    def apply(p:Player, commands:TraversableOnce[String]): Unit = apply(p, commands.mkString(" "))
    def apply(p:Player, f:File): Unit = attempt(p, apply(p, Source.fromFile(f).getLines))
  }

  val testScript =
    """
     ((goto origin)
      (corners ((+ X 20) (+ Y 50) (+ Z 20)) ((- X 20) Y (- Z 20)))
      (floor stone)
      (walls brick)
     )
    """.stripMargin.trim
}

import javax.persistence._
import scala.beans.BeanProperty

@Entity
class Script {
  @Id @GeneratedValue @BeanProperty var id = 0
  @BeanProperty var player = ""
  @BeanProperty var title: String = ""
  @BeanProperty var commandsString:String = ""
  def commands = commandsString.split(";").map(_.trim).filter(_.nonEmpty)
  override def toString = s"$player.$title \n[${commands.mkString("\n")}]"
}
