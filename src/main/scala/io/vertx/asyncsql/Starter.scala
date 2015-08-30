package io.vertx.asyncsql

import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Promise}

import org.vertx.scala.core.json.{Json, JsonObject}
import org.vertx.scala.platform.Verticle

import com.github.mauricio.async.db.Configuration

import org.vertx.scala.core.FunctionConverters._
import io.vertx.asyncsql.database.{ConnectionHandler, MySqlConnectionHandler, PostgreSqlConnectionHandler}

import scala.util.{Try, Failure, Success}

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
      val transactionTimeout = config.getLong("transactionTimeout", 500L)

      handler = dbType match {
        case "postgresql" => new PostgreSqlConnectionHandler(this, configuration, maxPoolSize, transactionTimeout)
        case "mysql" => new MySqlConnectionHandler(this, configuration, maxPoolSize, transactionTimeout)
      }
      vertx.eventBus.registerHandler(address, handler, {
        case Success(x) =>
          logger.info("Async database module for MySQL and PostgreSQL started")
          startedResult.success()
        case Failure(x) =>
          startedResult.failure(x)
      }: Try[Void] => Unit)
    } catch {
      case ex: Throwable =>
        logger.fatal("could not start async database module!", ex)
        startedResult.failure(ex)
    }
  }

  override def stop() {
    Option(handler).map(pool => pool.close() andThen {
      case Success(x) =>
        logger.info(s"Async database module for MySQL and PostgreSQL stopped completely on address ${container.config().getString("address")}")
      case Failure(ex) =>
        logger.warn(s"Async database module for MySQL and PostgreSQL failed to stop completely on address ${container.config().getString("address")}", ex)
    })
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
    case "postgresql" => "postgres"
    case "mysql" => "root"
  }

  private def defaultPasswordFor(connection: String): Option[String] = connection match {
    case "postgresql" => None
    case "mysql" => None
  }
}