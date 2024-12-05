package com.example

import com.fasterxml.jackson.databind.ObjectMapper
import spark.Spark.*
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.mindrot.jbcrypt.BCrypt
import java.security.SecureRandom

fun main() {
    val objectMapper = jacksonObjectMapper()
    val dbUrl = "jdbc:sqlite:local.db"
    val connection = connectToDatabase(dbUrl)

    connection.createStatement().executeUpdate(
        """
        CREATE TABLE IF NOT EXISTS entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            username TEXT NOT NULL,
            password TEXT NOT NULL
        );
        """.trimIndent()
    )

    port(8080)

    get("/entries") { _, res ->
        res.type("application/json")
        fetchEntries(connection, objectMapper)
    }

    post("/entries") { req, res ->
        try {
            val jsonBody = req.body()
            val entry = objectMapper.readValue<Entry>(req.body())

            val finalPassword = if (entry.password == "genpass") {
                generateRandomPassword()
            } else {
                entry.password
            }

            val hashedPassword = hashPassword(finalPassword)


            insertEntry(connection, entry.name, entry.username, hashedPassword)
            res.status(201)
            return@post "Entry added successfully"
        } catch (e: Exception) {
            println("Error: ${e.message}")
            res.status(500)
            return@post "Internal Server Error: ${e.message}"
        }
    }
}

data class Entry(val name: String, val username: String, val password: String)

fun connectToDatabase(url: String): Connection {
    return DriverManager.getConnection(url)
}

fun fetchEntries(connection: Connection, objectMapper: ObjectMapper): String {
    val resultSet: ResultSet = connection.createStatement().executeQuery("SELECT * FROM entries;")
    val entries = mutableListOf<Map<String, Any>>()
    while (resultSet.next()) {
        entries.add(
            mapOf(
                "id" to resultSet.getInt("id"),
                "name" to resultSet.getString("name"),
                "username" to resultSet.getString("username"),
                "password" to resultSet.getString("password")
            )
        )
    }
    return objectMapper.writeValueAsString(entries)
}

fun insertEntry(connection: Connection, name: String, username: String, password: String) {
    val preparedStatement =
        connection.prepareStatement("INSERT INTO entries (name, username, password) VALUES (?, ?, ?);")
    preparedStatement.setString(1, name)
    preparedStatement.setString(2, username)
    preparedStatement.setString(3, password)
    preparedStatement.executeUpdate()
}

fun hashPassword(password: String): String {
    return BCrypt.hashpw(password, BCrypt.gensalt())
}

fun generateRandomPassword(): String {
    val length = 12
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val random = SecureRandom()
    val password = StringBuilder()
    for (i in 0 until length) {
        val index = random.nextInt(chars.length)
        password.append(chars[index])
    }

    return password.toString()
}