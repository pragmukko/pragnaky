import java.net.InetAddress
import java.util.Date

import actors.SwarmDiscovery
import ping.{RichPing, PingHost, PingTick}
import util.{ConfigGenId, Messages, Telemetry}
import Messages.Register
import actors.Messages._
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import builders.EmbeddedNode
import spray.json.{JsString, JsNumber, JsObject}
import utils.{ConfigProvider, NetUtils, MemberUtils}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Try, Failure, Success}

/**
 * Created by max on 11/24/15.
 */
object EmbeddedNodeWithId extends EmbeddedNode with ConfigGenId

object Agent extends App {

  EmbeddedNodeWithId.builder().withHWGate[ClusterState].start()

}

case object TelemetryTick

class ClusterState extends Actor with Telemetry with ActorLogging with ConfigProvider with ConfigGenId with SwarmDiscovery {

  val cluster = Cluster(context.system)

  override def preStart() = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
  }

  //@volatile var listeners = Set.empty[ActorRef]

  import context.dispatcher

  //discoverAndJoin()
  startDiscovery()

  private val pingerInterval: FiniteDuration = config.getDuration("pinger.interval")

  //context.system.scheduler.schedule(1 second, pingerInterval, self, PingTick(Nil))

  val terminator = context.system.scheduler.scheduleOnce(10 seconds) {
    log.warning("Couldn't find cluster, shutting down")
    cluster.down(cluster.selfAddress)
    context.system.terminate()
  }

  val pinger = new TcpPingerNioSync(self)

  override def receive : Receive = {

    case DiscoveredSeedAddresses(seedAddresses: Array[Address]) =>
      terminator.cancel()
      cluster.joinSeedNodes(seedAddresses.toList)
      SwarmDiscovery.startResponder(context.system, config)
      println("discovery responder started")
      new TcpPingResponderNioSync().start()
      println("ping responder started")

      context.system.scheduler.schedule(1 second, pingerInterval, self, TelemetryTick)
      context.system.scheduler.scheduleOnce(2 second, self, PingTick(Nil))

      cluster.registerOnMemberRemoved(clusterLeave)

      context become processing(Nil)

    case x => println(s"AgentInit-Unknown: $x")
  }

  def processing(listeners: List[ActorRef]): Receive = {
    case Subscribe(listener) =>
      listener ! DevDiscover
      println("listener added " + listener)
      //listeners += listener
      listener ! Register(netGateway)
      context become processing(listener :: listeners)

    case Unsubscribe(listener) =>
      //listeners -= listener
      context become processing(listeners.filterNot(_ == listener))

    case TelemetryTick =>
      listeners foreach sendTelemetry

    case PingTick(hosts) =>
      hosts match {
        case Nil => context.system.scheduler.scheduleOnce(1 second, self, PingTick(knownHosts.map(InetAddress.getByName)))

        case head :: rest =>
          Future {
            Try { pinger.ping(head) } recover { case th => log.error(th, "Error on ping " + head) }
            context.system.scheduler.scheduleOnce(1 second, self, PingTick(rest))
          }
      }

    case rp @ RichPing(time, source, dest, pingTo, pingFrom, pingTotal) =>
      println(s"Received RichPing: $rp")

      listeners foreach (_ ! rp )

    case MemberUp(member) =>
      //println(s"member UP: $member\n- leader: ${cluster.state.leader}\n- members: ${cluster.state.members}")
    case MemberRemoved(member, _) =>
      //println(s"member REMOVED: $member\n- leader: ${cluster.state.leader}\n- members: ${cluster.state.members}")
      if (cluster.state.leader.contains(cluster.selfAddress) && cluster.state.members.size == 1 && cluster.state.members.map(_.address).contains(cluster.selfAddress)) {
        println(s"it seems that node is disconnected from the cluster - shutting down...")
        cluster.down(cluster.selfAddress)
        //context.system.terminate()
      }
    case x => println(s"Agent-Unknown: $x")
  }

  def knownHosts = {
    cluster.state.members.map(_.address.host).filterNot(_ == cluster.selfAddress.host).flatten.toList
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

  implicit def asFiniteDuration(d: java.time.Duration): FiniteDuration = Duration.fromNanos(d.toNanos)

  private def clusterLeave() {
    println(s"Member removed or cluster is unavailable, shutting down...")
    import scala.concurrent.Await
    // exit JVM when ActorSystem has been terminated
    context.system.registerOnTermination(System.exit(0))
    // shut down ActorSystem
    context.system.terminate()

    // In case ActorSystem shutdown takes longer than 10 seconds,
    // exit the JVM forcefully anyway.
    // We must spawn a separate thread to not block current thread,
    // since that would have blocked the shutdown of the ActorSystem.
    new Thread {
      override def run(): Unit = {
        if (Try(Await.ready(context.system.whenTerminated, 10.seconds)).isFailure)
          System.exit(-1)
      }
    }.start()
  }
}
