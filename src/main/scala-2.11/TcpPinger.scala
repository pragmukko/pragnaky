import java.net.{InetAddress, InetSocketAddress}
import java.nio.ByteBuffer

import actors.Messages.Start
import akka.actor._
import akka.io.{Tcp, IO}
import akka.io.Tcp.Register
import akka.util.ByteString
import utils.ConfigProvider

/**
 * Created by yishchuk on 27.11.2015.
 */
class TcpPinger(host: InetAddress) extends Actor with ConfigProvider with ActorLogging {
  import context.system
  import akka.io.Tcp
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global


  def receive = {
    case Start =>
      IO(Tcp) ! Tcp.Connect(new InetSocketAddress(host, config.getInt("pinger.port")),  options = Tcp.SO.KeepAlive(true) :: Tcp.SO.TcpNoDelay(true) :: Nil)
      context become receiveConnection(sender())
  }
  def receiveConnection(replyTo: ActorRef): Receive = {
    case Tcp.Connected(remote, local) =>
      sender() ! Register(self)
      val localSender = sender()
      log.debug(s"connected: $remote -> $local, socket: $localSender")

     // val isa = new InetSocketAddress(broadcast, config.getInt("discovery.udp-port"))
      val scheduled = context.system.scheduler.schedule(500 millis, 1 second) {
        val bb = ByteBuffer.allocate(9).put('>'.toByte).putLong(System.currentTimeMillis()).array()
        localSender ! Tcp.Write(ByteString(bb))
      }

      context.become(ready(replyTo, sender(), scheduled, remote, local))

    case Tcp.CommandFailed(cmd) => log.error(s"Can't connect to $host")

    case x => println(s"pinger unknown: $x")
  }

  def ready(replyTo: ActorRef, socket: ActorRef, scheduled: Cancellable, remote: InetSocketAddress, local: InetSocketAddress): Receive = {
    case Tcp.Received(data) =>
      if (data.head == '<'.toByte) {
        val dataBB = data.tail.asByteBuffer
        val sentTs = dataBB.getLong
        val replyTs = dataBB.getLong
        val nowTs = System.currentTimeMillis()


        replyTo ! RichPing(nowTs, local.getAddress.getHostAddress, remote.getAddress.getHostAddress, (replyTs-sentTs).toInt, (nowTs-replyTs).toInt, (nowTs-sentTs).toInt)
      }

    case Tcp.Close => socket ! Tcp.Close
    case Tcp.Closed => context.stop(self)
    case Tcp.ErrorClosed(cause) => log.warning(s"Connection to responder was closed due to: $cause"); context.stop(self)

    case x => println(s"TCPPinger-UNKNOWN: $x")
  }
}


class TcpPingResponder extends Actor with ConfigProvider with ActorLogging {
  import context.system
  import akka.io.Tcp

  IO(Tcp) ! Tcp.Bind(self, new InetSocketAddress(config.getInt("pinger.port")), options = Tcp.SO.KeepAlive(true) :: Tcp.SO.TcpNoDelay(true) :: Nil)

  def receive = {
    case Tcp.Bound(local) =>
      log.debug(s"discovery responder bound to $local [${sender()}}]");
      context.become(ready(sender()))

    case x => println(s"N: $x")
  }

  def ready(socket: ActorRef): Receive = {

    case Tcp.Unbind  => socket ! Tcp.Unbind
    case Tcp.Unbound => context.stop(self)
    case Tcp.Connected(remote, local) =>
      log.debug(s"connected in responder: $remote -> $local, socket: ${sender()}")
      val handler = context.actorOf(Props(classOf[TcpResponderConnection], remote, local, sender()))
      sender() ! Register(handler)
    case x => println(s"TCPPong-UNKNOWN: $x")
  }
}

class TcpResponderConnection(remote: InetSocketAddress, local: InetSocketAddress, socket: ActorRef) extends Actor with ConfigProvider with ActorLogging {
  def receive = {
    case Tcp.Received(data) =>
      if (data.head == '>'.toByte) {
        val sentOn = data.tail.asByteBuffer.getLong
        val now = System.currentTimeMillis()
        val bb = ByteBuffer.allocate(17).put('<'.toByte).putLong(sentOn).putLong(now).array()
        socket ! Tcp.Write(ByteString(bb))
      } else {
        log.warning(s"received unknown request ${data.utf8String}")
      }

    case Tcp.Unbind  => socket ! Tcp.Unbind
    case Tcp.Closed => context.stop(self)
    case Tcp.ErrorClosed(cause) => log.warning(s"Connection closed due to: $cause"); context.stop(self)
    case x => println(s"respconn: $x")
  }
}

