package com.campudus.vertx

import java.net.URI
import scala.concurrent.{ Future, Promise }
import org.vertx.java.core.AsyncResult
import org.vertx.java.core.buffer.Buffer
import org.vertx.java.core.eventbus.Message
import org.vertx.java.core.file.AsyncFile
import org.vertx.java.core.http.HttpClientResponse
import org.vertx.java.core.json.JsonObject

trait VertxFutureHelpers extends VertxScalaHelpers {
  this: Verticle =>

  private def futurify[T](doSomething: Promise[T] => Unit) = {
    val promise = Promise[T]

    doSomething(promise)

    promise.future
  }

}
