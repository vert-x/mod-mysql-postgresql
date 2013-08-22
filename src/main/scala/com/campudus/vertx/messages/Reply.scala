package com.campudus.vertx.messages

import org.vertx.java.core.json.JsonObject

trait MessageHelper {
  sealed trait Reply {
    def toJson: JsonObject
  }
  case class Ok(x: JsonObject) extends Reply {
    def toJson = x.mergeIn(new JsonObject().putString("status", "ok"))
  }
  case class Error(message: String, id: Option[String] = None, obj: Option[JsonObject] = None) extends Reply {
    def toJson = {
      val js = obj.getOrElse(new JsonObject()).putString("status", "error").putString("message", message)
      id map (x => js.putString("id", x))
      js
    }
  }
}
