package io.vertx.helpers

trait Verticle extends org.vertx.scala.platform.Verticle with VertxExecutionContext {

  lazy val logger = new Logger(container.logger())

}