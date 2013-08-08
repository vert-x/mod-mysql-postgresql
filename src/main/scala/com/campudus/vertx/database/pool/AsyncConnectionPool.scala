package com.campudus.vertx.database.pool

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
        fn(c).onComplete {
          case Success(x) => p.success(x)
          case Failure(x) => p.failure(x)
        }
      } catch {
        case ex: Throwable => p.failure(ex)
      } finally {
        giveBack(c)
      }
    }
    p.future
  }
}

object AsyncConnectionPool {

  def apply(vertx: Vertx, dbType: String, config: Configuration) = dbType match {
    case "postgresql" =>
      new PostgreSQLAsyncConnectionPool(
        config,
        vertx.internal.currentContext().asInstanceOf[EventLoopContext].getEventLoop())
    case _ => throw new NotImplementedError
  }

}