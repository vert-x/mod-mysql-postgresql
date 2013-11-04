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
    case "prepared" => prepared(msg.body)
    case "transaction" => transaction(msg.body)
    case "raw" => rawCommand(msg.body.getString("command"))
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
    rawCommand(selectCommand(json))
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
    rawCommand(insertCommand(json))
  }

  protected def transaction(json: JsonObject): Future[Reply] = pool.withConnection({ c: Connection =>
    logger.info("TRANSACTION-JSON: " + json.encodePrettily())
    Option(json.getArray("statements")) match {
      case Some(statements) => rawCommand((statements.asScala.map {
        case js: JsonObject =>
          js.getString("action") match {
            case "select" => selectCommand(js)
            case "insert" => insertCommand(js)
            case "raw" => js.getString("command")
          }
        case _ => throw new IllegalArgumentException("'statements' needs JsonObjects!")
      }).mkString(transactionStart, statementDelimiter, statementDelimiter + transactionEnd))
      case None => throw new IllegalArgumentException("No 'statements' field in request!")
    }
  })

  protected def prepared(json: JsonObject): Future[Reply] = pool.withConnection({ c: Connection =>
    c.sendPreparedStatement(json.getString("statement"), json.getArray("values").toArray()) map buildResults recover {
      case x: GenericDatabaseException =>
        Error(x.errorMessage.message)
      case x =>
        Error(x.getMessage())
    }
  })

  protected def rawCommand(command: String): Future[Reply] = pool.withConnection({ c: Connection =>
    logger.info("sending command: " + command)
    c.sendQuery(command) map buildResults recover {
      case x: GenericDatabaseException =>
        Error(x.errorMessage.message)
      case x =>
        Error(x.getMessage())
    }
  })

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