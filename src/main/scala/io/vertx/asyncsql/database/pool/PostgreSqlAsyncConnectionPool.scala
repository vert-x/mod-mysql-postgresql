package io.vertx.asyncsql.database.pool

import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import io.netty.channel.EventLoop
import io.vertx.asyncsql.Starter

class PostgreSqlAsyncConnectionPool(val verticle: Starter, config: Configuration, eventLoop: EventLoop, val maxPoolSize: Int) extends AsyncConnectionPool {

  override def create() = new PostgreSQLConnection(configuration = config, group = eventLoop).connect

}