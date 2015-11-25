import java.util.Date

import actors.Messages.{DevDiscover, Unsubscribe, Subscribe}
import akka.actor.{ActorRef, Actor}
import builders.EmbeddedNode
import spray.json.{JsNumber, JsObject}
import scala.concurrent.duration._

/**
 * Created by max on 11/24/15.
 */
object Agent extends App {

  EmbeddedNode.builder().withHWGate[ClusterState].start()

}

case object Tick

class ClusterState extends Actor {

  @volatile var listeners = Set.empty[ActorRef]

  import context.dispatcher

  context.system.scheduler.schedule(1 second, 1 second, self, Tick)

  override def receive : Receive = {

    case Subscribe(listener) =>
      listener ! DevDiscover
      println("listener added " + listener)
      listeners += listener

    case Unsubscribe(listener) =>
      listeners -= listener

    case Tick => listeners foreach sendTelemetry

  }

  def sendTelemetry(dst:ActorRef) = {
    val msg = JsObject(Map("when" -> JsNumber(new Date().getTime)))
    dst ! Array(msg)
  }

}
