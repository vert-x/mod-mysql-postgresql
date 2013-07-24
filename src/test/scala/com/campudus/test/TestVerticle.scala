package com.campudus.test

import com.campudus.vertx.VertxExecutionContext
import scala.concurrent.Future
import org.vertx.java.core.json.JsonObject
import org.vertx.java.core.Handler
import org.vertx.java.core.AsyncResult
import org.vertx.testtools.VertxAssert
import org.vertx.scala.platform.Verticle
import org.junit.runner.RunWith
import org.vertx.testtools.JavaClassRunner
import java.lang.reflect.InvocationTargetException
import org.vertx.java.core.logging.impl.LoggerFactory
import org.vertx.scala.core.json.JSON

abstract class TestVerticle extends org.vertx.testtools.TestVerticle with VertxExecutionContext {

  private val log = LoggerFactory.getLogger(classOf[TestVerticle]);

  override def start() = {
    initialize()

    log.info("starting module " + System.getProperty("vertx.modulename"))

    container.deployModule(System.getProperty("vertx.modulename"), getConfig(), new Handler[AsyncResult[String]]() {
      override def handle(deploymentID: AsyncResult[String]) = {
        VertxAssert.assertNotNull("deploymentID should not be null", deploymentID.succeeded())

        before() map { _ =>
          log.info("starting tests")
          startTests()
        }
      }
    })

  }

  def before(): Future[Unit] = Future.successful()
  def getConfig(): JsonObject = new JsonObject()
}