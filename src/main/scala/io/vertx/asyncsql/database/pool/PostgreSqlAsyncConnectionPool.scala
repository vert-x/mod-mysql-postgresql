package io.vertx.asyncsql.database.pool

import scala.concurrent.{ ExecutionContext, Future }
import com.github.mauricio.async.db.{ Configuration, Connection }
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import io.netty.channel.EventLoop
import org.vertx.scala.core.VertxExecutionContext

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