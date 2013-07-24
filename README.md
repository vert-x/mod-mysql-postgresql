# vertx-mysql-postgres

This Vert.x module uses the https://github.com/mauricio/postgresql-async drivers to support an async module for MySQL and PostgreSQL.

## Requirements

* Vert.x 2.0.0

## Installation

`vertx install com.campudus.async-mysql-postgresql`

## Configuration

    {
      "connection" : <MySQL|PostgreSQL>,
      "host" : <your-host>,
      "port" : <your-port>,
      "username" : <your-username>,
      "password" : <your-password>,
      "database" : <name-of-your-database>
    }

* `address` - The address this module should register on the event bus. Defaults to `campudus.asyncdb`
* `connection` - The database you want to use. Defaults to `PostgreSQL`.
* `host` - The host of the database. Defaults to `localhost`.
* `port` - The port of the database. Defaults to `5432` for PostgreSQL and `3306` for MySQL.
* `username` - The username to connect to the database. Defaults to `postgres` for PostgreSQL and `root` for MySQL.
* `password` - The password to connect to the database. Default is not set, i.e. it uses no password.
* `database` - The name of the database you want to connect to. Defaults to `test`.
