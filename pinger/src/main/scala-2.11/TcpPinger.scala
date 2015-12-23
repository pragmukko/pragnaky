import java.io.IOException
import java.net._
import java.nio.ByteBuffer
import java.nio.channels._
import java.util
import java.util.concurrent

import akka.actor._
import akka.io.{Tcp, IO}
import akka.io.Tcp.Register
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Flow, Source}
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.stage.{PushStage, Context, SyncDirective}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.util.{Failure, Success}

/**
 * Created by yishchuk on 27.11.2015.
 */
class TcpPinger(host: InetAddress) extends Actor with PingerConfigProvider with ActorLogging {
  import context.system
  import akka.io.Tcp
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global


  def receive = {
    case PingStart =>
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


class TcpPingResponder extends Actor with PingerConfigProvider with ActorLogging {
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

class TcpResponderConnection(remote: InetSocketAddress, local: InetSocketAddress, socket: ActorRef) extends Actor with PingerConfigProvider with ActorLogging {
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

class TcpPingResponderFlow(implicit val system: ActorSystem) extends PingerConfigProvider {
  import akka.stream.scaladsl.Tcp
  implicit val materializer = ActorMaterializer()

  val connections: Source[IncomingConnection, Future[ServerBinding]] = Tcp().bind("0.0.0.0", config.getInt("pinger.port"))

  def start() = {
    connections runForeach { connection =>
      println(s"New connection from: ${connection.remoteAddress}")

      val pong = Flow[ByteString]
        .filter(_.head == '>'.toByte)
        .map(_.tail.asByteBuffer.getLong)
        .map(ByteBuffer.allocate(17).put('<'.toByte).putLong(_).putLong(System.currentTimeMillis()).array())
        .map(ByteString(_))

      connection.handleWith(pong)
    }
  }
}

class TcpPingerFlow(host: String, replyTo: ActorRef)(implicit val system: ActorSystem) extends PingerConfigProvider {
  import akka.stream.scaladsl.Tcp
  import scala.concurrent.duration._
  implicit val materializer = ActorMaterializer()
  import scala.concurrent.ExecutionContext.Implicits.global

  val connection = Tcp().outgoingConnection(new InetSocketAddress(host, config.getInt("pinger.port")))

  def source = Source.single(ByteString(ByteBuffer.allocate(9).put('>'.toByte).putLong(System.currentTimeMillis()).array()))

  val pingFlow = Flow[ByteString]
    .via(connection)
    .filter(_.head == '<'.toByte)
    .map{ bs =>
      println(s"response!: ${bs.utf8String}")
      val dataBB = bs.tail.asByteBuffer
      val sentTs = dataBB.getLong
      val replyTs = dataBB.getLong
      val nowTs = System.currentTimeMillis()
      RichPing(nowTs, PingerNetUtils.localHost.getHostAddress, host, (replyTs-sentTs).toInt, (nowTs-replyTs).toInt, (nowTs-sentTs).toInt)
    }
  val pingSink = Sink.foreach[RichPing]{replyTo ! _}

  def start() = {
    system.scheduler.schedule(500 millis, 1 second) {
      source.via(pingFlow).runWith(pingSink)
    }
  }

}

class TcpPingResponderNio extends PingerConfigProvider with PingNio {

  //this.setDaemon(true)

  import scala.concurrent.ExecutionContext.Implicits.global

  val server = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(config.getInt("pinger.port")))
  println(s"ping responder bound to $server")

  //@tailrec
  def start(): Unit = {
    val written = for {
      ch <- accept(server)
      bytes <- read(ch) if bytes.get() == '>'.toByte
      out = transform(bytes)
      written <- writeOnce(out, ch)
    } yield written


    written onComplete {
      case Success(numWritten) => start()
      case Failure(err) => println(s"pong error: $err"); start()
    }

  }

  def transform(in: ByteBuffer): ByteString = {
    ByteString(ByteBuffer.allocate(17).put('<'.toByte).putLong(in.getLong).putLong(System.currentTimeMillis()).array())
  }

}

class TcpPingResponderNioSync extends Thread with PingerConfigProvider {
  this.setDaemon(true)
  val serverSocketChannel = ServerSocketChannel.open()

  serverSocketChannel.socket().bind(new InetSocketAddress(config.getInt("pinger.port")))
  val pongPayloadSize = config.getInt("pinger.pong-payload")
  val pingPayloadSize = config.getInt("pinger.ping-payload")

  val buf = ByteBuffer.allocateDirect(Math.max(pongPayloadSize, pingPayloadSize) + 17)

  @tailrec
  override final def run(): Unit = {
    try {
      val socketChannel = serverSocketChannel.accept()
      var bytesRead = 0
      while ({bytesRead += socketChannel.read(buf); bytesRead} < 9 + pingPayloadSize ){}
      //val bytesRead = socketChannel.read(buf)
      println(s"S:ping read: $bytesRead from ${socketChannel.getRemoteAddress}")
      buf.flip()
      if (bytesRead >= 9 && buf.get() == '>'.toByte) {
        val start = buf.getLong
        buf.clear()
        buf.put('<'.toByte).putLong(start).putLong(System.currentTimeMillis()).put(Array.ofDim[Byte](pongPayloadSize)).flip()
        socketChannel.write(buf)
        buf.clear()
      }
      socketChannel.close()
    } catch {
      case io: IOException => println(s"S:IO error: ${io.getLocalizedMessage}")
    }
    run()
  }

}

class TcpPingerNioSync(replyTo: ActorRef) extends PingerConfigProvider with PingNio with Pinger {

  val pongPayloadSize = config.getInt("pinger.pong-payload")
  val pingPayloadSize = config.getInt("pinger.ping-payload")

  def ping(host: InetAddress) = {
    var channel: Option[SocketChannel] = None
    try {
      val buf = ByteBuffer.allocate(Math.max(pongPayloadSize, pingPayloadSize) + 17)
      channel = Some(SocketChannel.open(new InetSocketAddress(host, config.getInt("pinger.port"))))
      buf.put('>'.toByte).putLong(System.currentTimeMillis()).put(Array.ofDim[Byte](pingPayloadSize)).flip()
      channel.get.write(buf)
      buf.clear()
      var bytesRead = 0
      while ( {
        bytesRead += channel.get.read(buf); bytesRead
      } < 17 + pongPayloadSize) {}

      println(s"C:pong read: $bytesRead from $host")
      buf.flip()
      if (bytesRead >= 17 && buf.get() == '<'.toByte) {
        replyTo ! readPing(buf, host)
      }
      buf.clear()
    } catch {
      case io: IOException => println(s"C[${host.getHostAddress}}]:IO error: ${io.getLocalizedMessage}")
    } finally {
      channel.foreach(_.close())
    }
  }
}

class TcpPingerNio(replyTo: ActorRef) extends PingerConfigProvider with PingNio with Pinger {
  import scala.concurrent.ExecutionContext.Implicits.global

  def ping(host: InetAddress) = {
    val channel = AsynchronousSocketChannel.open()
    val request = ByteString(ByteBuffer.allocate(9).put('>'.toByte).putLong(System.currentTimeMillis()).array())
    for {
      channel <- connect(channel, new InetSocketAddress(host, config.getInt("pinger.port")))
      numWritten <- write(request, channel)
      readBytes <- read(channel) if readBytes.get() == '<'.toByte
      ping = readPing(readBytes, host)
    } {replyTo ! ping}
  }

}

trait Pinger {
  def ping(host: InetAddress): Any
}
trait PingNio {
  def accept(server: AsynchronousServerSocketChannel): Future[AsynchronousSocketChannel] = {
    val p = Promise[AsynchronousSocketChannel]
    server.accept(null, new CompletionHandler[AsynchronousSocketChannel, Void] {
      def completed(client: AsynchronousSocketChannel, attachment: Void) = p.success(client)
      def failed(e: Throwable, attachment: Void) = {println(s"error1: $e"); p.failure(e)}
    })
    p.future
  }

  def connect(client: AsynchronousSocketChannel, address: SocketAddress): Future[AsynchronousSocketChannel] = {
    val p = Promise[AsynchronousSocketChannel]
    client.connect(address, null, new CompletionHandler[Void, Void] {
      def completed(cl: Void, att: Void) = p.success(client)
      def failed(e: Throwable, attachment: Void) = p.failure(e)
    })
    p.future
  }

  def read(conn: AsynchronousSocketChannel): Future[ByteBuffer] = {
    val buf = ByteBuffer.allocate(1024)
    val p = Promise[ByteBuffer]
    conn.read(buf, null, new CompletionHandler[Integer, Void] {
      def completed(numBytes: Integer, attachment: Void): Unit = {
        //println(s"read $numBytes bytes")
        buf.flip()
        p.success(buf)
      }
      def failed(e: Throwable, attachment: Void) = {println(s"fail1: $e"); p.failure(e)}
    })
    p.future
  }

  def writeOnce(bs: ByteString, sock: AsynchronousSocketChannel): Future[Integer] = {
    val p = Promise[Integer]
    sock.write(bs.asByteBuffer, null, new CompletionHandler[Integer, Void] {
      def completed(numBytesWritten: Integer, attachment: Void) = p.success(numBytesWritten)
      def failed(e: Throwable, attachment: Void) = p.failure(e)
    })
    p.future
  }
  def write(bs: ByteString, sock: AsynchronousSocketChannel)(implicit executor: ExecutionContext): Future[Unit] = {
    writeOnce(bs, sock).flatMap { numBytesWritten =>
      if (numBytesWritten == bs.length) Future.successful(())
      else write(bs.drop(numBytesWritten), sock)
    }
  }
  protected def readPing(bb: ByteBuffer, host: InetAddress): RichPing = {
    //println(s"response!: __")
    //val dataBB = bs.tail.asByteBuffer
    val sentTs = bb.getLong
    val replyTs = bb.getLong
    val nowTs = System.currentTimeMillis()
    RichPing(nowTs, PingerNetUtils.localHost.getHostAddress, host.getHostAddress, (replyTs-sentTs).toInt, (nowTs-replyTs).toInt, (nowTs-sentTs).toInt)
  }
}

object PingerNetUtils {

  import scala.collection.JavaConverters._

  lazy val localHost = {
    val local = NetworkInterface.getNetworkInterfaces.asScala flatMap { ni =>
      if (ni.isLoopback) None
      else {
        ni.getInterfaceAddresses.asScala.map(ia=>(Option(ia.getBroadcast), ia.getAddress)).collectFirst{
          case (Some(_), address) => address
        }
      }
    }
    val localHostName = local.next()
    println(s"localhost: ${localHostName.getHostAddress}")
    localHostName
  }
}

trait PingerConfigProvider {
  lazy val config = ConfigFactory.load()
}