package com.campudus.vertx.database

import com.campudus.vertx.Verticle
import com.github.mauricio.async.db.Configuration

class PostgreSqlConnectionHandler(val verticle: Verticle, val config: Configuration, val dbType: String = "postgresql") extends ConnectionHandler {
  
}