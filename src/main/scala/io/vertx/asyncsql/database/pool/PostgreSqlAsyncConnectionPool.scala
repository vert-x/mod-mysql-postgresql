package io.vertx.asyncsql.database.pool

import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import io.vertx.helpers.VertxExecutionContext
import com.github.mauricio.async.db.Connection
import org.vertx.java.core.impl.EventLoopContext
import io.netty.channel.EventLoop

class PostgreSqlAsyncConnectionPool(config: Configuration, eventLoop: EventLoop, implicit val executionContext: ExecutionContext = VertxExecutionContext) extends AsyncConnectionPool[PostgreSQLConnection] {

  override def take() = new PostgreSQLConnection(configuration = config, group = eventLoop).connect

  override def giveBack(connection: Connection) = {
    connection.disconnect map (_ => PostgreSqlAsyncConnectionPool.this) recover {
      case ex =>
        executionContext.reportFailure(ex)
        PostgreSqlAsyncConnectionPool.this
    }
  }

  override def close() = Future.successful(PostgreSqlAsyncConnectionPool.this)

}