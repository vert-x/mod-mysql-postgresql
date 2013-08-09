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
import org.vertx.scala.core.Vertx
import com.campudus.vertx.Verticle

class ConnectionHandler(verticle: Verticle, dbType: String, config: Configuration) extends ScalaBusMod {
  val pool = AsyncConnectionPool(verticle.vertx, dbType, config)

  override def asyncReceive(msg: Message[JsonObject]) = {
    case "query" =>
      verticle.container.logger.info("got a query")
      pool.withConnection({ c: Connection =>
        verticle.container.logger.info("got a connection")
        c.sendQuery(msg.body.getString("query")) map { qr =>
          qr.rows match {
            case Some(resultSet) =>
              verticle.container.logger.info("got a Some: " + resultSet)
              Ok(new JsonObject().putString("result", resultSet.head(0).toString))
            case None =>
              verticle.container.logger.info("got a None")
              Error("Nothing returned.")
          }
        }
      })
  }

  def close() = pool.close

}