package jcdc.pluginfactory.examples

import jcdc.pluginfactory.{CommandsPluginV2, SingleClassDBPlugin}
import org.bukkit.{Location, World}
import org.bukkit.entity.Player
import scala.collection.JavaConversions._

class WarpPlugin extends CommandsPluginV2 with SingleClassDBPlugin[Warp]{

  val dbClass = classOf[Warp]

  val commands = Map(
    "warps"       -> noArgs(p => warpsFor(p).foreach { w => p.sendMessage(w.toString) }),
    "warp"        -> args(warp){ case p ~ w => p.teleport(w.location(p.getWorld)) },
    "delete-warp" -> args(warp){ case p ~ w => db.delete(w); p ! ("deleted warp: " + w.name) },
    "delete-all"  -> opOnly(noArgs(p => db.foreach { w => p ! ("deleting: " + w); db.delete(w) })),
    "set-warp"    -> args(warp||anyString){
      case p ~ Left(w)  =>
        // TODO: can i use an update here?
        // TODO: well, i need to make a case class out of warp
        // then i need to put the annotations on its constructor params... have to test that.
        db.delete(w)
        db.insert(createWarp(w.name, p))
        p ! ("updated warp: " + w.name)
      case p ~ Right(name) => db.insert(createWarp(name, p)); p ! "created warp: " + name
    }
  )

  def warp = new ArgParser[Warp] {
    def apply(p:Player, args: List[String]) = args match {
      case Nil => Failure("expected warp name")
      case x :: xs => warpsFor(p).filter(_.name == x).headOption.
        fold(Failure("no such warp: " + x):ParseResult[Warp])(Success(_, xs))
    }
  }

  def createWarp(n: String, p: Player): Warp = {
    val w = new Warp(); w.name = n; w.player = p.name; w.x = p.x; w.y = p.y; w.z = p.z; w
  }

  def warpsFor(p: Player) = db.query.where.ieq("player", p.getName).findList()
}

@javax.persistence.Entity
class Warp {
  @javax.persistence.Id
  @javax.persistence.GeneratedValue
  var id = 0
  var name: String = ""
  var player = ""
  var x = 0d; var y = 0d; var z = 0d
  def location(world: World) = new Location(world, x, y, z)
  override def toString = player + "." + name + (x, y, z)
}
