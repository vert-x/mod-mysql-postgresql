package com.campudus.vertx.busmod

import scala.concurrent.Future

import org.vertx.java.core.json.JsonObject
import org.vertx.scala.core.eventbus.Message

import com.campudus.vertx.VertxExecutionContext
import com.campudus.vertx.messages.MessageHelper

trait ScalaBusMod extends MessageHelper with VertxExecutionContext with (Message[JsonObject] => Unit) {

  override def apply(msg: Message[JsonObject]) = {
    val action = msg.body.getString("action")

    System.err.println("ja hallo")

    val fut: Future[Reply] = try {
      if (receive(msg).isDefinedAt(action)) {
        val res = receive(msg).apply(action)
        System.err.println("got sync result:" + res)
        Future.successful(res)
      } else {
        System.err.println("got async...")
        asyncReceive(msg).applyOrElse(action, {
          str: String =>
            System.err.println("unknown action async result!")
            Future.failed(new UnknownActionException(action))
        })
      }
    } catch {
      // case ex: BusException => Future.failed(ex)
      case ex: Throwable =>
        System.err.println("there was an exception!")
        Future.failed(ex)
    }

    fut map { reply =>
      System.err.println("replying back to sender: " + reply.toJson)
      msg.reply(reply.toJson)
    } recover {
      // case x: BusException => msg.reply(new JsonObject().putString("status", "error").putString("message", x.getMessage()).putString("id", x.getId()))
      case x =>
        System.err.println("got an exception: " + x.getMessage())
        x.printStackTrace(System.err)
        msg.reply(new JsonObject().putString("status", "error").putString("message", x.getMessage()))
    }
  }

  def receive(msg: Message[JsonObject]): PartialFunction[String, Reply] = Map.empty
  def asyncReceive(msg: Message[JsonObject]): PartialFunction[String, Future[Reply]] = Map.empty
}