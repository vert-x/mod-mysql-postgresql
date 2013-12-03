package io.vertx.asyncsql.database

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.concurrent.Future
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.json.{ JsonArray, JsonObject }
import org.vertx.scala.core.logging.Logger
import org.vertx.scala.platform.Verticle
import com.github.mauricio.async.db.{ Configuration, Connection, QueryResult, RowData }
import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException
import io.vertx.asyncsql.database.pool.AsyncConnectionPool
import io.vertx.busmod.ScalaBusMod
import io.vertx.helpers.VertxScalaHelpers
import org.vertx.scala.core.json.Json

trait ConnectionHandler extends ScalaBusMod with VertxScalaHelpers {
  val verticle: Verticle
  def dbType: String
  val config: Configuration
  lazy val logger: Logger = verticle.logger
  val pool = AsyncConnectionPool(verticle.vertx, dbType, config)

  def transactionStart: String = "START TRANSACTION;"
  def transactionEnd: String = "COMMIT;"
  def statementDelimiter: String = ";"

  import org.vertx.scala.core.eventbus._
  override def asyncReceive(msg: Message[JsonObject]) = {
    case "select" => select(msg.body)
    case "insert" => insert(msg.body)
    case "prepared" => sendWithPool(prepared(msg.body))
    case "transaction" => transaction(msg.body)
    case "raw" => sendWithPool(rawCommand(msg.body.getString("command")))
  }

  def close() = pool.close

  protected def escapeField(str: String): String = "\"" + str.replace("\"", "\"\"") + "\""
  protected def escapeString(str: String): String = "'" + str.replace("'", "''") + "'"

  protected def escapeValue(v: Any): String = v match {
    case v: Int => v.toString
    case v: Boolean => v.toString
    case v => escapeString(v.toString)
  }

  protected def selectCommand(json: JsonObject): String = {
    val table = escapeField(json.getString("table"))
    Option(json.getArray("fields")) match {
      case Some(fields) => fields.asScala.toStream.map(elem => escapeField(elem.toString)).mkString("SELECT ", ",", " FROM " + table)
      case None => "SELECT * FROM " + table
    }
  }

  protected def select(json: JsonObject): Future[Reply] = pool.withConnection({ c: Connection =>
    sendWithPool(rawCommand(selectCommand(json)))
  })

  protected def insertCommand(json: JsonObject): String = {
    val table = json.getString("table")
    val fields = json.getArray("fields").asScala
    val lines = json.getArray("values").asScala
    val listOfLines = for {
      line <- lines
    } yield {
      line.asInstanceOf[JsonArray].asScala.toStream.map(v => escapeValue(v)).mkString("(", ",", ")")
    }
    new StringBuilder("INSERT INTO ")
      .append(escapeField(table))
      .append(" ")
      .append(fields.map(f => escapeField(f.toString)).mkString("(", ",", ")"))
      .append(" VALUES ")
      .append(listOfLines.mkString(",")).toString
  }

  protected def insert(json: JsonObject): Future[Reply] = {
    sendWithPool(rawCommand(insertCommand(json)))
  }

  sealed trait CommandType { val query: Connection => Future[QueryResult] }
  case class Raw(stmt: String) extends CommandType { val query = rawCommand(stmt) }
  case class Prepared(json: JsonObject) extends CommandType { val query = prepared(json) }

  protected def transaction(json: JsonObject): Future[Reply] = pool.withConnection({ c: Connection =>
    logger.info("TRANSACTION-JSON: " + json.encodePrettily())

    Option(json.getArray("statements")) match {
      case Some(statements) => c.inTransaction { conn: Connection =>
        val futures = (statements.asScala.map {
          case js: JsonObject =>
            js.getString("action") match {
              case "select" => Raw(selectCommand(js))
              case "insert" => Raw(insertCommand(js))
              case "prepared" => Prepared(js)
              case "raw" => Raw(js.getString("command"))
            }
          case _ => throw new IllegalArgumentException("'statements' needs JsonObjects!")
        })
        val f = (futures.foldLeft(Future[Any]()) { case (f, cmd) => f flatMap (_ => cmd.query(conn)) })
        f map (_ => Ok(Json.obj()))
      }
      case None => throw new IllegalArgumentException("No 'statements' field in request!")
    }
  })

  protected def sendWithPool(fn: Connection => Future[QueryResult]): Future[Reply] = pool.withConnection({ c: Connection =>
    fn(c) map buildResults recover {
      case x: GenericDatabaseException =>
        Error(x.errorMessage.message)
      case x =>
        Error(x.getMessage())
    }
  })

  protected def prepared(json: JsonObject): Connection => Future[QueryResult] = { c: Connection =>
    c.sendPreparedStatement(json.getString("statement"), json.getArray("values").toArray())
  }

  protected def rawCommand(command: String): Connection => Future[QueryResult] = { c: Connection => c.sendQuery(command) }

  private def buildResults(qr: QueryResult): Reply = {
    val result = new JsonObject()
    result.putString("message", qr.statusMessage)
    result.putNumber("rows", qr.rowsAffected)

    qr.rows match {
      case Some(resultSet) =>
        val fields = (new JsonArray() /: resultSet.columnNames) { (arr, name) =>
          arr.addString(name)
        }

        val rows = (new JsonArray() /: resultSet) { (arr, rowData) =>
          arr.add(rowDataToJsonArray(rowData))
        }

        result.putArray("fields", fields)
        result.putArray("results", rows)
      case None =>
    }

    Ok(result)
  }

  private def rowDataToJsonArray(rowData: RowData): JsonArray = Json.arr(rowData.toList: _*)
}