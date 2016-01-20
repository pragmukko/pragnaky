package web

import akka.actor.{Cancellable, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Credentials`, `Access-Control-Max-Age`, `Access-Control-Allow-Headers`}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import db.mongo.{Mongo2Spray, MongoMetricsDAL}
import http.CorsSupport
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDateTime, BSONArray, BSONString, BSONDocument}
import akka.http.scaladsl.model.StatusCodes._
import spray.json._
import utils.ConfigProvider
import reactivemongo.api.commands._
import scala.concurrent.Future
import akka.agent.Agent
import scala.concurrent.duration._

/**
 * Created by yishchuk on 30.11.2015.
 */
class RestService(implicit val system: ActorSystem, val config: Config) extends CorsSupport with MongoMetricsDAL with SprayJsonSupport {

  implicit val executionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()

  override val corsAllowOrigins: List[String] = List("*")
  override val corsAllowedHeaders: List[String] = List("Origin", "X-Requested-With", "Content-Type", "Accept", "Accept-Encoding", "Accept-Language", "Host", "Referer", "User-Agent")
  override val corsAllowCredentials: Boolean = true
  override val optionsCorsHeaders: List[HttpHeader] = List[HttpHeader](
    `Access-Control-Allow-Headers`(corsAllowedHeaders.mkString(", ")),
    `Access-Control-Max-Age`(60 * 60 * 24 * 20), // cache pre-flight response for 20 days
    `Access-Control-Allow-Credentials`(corsAllowCredentials)
  )

  implicit val bsonListMarshaller = Marshaller.opaque { bsons: List[BSONDocument] =>
    val js = JsArray(bsons.map(bson => reader.read(bson)):_*)
    HttpResponse(OK, entity = HttpEntity(ContentTypes.`application/json`, js.compactPrint ))
  }

  def start() = {
    Http().bindAndHandle(cors { routes }, config.getString("http.interface"), config.getInt("http.port"))
    println("REST Service started")
  }

  def now = System.currentTimeMillis()

  val routes = {
    import Directives._

    //logRequestResult("swarm-akka") {
      (path("db" / knownDbs) & parameters('q.?, 'p.?, 'sort.?, 'limit.as[Int].?)) { (db, q, p, sort, limit) =>
        get {
            complete {
              db.find(q.toBson, p.toBson).sort(sort.toBson).cursor[BSONDocument]().collect[List](limit.getOrElse(100))
            }
        }
      } ~ path("edges") {
        get {
          complete {
            val col:BSONCollection = knownDbs("latency")
            import col.BatchCommands.AggregationFramework.{
              Match, Avg, Group, Last, Max
            }
            //val mtch = Match( BSONDocument( "creationTime" -> BSONDocument("$gte" -> BSONDateTime(now - (30 * 60 * 1000 )) ) ) )
            val group = Group(BSONDocument("source" -> "$source", "dest" -> "$dest"))("last" -> Avg("pingTotal"), "timestamp" -> Max("time"))
            col.aggregate(group).map(_.documents)
          }
        }
      } ~ path("nodes") {
        get {
          complete {
            val col:BSONCollection = knownDbs("telemetry")
            import col.BatchCommands.AggregationFramework.{
              Sort, Group, Last, Ascending
            }
            val sort = Sort(Ascending("timestamp"))
            val group = Group(BSONDocument("addr" -> "$addr"))("last" -> Last("timestamp"), "cpu" -> Last("cpu"))
            col.aggregate(sort, List(group)).map(_.documents)
          }
        }
      } ~ path("") {
        get {
          getFromResource(s"www/index.html")
        }
      } ~
        getFromResourceDirectory("www")
    //}

  }

  implicit class BsonSupport(strOpt: Option[String]) {
    def toBson = strOpt.map(str => writer.write(JsonParser(str).asJsObject)).getOrElse(BSONDocument())
  }

}

object RestNode extends App with ConfigProvider {
    val system = ActorSystem(config.getString("akka-sys-name"), config)

    new RestService()(system, config).start()
}
