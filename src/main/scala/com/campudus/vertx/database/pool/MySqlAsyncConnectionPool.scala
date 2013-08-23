package com.campudus.vertx.database.pool

import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import com.campudus.vertx.VertxExecutionContext
import com.github.mauricio.async.db.Connection
import org.vertx.java.core.impl.EventLoopContext
import io.netty.channel.EventLoop
import com.github.mauricio.async.db.mysql.MySQLConnection

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