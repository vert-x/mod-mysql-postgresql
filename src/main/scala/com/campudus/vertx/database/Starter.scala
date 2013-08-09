package com.campudus.vertx.database

import com.campudus.vertx.Verticle
import org.vertx.java.core.json.JsonObject
import com.github.mauricio.async.db.postgresql.util.URLParser
import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.postgresql.pool.PostgreSQLConnectionFactory
import com.github.mauricio.async.db.pool.ConnectionPool
import com.github.mauricio.async.db.pool.PoolConfiguration
import com.campudus.vertx.VertxExecutionContext
import com.campudus.vertx.database.pool.AsyncConnectionPool
import org.vertx.scala.core.json.JSON
import org.vertx.scala.core.eventbus.EventBus._

class Starter extends Verticle {

  var handler: ConnectionHandler = null

  override def start(startedResult: org.vertx.scala.core.Future[Void]) = {

    logger.error("Starting async database module for MySQL and PostgreSQL.")

    try {
      val config = Option(new JsonObject(container.config().toString())).getOrElse(new JsonObject)

      val address = config.getString("address", "campudus.asyncdb")
      val dbType = getDatabaseType(config)
      val configuration = getConfiguration(config, dbType)

      handler = new ConnectionHandler(this, dbType, configuration)
      vertx.eventBus.registerHandler(address)(handler)

      logger.error("Async database module for MySQL and PostgreSQL started with config " + configuration)

      startedResult.setResult(null)
    } catch {
      case ex: Throwable =>
        logger.fatal("could not start async database module!", ex)
        startedResult.setFailure(ex)
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