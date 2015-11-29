package db.neo4j

/*
import db.MetricsDAL
import org.neo4j.graphdb.{RelationshipType, Node, DynamicLabel}
import org.neo4j.graphdb.factory.GraphDatabaseFactory

/**
 * Created by max on 11/27/15.
 */
trait Neo4jMetricsDAL extends MetricsDAL {
  import Neo4jMetricsDAL._

  private val NODE_LABEL = DynamicLabel.label("Server")
  private val SERVER_ATTR_LABEL = DynamicLabel.label("ServerAttribute")

  def storeNodeIfNotExists(name:String, attrs:Tuple2[String, Any]*) : String = {

    val id = getNodeByName(name) match {
      case Some(n:Node) => n.getId
      case None =>
        val node = db.createNode(NODE_LABEL)
        node.setProperty("name", name)
        attrs foreach {case (attrName, attrValue) => node.setProperty(attrName, attrValue)}
        node.getId
    }
    id.toString
  }

  def storeReference(name1:String, name2:String) = ???

  def storeAttributes(name:String, attrs:Tuple2[String, Any]*) = {
    getNodeByName(name) foreach {
      node =>
        val attrNode = db.createNode(SERVER_ATTR_LABEL)
        attrs foreach {case (attrName, attrValue) => node.setProperty(attrName, attrValue)}
        node.createRelationshipTo(attrNode, AttrRelationShip)
    }
  }

  def getNodeByName(name:String) : Option[Node] = Option(db.findNode(NODE_LABEL, "name", name))

  def transaction(proc: => Unit) = {
    val tx = db.beginTx()
    try {
      proc
      tx.success()
    } catch {
      case th =>
        tx.failure()
        throw th
    }

  }

}

case object AttrRelationShip extends RelationshipType {
  def name() = "AttrRelationShip"
}


object Neo4jMetricsDAL {
  lazy val db = new GraphDatabaseFactory().newEmbeddedDatabase("neo4j_database")
} */