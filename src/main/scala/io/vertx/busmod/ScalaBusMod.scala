package io.vertx.busmod

import scala.concurrent.Future

import org.vertx.scala.core.VertxExecutionContext
import org.vertx.scala.core.eventbus.{ JsonObjectData, Message }
import org.vertx.scala.core.json.{ Json, JsonObject }

import io.vertx.asyncsql.messages.MessageHelper

trait ScalaBusMod extends MessageHelper with VertxExecutionContext with (Message[JsonObject] => Unit) {

  override def apply(msg: Message[JsonObject]) = {
    val action = msg.body.getString("action")

    val fut: Future[Reply] = try {
      if (receive(msg).isDefinedAt(action)) {
        val res = receive(msg).apply(action)
        Future.successful(res)
      } else if (asyncReceive(msg).isDefinedAt(action)) {
        asyncReceive(msg)(action)
      } else {
        Future.failed(new UnknownActionException("Unknown action: " + action))
      }
    } catch {
      // case ex: BusException => Future.failed(ex)
      case ex: Throwable =>
        Future.failed(ex)
    }

    fut map { reply =>
      msg.reply(reply.toJson)
    } recover {
      // case x: BusException => msg.reply(new JsonObject().putString("status", "error").putString("message", x.getMessage()).putString("id", x.getId()))
      case x =>
        x.printStackTrace(System.err)
        msg.reply(Json.obj("status" -> "error", "message" -> x.getMessage()))
    }
  }

  def receive(msg: Message[JsonObject]): PartialFunction[String, Reply] = Map.empty
  def asyncReceive(msg: Message[JsonObject]): PartialFunction[String, Future[Reply]] = Map.empty
}