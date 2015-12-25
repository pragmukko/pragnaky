import java.net.InetAddress

import spray.json.JsValue
import akka.actor._
import akka.agent.Agent
import scala.concurrent.duration._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.ImplicitMaterializer
import util.Telemetry

import scala.util.{Failure, Success}


object RestAgent extends App with PingerConfigProvider {
  val system = ActorSystem()
  val agent = system.actorOf(Props[RestAgent], s"rest-actor.${PingerNetUtils.localHost.getHostAddress}")
}

class RestAgent extends Actor with ActorLogging with PingerConfigProvider with ImplicitMaterializer with Telemetry  {
  import akka.pattern.pipe
  import akka.util.ByteString
  import akka.stream.scaladsl.Sink
  import spray.json._

  implicit val executionContext = context.dispatcher
  val http = Http(context.system)
  val knownHosts = Agent(Set.empty[String])
  override def preStart() = {
    new TcpPingResponderNioSync().start()
    println("ping responder started in REST agent")
  }
  val pinger = new TcpPingerNioSync(self)

  private val pingerInterval: FiniteDuration = config.getDuration("pinger.interval")

  context.system.scheduler.schedule(1 second, pingerInterval, self, PingTick(Nil))

  override def receive : Receive = {
    case PingTick(hosts) =>
      sendTelemetry()
      knownHosts().map(InetAddress.getByName).foreach(self ! PingHost(_))

    case PingHost(host) => context.actorOf(Props[PingerActor]) ! (pinger, host)

    case rp @ RichPing(time, source, dest, pingTo, pingFrom, pingTotal) =>
      println(s"Received RichPing: $rp")
      sendJs("latency", rp.toJs)

    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      entity.dataBytes.runFold(ByteString(""))(_ ++ _) onComplete {
        case Success(x: ByteString) =>
          //println(s"Got response with hosts: ${x.utf8String}")
          //TODO YI add exception handling
          val hosts = x.utf8String.parseJson.asInstanceOf[JsArray].elements.collect {
            case s: JsString if s.value != PingerNetUtils.localHost.getHostAddress && s.value != "127.0.0.1" => s.value
          }.toSet
          knownHosts send {_=>hosts}
        case _ => knownHosts send {_=>Set.empty[String]}
      }
    case HttpResponse(code, _, _, _) =>
      log.info("Request failed, response code: " + code)
      knownHosts send {_=>Set.empty[String]}
  }

  def sendTelemetry() = {
    nodeTelemetry match {
      case Success(tel) => sendJs("telemetry", tel)
      case Failure(th) => log.error(th, "Can't obtain telemetry")
    }
  }

  def sendJs(path: String, js: JsValue) = {
    val uri = s"http://${config.getString("pinger.rest.host")}:${config.getInt("pinger.rest.port")}/$path"
    http.singleRequest(HttpRequest(uri = uri, method = HttpMethods.POST, entity = HttpEntity(ContentTypes.`application/json`, js.compactPrint))).pipeTo(self)
  }

  implicit def asFiniteDuration(d: java.time.Duration): FiniteDuration = Duration.fromNanos(d.toNanos)
}

class PingerActor extends Actor {
  import scala.concurrent.{ Future, blocking }
  implicit val exc = context.dispatcher
  def receive = {
    case (pinger: Pinger, host: InetAddress) =>
      Future {
        blocking {
          pinger.ping(host)
        }
      }.onComplete(_ => self ! PoisonPill)


  }
}