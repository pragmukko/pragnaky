import java.net.InetAddress
import java.util.Date

import actors.SwarmDiscovery
import util.{Messages, Telemetry}
import Messages.Register
import actors.Messages.{Start, DevDiscover, Unsubscribe, Subscribe}
import akka.actor.{Props, ActorLogging, ActorRef, Actor}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberUp, InitialStateAsEvents, MemberEvent, UnreachableMember}
import builders.EmbeddedNode
import spray.json.{JsString, JsNumber, JsObject}
import util.Telemetry
import utils.{ConfigProvider, NetUtils, MemberUtils}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Created by max on 11/24/15.
 */
object Agent extends App {

  EmbeddedNode.builder().withHWGate[ClusterState].start()

}

case class PingTick(hosts:List[InetAddress])

case class PingHost(host: InetAddress)

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

class ClusterState extends Actor with Telemetry with ActorLogging with ConfigProvider /*with SwarmDiscovery*/ {

  val cluster = Cluster(context.system)

  override def preStart() = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])

    new TcpPingResponderNioSync().start()
    println("ping responder started")
  }

  @volatile var listeners = Set.empty[ActorRef]

  import context.dispatcher

  //discoverAndJoin()

  context.system.scheduler.schedule(1 second, 1 second, self, PingTick(Nil))

  val pinger = new TcpPingerNioSync(self)

  override def receive : Receive = {

    /*
    case MemberUp(member) =>
      println(s"member $member is Up")
      if (member.address != cluster.selfAddress) {
        println(s"creating pinger for $member [$self]")
        val address = member.address.host.map(InetAddress.getByName).getOrElse(NetUtils.localHost)
      }*/

    case Subscribe(listener) =>
      listener ! DevDiscover
      println("listener added " + listener)
      listeners += listener
      listener ! Register(netGateway)

    case Unsubscribe(listener) =>
      listeners -= listener

    case PingTick(hosts) =>
      listeners foreach sendTelemetry
      //pingNextKnownHost(hosts)
      cluster.state.members.map(_.address.host).filterNot(_ == cluster.selfAddress.host).flatMap(_.map(InetAddress.getByName)).foreach(self ! PingHost(_))

    case PingHost(host) => pinger.ping(host)

    case rp @ RichPing(time, source, dest, pingTo, pingFrom, pingTotal) =>
      println(s"Received RichPing: $rp")
      //listeners foreach (_ ! Array(rp.toJs))
      listeners foreach (_ ! rp )

  }

  def pingNextKnownHost(hosts:List[InetAddress]) = {
    val other = hosts match {
      case head :: rest =>
        pinger.ping(head)
        rest

      case Nil =>
        cluster.state.members.map(_.address.host).filterNot(_ == cluster.selfAddress.host).flatMap(_.map(InetAddress.getByName)).toList
    }
    context.system.scheduler.schedule(1 second, 1 second, self, PingTick(other))
  }

  def sendTelemetry(dst:ActorRef) = {
    nodeTelemetry match {
      case Success(tel) => dst ! Array(tel)
      case Failure(th) => log.error(th, "Can't obtain telemetry")
    }
  }

}
