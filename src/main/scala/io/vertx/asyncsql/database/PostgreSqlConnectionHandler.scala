package io.vertx.asyncsql.database

import org.vertx.scala.platform.Verticle

import com.github.mauricio.async.db.Configuration

class PostgreSqlConnectionHandler(val verticle: Verticle, val config: Configuration, val dbType: String = "postgresql") extends ConnectionHandler {
}