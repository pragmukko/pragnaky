package db

import spray.json.JsObject

/**
 * Created by max on 11/27/15.
 */
trait MetricsDAL {

  def persistTelemetry(host: String, telemetry:Seq[JsObject])

  def saveLatency(json:JsObject)

}
