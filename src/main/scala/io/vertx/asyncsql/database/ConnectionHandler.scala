package io.vertx.asyncsql.database

import org.vertx.scala.core.eventbus.Message
import com.github.mauricio.async.db.Configuration
import io.vertx.asyncsql.database.pool.PostgreSqlAsyncConnectionPool
import io.vertx.helpers.VertxExecutionContext
import com.github.mauricio.async.db.Connection
import io.vertx.busmod.ScalaBusMod
import scala.concurrent.Future
import io.vertx.asyncsql.database.pool.AsyncConnectionPool
import org.vertx.scala.core.Vertx
import io.vertx.helpers.Verticle
import com.github.mauricio.async.db.QueryResult
import io.vertx.helpers.VertxScalaHelpers
import com.github.mauricio.async.db.RowData
import collection.JavaConverters._
import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException
import org.vertx.scala.core.json._

trait ConnectionHandler extends ScalaBusMod with VertxScalaHelpers {
  val verticle: Verticle
  def dbType: String
  val config: Configuration
  lazy val pool = AsyncConnectionPool(verticle.vertx, dbType, config)
  lazy val logger = verticle.container.logger()

  override def asyncReceive(msg: Message[JsonObject]) = {
    case "select" => select(msg.body)
    case "insert" => insert(msg.body)
    case "prepared" => prepared(msg.body)
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

  protected def select(json: JsonObject): Future[Reply] = pool.withConnection({ c: Connection =>
    val table = escapeField(json.getString("table"))
    val command = Option(json.getArray("fields")) match {
      case Some(fields) => fields.asScala.toStream.map(elem => escapeField(elem.toString)).mkString("SELECT ", ",", " FROM " + table)
      case None => "SELECT * FROM " + table
    }

    rawCommand(command)
  })

  protected def insert(json: JsonObject): Future[Reply] = {
    val table = json.getString("table")
    val fields = json.getArray("fields").asScala
    val lines = json.getArray("values").asScala
    val listOfLines = for {
      line <- lines
    } yield {
      line.asInstanceOf[JsonArray].asScala.toStream.map(v => escapeValue(v)).mkString("(", ",", ")")
    }
    val cmd = new StringBuilder("INSERT INTO ")
      .append(escapeField(table))
      .append(" ")
      .append(fields.map(f => escapeField(f.toString)).mkString("(", ",", ")"))
      .append(" VALUES ")
      .append(listOfLines.mkString(","))

    rawCommand(cmd.toString)
  }

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

  private def rowDataToJsonArray(rowData: RowData): JsonArray = rowData.toList
}