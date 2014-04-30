package io.vertx.asyncsql.database.pool

import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.mysql.MySQLConnection
import io.netty.channel.EventLoop
import io.vertx.asyncsql.Starter
import org.vertx.scala.core.VertxExecutionContext

class MySqlAsyncConnectionPool(val verticle: Starter, config: Configuration, eventLoop: EventLoop, val maxPoolSize: Int) extends AsyncConnectionPool {

  override def create() = new MySQLConnection(
    configuration = config,
    group = eventLoop,
    executionContext = VertxExecutionContext.fromVertxAccess(verticle)
  ).connect

}