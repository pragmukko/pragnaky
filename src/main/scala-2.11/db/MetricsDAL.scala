package db

/**
 * Created by max on 11/27/15.
 */
trait MetricsDAL {

  def storeNodeIfNotExists(name:String, attrs:Tuple2[String, Any]*) : String

  def storeReference(name1:String, name2:String)

  def storeAttributes(name:String, attrs:Tuple2[String, Any]*)

  def transaction(proc:  => Unit)
}
