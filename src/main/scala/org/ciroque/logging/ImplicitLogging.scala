package org.ciroque.logging

import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.Logger
import spray.json.{DefaultJsonProtocol, _}

import scala.util.DynamicVariable

object ImplicitLogging {

  private[logging] val activeLoggers = new DynamicVariable[LogEntryBuilder](new LogEntryBuilder)

  def withImplicitLogging[R](name: String)(fx: => R)(implicit logger: Logger) = {
    val started: DateTime = DateTime.now(DateTimeZone.UTC)

    try {
      fx
    } finally {
      val ended = DateTime.now(DateTimeZone.UTC)
      val logEntry = getCurrentImplicitLogger.buildAsJson(name, started, ended)
      logger.info(logEntry.toString())
      getCurrentImplicitLogger.close()
    }
  }

  def setResultException(cause: Throwable) = {
    getCurrentImplicitLogger.result = Exception(cause.getMessage, cause)
  }

  def setResultError(msg: String) = {
    getCurrentImplicitLogger.result = Error(msg)
  }

  def getCurrentImplicitLogger = activeLoggers.value

  def recordValue(name: String, value: String) = {
    getCurrentImplicitLogger.addValue(name, value)
  }

  trait Result

  class LogEntryBuilder() {
    var values: collection.concurrent.Map[String, String] = new collection.concurrent.TrieMap[String, String]
    var result: Result = Success()

    def addValue(name: String, value: String) = {
      values.put(name, value)
    }

    def close(): Unit = {
      values.clear()
      result = Success()
    }

    def buildAsJson(name: String, started: DateTime, ended: DateTime): JsValue = {
      import ImplicitLogging.LogEntryProtocol._
      build(name, started, ended).toJson
    }

    def build(name: String, started: DateTime, ended: DateTime): LogEntry = {
      LogEntry(name, Timing(started, ended, ended.getMillis - started.getMillis), values.toMap, result)
    }
  }

  case class LogEntry(name: String, timing: Timing, values: Map[String, String], result: Result = Success())

  case class Timing(start: DateTime, end: DateTime, duration: Long)

  case class Success() extends Result

  case class Error(msg: String) extends Result

  case class Exception(msg: String, cause: Throwable) extends Result

  object CommonJsonFormatters {
    implicit object DateTimeFormatter extends RootJsonFormat[DateTime] {
      override def read(json: JsValue): DateTime = {
        json match {
          case JsString(string) => DateTime.parse(string)
          case _ => throw new IllegalArgumentException("WTF")
        }
      }

      override def write(obj: DateTime): JsValue = JsString(obj.toString())
    }
  }

  object TimingProtocol extends DefaultJsonProtocol {
    import ImplicitLogging.CommonJsonFormatters._
    implicit def timingFormat: RootJsonFormat[Timing] = jsonFormat3(Timing)
  }

  implicit object ResultJsonFormat extends RootJsonFormat[Result] {
    override def read(json: JsValue): Result = {
      val jso = json.asJsObject
      jso.fields.size match {
        case 0 => Success()
        case 1 => Error(jso.fields("msg").toString())
        case 2 => Exception(jso.fields("msg").toString(), null)
      }
    }

    override def write(result: Result): JsValue = {
      result match {
        case success: Success => JsObject("status" -> JsString("SUCCESS"))
        case error: Error => JsObject("status" -> JsString("ERROR"), "message" -> JsString(error.msg))
        case exception: Exception =>
          JsObject(
            "status" -> JsString("EXCEPTION"),
            "message" -> JsString(exception.cause.getMessage),
            "stackTrace" -> JsString(exception.cause.getStackTrace.mkString))
      }
    }
  }

  object LogEntryProtocol extends DefaultJsonProtocol {
    import ImplicitLogging.TimingProtocol._

    implicit def logEntryFormat: RootJsonFormat[LogEntry] = jsonFormat4(LogEntry)
  }
}
