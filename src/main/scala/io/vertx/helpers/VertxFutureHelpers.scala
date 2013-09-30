package io.vertx.helpers

import scala.concurrent.Promise
import org.vertx.scala.platform.Verticle

trait VertxFutureHelpers extends VertxScalaHelpers {
  this: Verticle =>

  private def futurify[T](doSomething: Promise[T] => Unit) = {
    val promise = Promise[T]

    doSomething(promise)

    promise.future
  }

}
