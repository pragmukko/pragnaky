package db.mongo

import java.nio.ByteBuffer

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.commons.codec.binary.Hex
import org.jboss.netty.buffer.ChannelBuffers
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat

import reactivemongo.bson._
import spray.json._

/**
 * @author rleibman
 */
trait Mongo2Spray {

  implicit val writer = new BSONDocumentWriter[JsObject] {
    override def write(json: JsObject): BSONDocument = {
      BSONDocument(json.fields.map(writePair))
    }
    private def writeArray(arr: JsArray): BSONArray = {
      val values = arr.elements.zipWithIndex.map(p ⇒ writePair((p._2.toString, p._1))).map(_._2)
      BSONArray(values)
    }

    private val IsoDateTime = """^(\d{4,})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})\.(\d{3})Z$""".r

    private def manageDate(year: String, month: String, day: String, hour: String, minute: String, second: String, milli: String) =
      Try(BSONDateTime((new DateTime(year.toInt, month.toInt, day.toInt, hour.toInt,
        minute.toInt, second.toInt, milli.toInt, DateTimeZone.UTC)).getMillis))

    private def manageTimestamp(o: JsObject) = o.fields.toList match {
      case ("t", JsNumber(t)) :: ("i", JsNumber(i)) :: Nil ⇒
        Success(BSONTimestamp((t.toLong & 4294967295L) | (i.toLong << 32)))
      case _ ⇒ Failure(new IllegalArgumentException("Illegal timestamp value"))
    }

    private def manageSpecials(obj: JsObject): BSONValue =
      if (obj.fields.size > 2) writeObject(obj)
      else (obj.fields.toList match {
        case ("$oid", JsString(str)) :: Nil            ⇒ Try(BSONObjectID(Hex.decodeHex(str.toArray)))
        case ("$undefined", JsTrue) :: Nil             ⇒ Success(BSONUndefined)
        // case ("$minKey", JsNumber(n)) :: Nil if n == 1 => Success(BSONMinKey) // Bug on reactivemongo
        case ("$maxKey", JsNumber(n)) :: Nil if n == 1 ⇒ Success(BSONMaxKey)
        case ("$js", JsString(str)) :: Nil             ⇒ Success(BSONJavaScript(str))
        case ("$sym", JsString(str)) :: Nil            ⇒ Success(BSONSymbol(str))
        case ("$jsws", JsString(str)) :: Nil           ⇒ Success(BSONJavaScriptWS(str))
        case ("$timestamp", ts: JsObject) :: Nil       ⇒ manageTimestamp(ts)
        case ("$regex", JsString(r)) :: ("$options", JsString(o)) :: Nil ⇒
          Success(BSONRegex(r, o))
        case ("$binary", JsString(d)) :: ("$type", JsString(t)) :: Nil ⇒
          Try(BSONBinary(ChannelBuffers.wrappedBuffer(Hex.decodeHex(d.toArray)).array(),
            findSubtype(Hex.decodeHex(t.toArray))))
        // case ("$ref", JsString(v)) :: ("$id", JsString(i)) :: Nil => // Not implemented
        //   Try(BSONDBPointer(v, Hex.decodeHex(i.toArray)))
        case _ ⇒ Success(writeObject(obj))
      }) match {
        case Success(v) ⇒ v
        case Failure(_) ⇒ writeObject(obj)
      }

    def findSubtype(bytes: Array[Byte]) =
      ByteBuffer.wrap(bytes).getInt match {
        case 0x00 ⇒ Subtype.GenericBinarySubtype
        case 0x01 ⇒ Subtype.FunctionSubtype
        case 0x02 ⇒ Subtype.OldBinarySubtype
        case 0x03 ⇒ Subtype.UuidSubtype
        case 0x05 ⇒ Subtype.Md5Subtype
        // case 0X80 => Subtype.UserDefinedSubtype // Bug on reactivemongo
        case _    ⇒ throw new IllegalArgumentException("unsupported binary subtype")
      }

    private def writeObject(obj: JsObject): BSONDocument = BSONDocument(obj.fields.map(writePair))

    private def writePair(p: (String, JsValue)): (String, BSONValue) = (p._1, p._2 match {
      case JsString(str @ IsoDateTime(y, m, d, h, mi, s, ms)) ⇒ manageDate(y, m, d, h, mi, s, ms) match {
        case Success(dt) ⇒ dt
        case Failure(_)  ⇒ BSONString(str)
      }
      case JsString(str)   ⇒ BSONString(str)
      case JsNumber(value) ⇒ BSONDouble(value.doubleValue)
      case obj: JsObject   ⇒ manageSpecials(obj)
      case arr: JsArray    ⇒ writeArray(arr)
      case JsTrue          ⇒ BSONBoolean(true)
      case JsFalse         ⇒ BSONBoolean(false)
      case JsNull          ⇒ BSONNull
    })
  }
  implicit val reader = new BSONDocumentReader[JsObject] {
    override def read(bson: BSONDocument): JsObject = {
      JsObject(bson.elements.toSeq.map(readElement): _*)
    }
    private def readObject(doc: BSONDocument) = {
      JsObject(doc.elements.toSeq.map(readElement): _*)
    }
    private val isoFormatter = ISODateTimeFormat.dateTime.withZone(DateTimeZone.UTC)

    private def readElement(e: BSONElement): (String, JsValue) = e._1 -> (e._2 match {
      case BSONString(value)         ⇒ JsString(value)
      case BSONInteger(value)        ⇒ JsNumber(value)
      case BSONLong(value)           ⇒ JsNumber(value)
      case BSONDouble(value)         ⇒ JsNumber(value)
      case BSONBoolean(true)         ⇒ JsTrue
      case BSONBoolean(false)        ⇒ JsFalse
      case BSONNull                  ⇒ JsNull
      case doc: BSONDocument         ⇒ readObject(doc)
      case arr: BSONArray            ⇒ readArray(arr)
      case oid @ BSONObjectID(value) ⇒ JsObject("$oid" -> JsString(oid.stringify))
      case BSONDateTime(value)       ⇒ JsString(isoFormatter.print(value)) // Doesn't follow mongdb-extended
      case bb: BSONBinary            ⇒ readBSONBinary(bb)
      case BSONRegex(value, flags)   ⇒ JsObject("$regex" -> JsString(value), "$options" -> JsString(flags))
      case BSONTimestamp(value) ⇒ JsObject("$timestamp" -> JsObject(
        "t" -> JsNumber(value.toInt), "i" -> JsNumber((value >>> 32).toInt)))
      case BSONUndefined           ⇒ JsObject("$undefined" -> JsTrue)
      // case BSONMinKey                   => JsObject("$minKey" -> JsNumber(1)) // Bug on reactivemongo
      case BSONMaxKey              ⇒ JsObject("$maxKey" -> JsNumber(1))
      // case BSONDBPointer(value, id) => JsObject("$ref" -> JsString(value), "$id" -> JsString(Hex.encodeHexString(id))) // Not implemented
      // NOT STANDARD AT ALL WITH JSON and MONGO
      case BSONJavaScript(value)   ⇒ JsObject("$js" -> JsString(value))
      case BSONSymbol(value)       ⇒ JsObject("$sym" -> JsString(value))
      case BSONJavaScriptWS(value) ⇒ JsObject("$jsws" -> JsString(value))
    })

    private def readArray(array: BSONArray) = JsArray(array.iterator.toSeq.map(element ⇒ readElement(element.get)._2): _*)

    private def readBSONBinary(bb: BSONBinary) = {
      val arr = new Array[Byte](bb.value.readable())
      bb.value.readBytes(arr)
      val sub = ByteBuffer.allocate(4).putInt(bb.subtype.value).array
      JsObject("$binary" -> JsString(Hex.encodeHexString(arr)),
        "$type" -> JsString(Hex.encodeHexString(sub)))
    }
  }

}