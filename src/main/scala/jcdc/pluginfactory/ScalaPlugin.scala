package jcdc.pluginfactory

import java.util.logging.Logger
import org.bukkit.entity.Player
import org.bukkit.event.{Listener, Event}
import org.bukkit.event.entity.{EntityDamageEvent, EntityDamageByEntityEvent, EntityListener}
import org.bukkit.command.{Command, CommandSender}
import org.bukkit.block.Block
import org.bukkit.{ChatColor, Location}

trait ScalaPluginPredef {
  def blocksAbove(b:Block): Stream[Block] = {
    val ba = new Location(b.getWorld, b.getX.toDouble, b.getY.toDouble + 1, b.getZ.toDouble).getBlock
    ba #:: blocksAbove(ba)
  }
}

trait ScalaPlugin extends org.bukkit.plugin.java.JavaPlugin with ScalaPluginPredef {
  val log = Logger.getLogger("Minecraft")
  def name = this.getDescription.getName
  def ban(player:Player, reason:String){
    player.setBanned(true)
    player.kickPlayer("banned: " + reason)
  }
  def onEnable(){ logInfo("enabled!") }
  def onDisable(){ logInfo("disabled!") }
  def registerListener(eventType:Event.Type, listener:Listener){
    this.getServer.getPluginManager.registerEvent(eventType, listener, Event.Priority.Normal, this)
  }
  def logInfo(message:String) { log.info("["+name+"] - " + message) }
  def logError(e:Throwable){
    log.log(java.util.logging.Level.SEVERE, "["+name+"] - " + e.getMessage, e)
  }
}

trait MultiListenerPlugin extends ScalaPlugin {
  val listeners:List[(Event.Type, Listener)]
  override def onEnable(){ super.onEnable(); listeners.foreach((registerListener _).tupled) }
}

trait ListenerPlugin extends ScalaPlugin {
  val eventType:Event.Type; val listener:Listener
  override def onEnable(){ super.onEnable(); registerListener(eventType, listener) }
}

trait SingleCommandPlugin extends ScalaPlugin {
  val command: String
  val commandHandler: CommandHandler
  override def onCommand(sender:CommandSender, cmd:Command, commandLabel:String, args:Array[String]) = {
    if(cmd.getName.equalsIgnoreCase(command)) commandHandler.handle(sender.asInstanceOf[Player], cmd, args)
    true
  }
}

trait CommandHandler{
  def handle(player: Player, cmd:Command, args:Array[String])
  def sendUsage(player:Player, cmd:Command) =
    player.sendMessage(ChatColor.RED + "usage: " + cmd.getUsage)
}

trait OpOnly extends CommandHandler{
  abstract override def handle(player: Player, cmd: Command, args: Array[String]) = {
    if(player.isOp) super.handle(player, cmd, args)
    else player.sendMessage(ChatColor.RED + "Nice try. You must be an op to run /" + cmd.getName)
  }
}

trait ManyCommandsPlugin extends ScalaPlugin {
  val commands: Map[String, CommandHandler]
  private def lowers: Map[String, CommandHandler] = commands.map{ case (k,v) => (k.toLowerCase, v)}
  override def onEnable(){
    super.onEnable()
    lowers.keys.foreach{ k => logInfo("["+name+"] command: " + k) }
  }
  override def onCommand(sender:CommandSender, cmd:Command, commandLabel:String, args:Array[String]) = {
    lowers.get(cmd.getName.toLowerCase).foreach(_.handle(sender.asInstanceOf[Player], cmd, args))
    true
  }
}

trait EntityDamageByEntityListener extends EntityListener with ScalaPluginPredef {
  override def onEntityDamage(event:EntityDamageEvent){
    if(event.isInstanceOf[EntityDamageByEntityEvent])
      onEntityDamageByEntity(event.asInstanceOf[EntityDamageByEntityEvent])
  }
  def onEntityDamageByEntity(e:EntityDamageByEntityEvent)
}