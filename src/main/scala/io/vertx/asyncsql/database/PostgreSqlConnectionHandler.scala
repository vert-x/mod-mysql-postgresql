package io.vertx.asyncsql.database

import io.vertx.helpers.Verticle
import com.github.mauricio.async.db.Configuration
import org.vertx.scala.core.logging.Logger

class PostgreSqlConnectionHandler(val verticle: Verticle, val config: Configuration, val dbType: String = "postgresql") extends ConnectionHandler {
  val logger = new Logger(verticle.container.logger)
}