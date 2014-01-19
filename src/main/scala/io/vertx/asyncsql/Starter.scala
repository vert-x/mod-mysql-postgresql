package io.vertx.asyncsql

import scala.annotation.implicitNotFound
import scala.concurrent.{ ExecutionContext, Promise }

import org.vertx.scala.core.json.{ Json, JsonObject }
import org.vertx.scala.platform.Verticle

import com.github.mauricio.async.db.Configuration

import io.vertx.asyncsql.database.{ ConnectionHandler, MySqlConnectionHandler, PostgreSqlConnectionHandler }

class Starter extends Verticle {

  var handler: ConnectionHandler = null

  override def start(startedResult: Promise[Unit]) = {

    logger.info("Starting async database module for MySQL and PostgreSQL.")

    try {
      val config = Option(container.config()).getOrElse(Json.emptyObj())

      val address = config.getString("address", "campudus.asyncdb")
      val dbType = getDatabaseType(config)
      val configuration = getConfiguration(config, dbType)
      val maxPoolSize = config.getInteger("maxPoolSize", 10)

      handler = dbType match {
        case "postgresql" => new PostgreSqlConnectionHandler(this, configuration, maxPoolSize)
        case "mysql" => new MySqlConnectionHandler(this, configuration, maxPoolSize)
      }
      vertx.eventBus.registerHandler(address, handler)

      logger.info("Async database module for MySQL and PostgreSQL started with config " + configuration)

      startedResult.success()
    } catch {
      case ex: Throwable =>
        logger.fatal("could not start async database module!", ex)
        startedResult.failure(ex)
    }
  }

  override def stop() {
    Option(handler).map(_.close)
  }

  private def getDatabaseType(config: JsonObject) = {
    config.getString("connection", "postgresql").toLowerCase match {
      case "postgresql" => "postgresql"
      case "mysql" => "mysql"
      case x => throw new IllegalArgumentException("unknown connection type " + x)
    }
  }

  private def getConfiguration(config: JsonObject, dbType: String) = {
    val host = config.getString("host", "localhost")
    val port = config.getInteger("port", defaultPortFor(dbType))
    val username = config.getString("username", defaultUserFor(dbType))
    val password = Option(config.getString("password")).orElse(defaultPasswordFor(dbType))
    val database = Option(config.getString("database")).orElse(defaultDatabaseFor(dbType))

    Configuration(username, host, port, password, database)
  }

  private def defaultPortFor(connection: String): Integer = connection match {
    case "postgresql" => 5432
    case "mysql" => 3306
  }

  private def defaultDatabaseFor(connection: String): Option[String] = connection match {
    case _ => Some("testdb")
  }

  private def defaultUserFor(connection: String): String = connection match {
    case "postgresql" => "vertx"
    case "mysql" => "root"
  }

  private def defaultPasswordFor(connection: String): Option[String] = connection match {
    case "postgresql" => Some("test")
    case "mysql" => None
  }
}