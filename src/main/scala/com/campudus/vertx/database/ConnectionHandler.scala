package com.campudus.vertx.database

import org.vertx.scala.core.eventbus.Message
import org.vertx.java.core.json.JsonObject
import com.github.mauricio.async.db.Configuration
import com.campudus.vertx.database.pool.PostgreSQLAsyncConnectionPool
import com.campudus.vertx.VertxExecutionContext
import com.github.mauricio.async.db.Connection
import com.campudus.vertx.busmod.ScalaBusMod
import scala.concurrent.Future
import com.campudus.vertx.database.pool.AsyncConnectionPool
import org.vertx.scala.core.Vertx
import com.campudus.vertx.Verticle
import com.github.mauricio.async.db.QueryResult
import com.campudus.vertx.VertxScalaHelpers
import org.vertx.java.core.json.JsonArray
import com.github.mauricio.async.db.RowData
import collection.JavaConverters._
import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException

class ConnectionHandler(verticle: Verticle, dbType: String, config: Configuration) extends ScalaBusMod with VertxScalaHelpers {
  val pool = AsyncConnectionPool(verticle.vertx, dbType, config)
  val logger = verticle.container.logger()

  override def asyncReceive(msg: Message[JsonObject]) = {
    case "select" => select(msg.body)
    case "insert" => insert(msg.body)
    case "raw" => rawCommand(msg.body.getString("command"))
  }

  def close() = pool.close

  private def select(json: JsonObject): Future[Reply] = pool.withConnection({ c: Connection =>
    val table = escapeField(json.getString("table"))
    val command = Option(json.getArray("fields")) match {
      case Some(fields) => fields.asScala.toStream.map(elem => escapeField(elem.toString)).mkString("SELECT ", ",", " FROM " + table)
      case None => "SELECT * FROM " + table
    }

    rawCommand(command)
  })

  private def escapeField(str: String): String = "\"" + str.replace("\"", "\"\"") + "\""
  private def escapeString(str: String): String = "'" + str.replace("'", "''") + "'"

  private def escapeValue(v: Any): String = v match {
    case v: Int => v.toString
    case v: Boolean => v.toString
    case v => escapeString(v.toString)
  }

  private def insert(json: JsonObject): Future[Reply] = {
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

  private def rawCommand(command: String): Future[Reply] = pool.withConnection({ c: Connection =>
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