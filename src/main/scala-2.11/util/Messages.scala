package util

/**
 * Created by max on 11/27/15.
 */
object Messages {

  case object Identify

  case class Register(route:String)

  case class PersistenceError(err:Any)

}
