/*
* Copyright 2015-2016 Pragmukko Project [http://github.org/pragmukko]
* Licensed under the Apache License, Version 2.0 (the "License"); you may not
* use this file except in compliance with the License. You may obtain a copy of
* the License at
*
*    [http://www.apache.org/licenses/LICENSE-2.0]
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations under
* the License.
*/
package web

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Credentials`, `Access-Control-Max-Age`, `Access-Control-Allow-Headers`}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config
import http.CorsSupport
import akka.http.scaladsl.model.StatusCodes._
import spray.json._
import util.Messages.{Nodes, Edges, RawQuery}
import utils.ConfigProvider
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.parsing.json.{JSONFormat, JSONObject, JSONArray}
import scala.collection.JavaConversions._
import akka.pattern._

/**
 * Created by yishchuk on 30.11.2015.
 */
class RestService(mediator: ActorRef, config:Config)(implicit system:ActorSystem) extends CorsSupport with SprayJsonSupport {

  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(2, TimeUnit.SECONDS)

  override val corsAllowOrigins: List[String] = List("*")
  override val corsAllowedHeaders: List[String] = List("Origin", "X-Requested-With", "Content-Type", "Accept", "Accept-Encoding", "Accept-Language", "Host", "Referer", "User-Agent")
  override val corsAllowCredentials: Boolean = true
  override val optionsCorsHeaders: List[HttpHeader] = List[HttpHeader](
    `Access-Control-Allow-Headers`(corsAllowedHeaders.mkString(", ")),
    `Access-Control-Max-Age`(60 * 60 * 24 * 20), // cache pre-flight response for 20 days
    `Access-Control-Allow-Credentials`(corsAllowCredentials)
  )

  implicit val stringMarshaller = Marshaller.opaque { js: String =>
    HttpResponse(OK, entity = HttpEntity(ContentTypes.`application/json`, js ))
  }

  def start() = {
    Http().bindAndHandle(cors { routes }, config.getString("http.interface"), config.getInt("http.port"))
    println("REST Service started")
  }

  def now = System.currentTimeMillis()

  val routes = {
   path("db" / Segment ) {
     dataType => {
        parameters('q.as[String], 'sort.as[String] ?, 'limit.as[Int] ?, 'fields.as[String]) {
          (q, sort, limit, fields) =>
            complete {
              val m = RawQuery(dataType, q, sort, limit, Some(fields))
              sendMsg(m)
            }

        }
     }
   } ~ path("edges") {
     get {
       complete {
         sendMsg(Edges)
       }
     }
   } ~ path("nodes") {
     get {
       complete {
         sendMsg(Nodes)
       }
     }
   } ~ path("") {
     get {
       getFromResource(s"www/index.html")
     }
   } ~
     getFromResourceDirectory("www")

  }

  def sendMsg(msg:Any): Future[String] = try {
    (mediator ? Send(path = "/user/TelemetryAdapter", msg = msg, localAffinity = false)) collect { case resp:String => resp }
  } catch {
    case th:Throwable =>
      th.printStackTrace()
      throw th
  }

  def jsonObjFormatter(v:Any) : String = v match {
    case m:java.util.Map[String, Any] =>
      JSONObject(m.toMap).toString(jsonObjFormatter)

    case a:java.util.List[Any] =>
      JSONArray(a.toList).toString(jsonObjFormatter)

    case jso:JSONObject => jso.toString(jsonObjFormatter)

    case null =>
      "0"
    case other =>
      JSONFormat.defaultFormatter(other)

  }

  implicit def asFiniteDuration(d: java.time.Duration): FiniteDuration = Duration.fromNanos(d.toNanos)

}

