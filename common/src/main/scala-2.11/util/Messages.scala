package util

import java.net.InetAddress

import spray.json.{JsString, JsNumber, JsObject}

/**
 * Created by max on 11/27/15.
 */
object Messages {

  case object Identify

  case class Register(route:String)

  case class PersistenceError(err:Any)

}

