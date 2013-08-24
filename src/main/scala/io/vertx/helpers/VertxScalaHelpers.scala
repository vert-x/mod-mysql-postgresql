package io.vertx.helpers

import org.vertx.java.core.Handler
import org.vertx.java.core.AsyncResult
import org.vertx.java.core.AsyncResultHandler
import org.vertx.scala.core.json._
import scala.concurrent.Future

trait VertxScalaHelpers {
  def json() = new JsonObject()

  import scala.language.implicitConversions
  implicit def fnToHandler[T <% X, X](fn: T => Any): Handler[X] = new Handler[X]() {
    override def handle(event: X) = fn(event.asInstanceOf[T])
  }

  implicit def noParameterFunctionToSimpleHandler(fn: () => Any): Handler[Void] = new Handler[Void]() {
    override def handle(v: Void) = fn()
  }

  implicit def fnToAsyncHandler[T](fn: AsyncResult[T] => Any): AsyncResultHandler[T] = new AsyncResultHandler[T]() {
    override def handle(result: AsyncResult[T]) = fn(result)
  }

  def tryOp[T](f: => T): Option[T] = try { Some(f) } catch { case _: Throwable => None }
  def toInt(s: String): Option[Int] = tryOp(s.toInt)

  implicit def listToJsonArray[X](list: List[X]): JsonArray = {
    Json.arr(list)
  }

}

object VertxScalaHelpers extends VertxScalaHelpers
