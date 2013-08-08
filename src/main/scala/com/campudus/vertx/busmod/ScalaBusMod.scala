package com.campudus.vertx.busmod

import scala.concurrent.Future

import org.vertx.java.core.json.JsonObject
import org.vertx.scala.core.eventbus.Message

import com.campudus.vertx.VertxExecutionContext
import com.campudus.vertx.messages.MessageHelper

trait ScalaBusMod extends MessageHelper with VertxExecutionContext with (Message[JsonObject] => Unit) {

  override def apply(msg: Message[JsonObject]) = {
    val action = msg.body.getString("action")

    val fut: Future[Reply] = try {
      receive(msg).applyOrElse(action, {
        str: String => Future.failed(new UnknownActionException(action))
      })
    } catch {
      // case ex: BusException => Future.failed(ex)
      case ex: Throwable => Future.failed(ex)
    }

    fut map { reply => msg.reply(reply.toJson) } recover {
      // case x: BusException => msg.reply(new JsonObject().putString("status", "error").putString("message", x.getMessage()).putString("id", x.getId()))
      case x =>
        x.printStackTrace(System.err)
        msg.reply(new JsonObject().putString("status", "error").putString("message", x.getMessage()))
    }
  }

  def receive(msg: org.vertx.scala.core.eventbus.Message[JsonObject]): PartialFunction[String, Future[Reply]]
}