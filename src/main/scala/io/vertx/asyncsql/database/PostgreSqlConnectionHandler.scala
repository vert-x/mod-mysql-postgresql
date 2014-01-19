package io.vertx.asyncsql.database

import org.vertx.scala.platform.Verticle
import com.github.mauricio.async.db.Configuration
import io.vertx.asyncsql.Starter

class PostgreSqlConnectionHandler(val verticle: Starter, val config: Configuration, val maxPoolSize: Int) extends ConnectionHandler {
  override val dbType: String = "postgresql"
}