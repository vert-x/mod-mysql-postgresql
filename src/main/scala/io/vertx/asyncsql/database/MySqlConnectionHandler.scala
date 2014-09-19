package io.vertx.asyncsql.database

import org.vertx.scala.platform.Verticle
import com.github.mauricio.async.db.Configuration
import io.vertx.asyncsql.Starter

class MySqlConnectionHandler(val verticle: Starter, val config: Configuration, val maxPoolSize: Int, val transactionTimeout: Long) extends ConnectionHandler {
  override val dbType: String = "mysql"

  override protected def escapeField(str: String): String = "`" + str.replace("`", "\\`") + "`"
  override protected def escapeString(str: String): String = "'" + str.replace("'", "''") + "'"

  //  override def transactionStart = "START TRANSACTION;"
  //  override def transactionEnd = ";COMMIT;"
  //  override def statementDelimiter = ";"
}