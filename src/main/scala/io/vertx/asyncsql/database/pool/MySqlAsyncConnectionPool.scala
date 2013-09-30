package io.vertx.asyncsql.database.pool

import scala.concurrent.{ ExecutionContext, Future }
import com.github.mauricio.async.db.{ Configuration, Connection }
import com.github.mauricio.async.db.mysql.MySQLConnection
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import io.netty.channel.EventLoop
import org.vertx.scala.core.VertxExecutionContext

class MySqlAsyncConnectionPool(config: Configuration, eventLoop: EventLoop, implicit val executionContext: ExecutionContext = VertxExecutionContext) extends AsyncConnectionPool[PostgreSQLConnection] {

  override def take() = new MySQLConnection(configuration = config, group = eventLoop).connect

  override def giveBack(connection: Connection) = {
    connection.disconnect map (_ => MySqlAsyncConnectionPool.this) recover {
      case ex =>
        executionContext.reportFailure(ex)
        MySqlAsyncConnectionPool.this
    }
  }

  override def close() = Future.successful(MySqlAsyncConnectionPool.this)

}