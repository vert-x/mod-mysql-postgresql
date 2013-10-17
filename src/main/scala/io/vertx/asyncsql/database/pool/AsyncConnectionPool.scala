package io.vertx.asyncsql.database.pool

import com.github.mauricio.async.db.Configuration
import scala.concurrent.Future
import com.github.mauricio.async.db.Connection
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success
import org.vertx.scala.core.Vertx
import org.vertx.java.core.impl.EventLoopContext

trait AsyncConnectionPool[ConnType <: Connection] {

  def take(): Future[Connection]

  def giveBack(conn: Connection): Future[AsyncConnectionPool[ConnType]]

  def close: Future[AsyncConnectionPool[ConnType]]

  def withConnection[ResultType](fn: Connection => Future[ResultType])(implicit ec: ExecutionContext): Future[ResultType] = {
    val p = Promise[ResultType]
    take map { c: Connection =>
      try {
        println("got connection from pool")
        fn(c).onComplete {
          case Success(x) =>
            println("got a success -> connection back to pool")
            giveBack(c)
            p.success(x)
          case Failure(x) =>
            println("got a failure -> connection back to pool")
            giveBack(c)
            p.failure(x)
        }
      } catch {
        case ex: Throwable =>
          println("connection back into pool")
          giveBack(c)
          p.failure(ex)
      }
    } recover {
      case ex => p.failure(ex)
    }
    p.future
  }
}

object AsyncConnectionPool {

  def apply(vertx: Vertx, dbType: String, config: Configuration) = {
    dbType match {
      case "postgresql" =>
        new PostgreSqlAsyncConnectionPool(
          config,
          vertx.internal.currentContext().asInstanceOf[EventLoopContext].getEventLoop())
      case "mysql" =>
        new MySqlAsyncConnectionPool(config,
          vertx.internal.currentContext().asInstanceOf[EventLoopContext].getEventLoop())
      case _ => throw new NotImplementedError
    }
  }

}