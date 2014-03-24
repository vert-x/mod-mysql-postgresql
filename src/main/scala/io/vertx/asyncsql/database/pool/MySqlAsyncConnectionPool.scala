package io.vertx.asyncsql.database.pool

import scala.concurrent.{ ExecutionContext, Future }
import com.github.mauricio.async.db.{ Configuration, Connection }
import com.github.mauricio.async.db.mysql.MySQLConnection
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import io.netty.channel.EventLoop
import org.vertx.scala.core.VertxExecutionContext
import org.vertx.scala.core.Vertx
import org.vertx.scala.platform.Verticle
import io.vertx.asyncsql.Starter

class MySqlAsyncConnectionPool(val verticle: Starter, config: Configuration, eventLoop: EventLoop, val maxPoolSize: Int) extends AsyncConnectionPool {

  implicit val executionContext = VertxExecutionContext.fromVertxAccess(verticle)
  verticle.

  override def create() = new MySQLConnection(configuration = config, group = eventLoop).connect

}