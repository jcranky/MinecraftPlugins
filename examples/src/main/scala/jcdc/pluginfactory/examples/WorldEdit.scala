package jcdc.pluginfactory.examples

import jcdc.pluginfactory.{Command, CommandsPlugin, ListenersPlugin}
import org.bukkit.{Location, Material}
import org.bukkit.entity.Player
import Material._
import org.bukkit.block.Block

class WorldEdit extends ListenersPlugin with CommandsPlugin {

  val corners = collection.mutable.Map[Player, List[Location]]().withDefaultValue(Nil)

  val listeners = List(
    OnLeftClickBlock((p, e)  => if (p isHoldingA WOOD_AXE) { setFirstPos (p, e.loc); e.cancel }),
    OnRightClickBlock((p, e) => if (p isHoldingA WOOD_AXE) { setSecondPos(p, e.loc) })
  )

  val commands = List(
    Command(
      name = "wand", desc = "Get a WorldEdit wand.", body = noArgs(_.loc.dropItem(WOOD_AXE))
    ),
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
        for{
          (startBlock,n) <- p.world.fromX(p.loc).zip(fibs take i)
          towerBlock     <- startBlock.andBlocksAbove take n
        } towerBlock changeTo m
      }
    )
  )

  def cube(p:Player):Iterator[Block] = {
    def blocksBetween(loc1:Location, loc2: Location): Iterator[Block] = {
      val ((x1, y1, z1), (x2, y2, z2)) = (loc1.xyz, loc2.xyz)
      def range(i1: Int, i2: Int) = (if(i1 < i2) i1 to i2 else i2 to i1).iterator
      for (x <- range(x1,x2); y <- range(y1,y2); z <- range(z1,z2)) yield loc1.world(x,y,z)
    }
    corners.get(p).filter(_.size == 2).
      fold({p ! "Both corners must be set!"; Iterator[Block]()})(ls => blocksBetween(ls(0), ls(1)))
  }

  def setFirstPos(p:Player,loc: Location): Unit = {
    corners.update(p, List(loc))
    p ! (s"first corner set to: ${loc.xyz}")
  }

  def setSecondPos(p:Player,loc2: Location): Unit = corners(p) match {
    case loc1 :: _ =>
      corners.update(p, List(loc1, loc2))
      p ! s"second corner set to: ${loc2.xyz}"
    case Nil =>
      p ! "set corner one first! (with a left click)"
  }
}
