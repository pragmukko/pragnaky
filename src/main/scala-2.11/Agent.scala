import java.util.Date

import actors.Messages.{DevDiscover, Unsubscribe, Subscribe}
import akka.actor.{ActorLogging, ActorRef, Actor}
import builders.EmbeddedNode
import spray.json.{JsNumber, JsObject}
import util.Telemetry
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Created by max on 11/24/15.
 */
object Agent extends App {

  EmbeddedNode.builder().withHWGate[ClusterState].start()

}

case object Tick

class ClusterState extends Actor with Telemetry with ActorLogging {

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
    nodeTelemetry match {
      case Success(tel) => dst ! Array(tel)
      case Failure(th) => log.error(th, "Can't obtain telemetry")
    }
  }

}
