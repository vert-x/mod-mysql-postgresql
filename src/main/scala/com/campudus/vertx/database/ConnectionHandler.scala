package com.campudus.vertx.database

import org.vertx.scala.core.eventbus.Message
import org.vertx.java.core.json.JsonObject
import com.github.mauricio.async.db.Configuration
import com.campudus.vertx.database.pool.PostgreSQLAsyncConnectionPool
import com.campudus.vertx.VertxExecutionContext
import com.github.mauricio.async.db.Connection
import com.campudus.vertx.busmod.ScalaBusMod
import scala.concurrent.Future
import com.campudus.vertx.database.pool.AsyncConnectionPool

class ConnectionHandler(dbType: String, config: Configuration) extends ScalaBusMod {

  val pool = dbType match {
    case "postgresql" => new PostgreSQLAsyncConnectionPool(config)
    case _ => ???
  }

  override def receive(msg: Message[JsonObject]) = {
    case "query" => pool.withConnection({ c: Connection =>
      c.sendQuery(msg.body.getString("query")) map { qr =>
        qr.rows match {
          case Some(resultSet) => Ok(new JsonObject().putString("result", resultSet.head(0).toString))
          case None => Error("Nothing returned.")
        }
      }
    })
  }

  def close() = pool.close

}