package io.vertx.asyncsql.database.pool

import scala.concurrent.{ ExecutionContext, Future }
import com.github.mauricio.async.db.{ Configuration, Connection }
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import io.netty.channel.EventLoop
import org.vertx.scala.core.VertxExecutionContext
import org.vertx.scala.platform.Verticle
import io.vertx.asyncsql.Starter

class PostgreSqlAsyncConnectionPool(verticle: Starter, config: Configuration, eventLoop: EventLoop, val maxPoolSize: Int) extends AsyncConnectionPool {

  override def create() = new PostgreSQLConnection(configuration = config, group = eventLoop).connect

}