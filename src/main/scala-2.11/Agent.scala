import java.net.InetAddress
import java.util.Date

import actors.Messages.{Start, DevDiscover, Unsubscribe, Subscribe}
import akka.actor.{Props, ActorLogging, ActorRef, Actor}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberUp, InitialStateAsEvents, MemberEvent, UnreachableMember}
import builders.EmbeddedNode
import com.sun.media.jfxmediaimpl.HostUtils
import spray.json.{JsString, JsNumber, JsObject}
import util.Telemetry
import utils.{NetUtils, MemberUtils}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Created by max on 11/24/15.
 */
object Agent extends App {

  EmbeddedNode.builder().withHWGate[ClusterState].start()

}

case object Tick

case class RichPing(time: Long, source: String, dest: String, pingTo: Int, pingFrom: Int, pingTotal: Int) {
  def toJs = JsObject (
    Map(
      "time" -> JsNumber(time),
      "source" -> JsString(source),
      "dest" -> JsString(dest),
      "pingTo" -> JsNumber(pingTo),
      "pingFrom" -> JsNumber(pingFrom),
      "pingTotal" -> JsNumber(pingTotal))
  )
}

class ClusterState extends Actor with Telemetry with ActorLogging {

  val cluster = Cluster(context.system)

  override def preStart() = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
    context.actorOf(Props[TcpPingResponder], "ping-responder")
  }

  @volatile var listeners = Set.empty[ActorRef]

  import context.dispatcher

  context.system.scheduler.schedule(1 second, 1 second, self, Tick)

  override def receive : Receive = {

    case MemberUp(member) =>
      if (member.address != cluster.selfAddress) {
        println(s"creating pinger for $member [$self]")
        val address = member.address.host.map(InetAddress.getByName(_)).getOrElse(NetUtils.localHost)
        context.actorOf(Props(classOf[TcpPinger], address), s"pinger-for-${address.getHostAddress}") ! Start
      }
    case Subscribe(listener) =>
      listener ! DevDiscover
      println("listener added " + listener)
      listeners += listener

    case Unsubscribe(listener) =>
      listeners -= listener

    case Tick => listeners foreach sendTelemetry

    case rp @ RichPing(time, source, dest, pingTo, pingFrom, pingTotal) =>
      println(s"Received RichPing: $rp")
      listeners foreach (_ ! Array(rp.toJs))

  }

  def sendTelemetry(dst:ActorRef) = {
    nodeTelemetry match {
      case Success(tel) => dst ! Array(tel)
      case Failure(th) => log.error(th, "Can't obtain telemetry")
    }
  }

}