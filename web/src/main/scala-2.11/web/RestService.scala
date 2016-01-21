package web

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Credentials`, `Access-Control-Max-Age`, `Access-Control-Allow-Headers`}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import http.CorsSupport
import org.elasticsearch.client.Client
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.elasticsearch.search.aggregations.AggregationBuilders
import akka.http.scaladsl.model.StatusCodes._
import org.elasticsearch.search.aggregations.metrics.avg.Avg
import org.elasticsearch.search.sort.SortOrder
import spray.json._
import utils.ConfigProvider
import scala.concurrent.duration._
import scala.util.parsing.json.{JSONObject, JSONArray}
import scala.collection.JavaConversions._

/**
 * Created by yishchuk on 30.11.2015.
 */
class RestService(clientProvider: (Client => String) => String, config:Config)(implicit system:ActorSystem) extends CorsSupport with SprayJsonSupport {

  implicit val materializer = ActorMaterializer()

  override val corsAllowOrigins: List[String] = List("*")
  override val corsAllowedHeaders: List[String] = List("Origin", "X-Requested-With", "Content-Type", "Accept", "Accept-Encoding", "Accept-Language", "Host", "Referer", "User-Agent")
  override val corsAllowCredentials: Boolean = true
  override val optionsCorsHeaders: List[HttpHeader] = List[HttpHeader](
    `Access-Control-Allow-Headers`(corsAllowedHeaders.mkString(", ")),
    `Access-Control-Max-Age`(60 * 60 * 24 * 20), // cache pre-flight response for 20 days
    `Access-Control-Allow-Credentials`(corsAllowCredentials)
  )

  implicit val stringSetMarshaller = Marshaller.opaque { addrs: Set[String] =>
    val js = JsArray(Vector(addrs.toList map {JsString(_)}:_*))
    HttpResponse(OK, entity = HttpEntity(ContentTypes.`application/json`, js.compactPrint ))
  }

  def start() = {
    Http().bindAndHandle(cors { routes }, config.getString("http.interface"), config.getInt("http.port"))
    println("REST Service started")
  }

  def now = System.currentTimeMillis()

  val routes = {
   path("db" / Segment ) {
     dataType => {
        parameters('q.as[String], 'sort.as[String] ?, 'limit.as[Int] ?) {
          (q, sort, limit) =>
            complete {
              clientProvider {
                c =>
                  val request = c.prepareSearch("stat").setTypes(dataType).setQuery(q)
                  sort
                    .map(_.split(':').toList)
                    .collect{ case a :: b :: Nil => a -> b  }
                    .foreach(x => request.addSort(x._1, SortOrder.valueOf(x._2) ))

                  request.setSize(limit.getOrElse(0))

                  val res = c.prepareSearch("stat").setTypes(dataType).setQuery(q).execute().actionGet()
                  JSONArray(res.getHits.getHits.map(_.sourceAsMap().toMap[String, Any]).map(JSONObject(_)).toList).toString
              }
            }
        }
     }
   } ~ path("edges") {
     get {
       complete {
         clientProvider {
           c =>
             val latencyAggr = AggregationBuilders.avg("avg_ping").field("pingTotal")
             val srcAddrAggr = AggregationBuilders.terms("source").field("source").size(0).subAggregation(latencyAggr)
             val destAddrAggr = AggregationBuilders.terms("dest").field("dest").size(0).subAggregation(srcAddrAggr)
             val resp = c.prepareSearch("stat").setTypes("latency").addAggregation(destAddrAggr).execute().actionGet()

             val terms = resp.getAggregations.get("dest").asInstanceOf[Terms]
             val respArray = terms.getBuckets.flatMap {
               destBucket =>
                 val dst = destBucket.getKey

                 destBucket.getAggregations.get("source").asInstanceOf[Terms].getBuckets.map {
                   sourceBucket =>
                     Map(
                       "dest" -> dst,
                       "source" -> sourceBucket.getKey,
                       "last" -> sourceBucket.getAggregations.get("avg_ping").asInstanceOf[Avg].getValue
                     )
                 }
             }

             JSONArray(respArray.toList).toString{ case m:Map[String, _] => JSONObject(m).toString()}
         }

       }
     }
   } ~ path("nodes") {
     get {
       complete {
         clientProvider {
           c =>
             val aggr = AggregationBuilders.terms("nodes").field("addr").size(0)
             val resp = c.prepareSearch("stat").setTypes("telemetry").addAggregation(aggr).execute().actionGet()
             val terms = resp.getAggregations.get("nodes").asInstanceOf[Terms]
             JSONArray(terms.getBuckets.map(_.getKey).toList).toString()
         }
       }
     }
   } ~ path("") {
     get {
       getFromResource(s"www/index.html")
     }
   } ~
     getFromResourceDirectory("www")

  }

  implicit def asFiniteDuration(d: java.time.Duration): FiniteDuration = Duration.fromNanos(d.toNanos)

}

