package com.example

import com.fasterxml.jackson.databind.ObjectMapper
import spark.Spark.*
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.mindrot.jbcrypt.BCrypt
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

fun main() {
    val objectMapper = jacksonObjectMapper()
    val dbUrl = "jdbc:sqlite:local.db"
    val connection = connectToDatabase(dbUrl)
    val secretKeyString = "Wd1JJ7GUi4T2KW3vJh/O0xH/3Gh2TO91rX1fZQknpdY="
    val secretKey = SecretKeySpec(Base64.getDecoder().decode(secretKeyString), "AES")

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
            val entry = objectMapper.readValue<Entry>(req.body())

            val finalPassword = if (entry.password == "genpass") {
                generateRandomPassword()
            } else {
                entry.password
            }

            val pwnedMessage = if (isPasswordPwned(finalPassword)) {
                "WARNING! This password was compromised in a known data breach!"
            } else {
                null
            }

            val encryptedPassword = encryptPassword(finalPassword, secretKey)

            insertEntry(connection, entry.name, entry.username, encryptedPassword)
            res.status(201)

            return@post if (pwnedMessage != null) {
                "Entry added successfully\n$pwnedMessage"
            } else {
                "Entry added successfully"
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
            res.status(500)
            return@post "Internal Server Error: ${e.message}"
        }
    }
    get("/check-strength/:id") { req, res ->
        res.type("application/json")
        try {
            val id = req.params(":id")
            return@get checkPasswordStrength(connection, objectMapper, id, secretKey)
        } catch (e: Exception) {
            e.printStackTrace()
            res.status(500)
            return@get "Internal Server Error: ${e.message}"
        }

    }
}

data class Entry(val name: String, val username: String, val password: String)

fun connectToDatabase(url: String): Connection {
    return DriverManager.getConnection(url)
}

// Get all entries in db
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

// Add entry to db
fun insertEntry(connection: Connection, name: String, username: String, password: String) {
    val preparedStatement = connection.prepareStatement(
        "INSERT INTO entries (name, username, password) VALUES (?, ?, ?);"
    )
    preparedStatement.setString(1, name)
    preparedStatement.setString(2, username)
    preparedStatement.setString(3, password)
    preparedStatement.executeUpdate()
}

fun checkPasswordStrength(connection: Connection, objectMapper: ObjectMapper, id: String, secretKey: SecretKeySpec): String {
    val resultSet: ResultSet = connection.createStatement().executeQuery(
        "SELECT password FROM entries WHERE id=$id"
    )
    val password = if (resultSet.next()) {
        resultSet.getString("password")
    } else {
        null
    }

    if (password == null){
        return objectMapper.writeValueAsString("No entry found with id #$id")
    }

    val decryptedPassword = decryptPassword(password, secretKey)

    var strength = 0
    if (decryptedPassword.any { it.isUpperCase() }) {
        strength++
    }
    if (decryptedPassword.any { it.isLowerCase() }) {
        strength++
    }
    if (decryptedPassword.any { it.isDigit() }) {
        strength++
    }
    if (decryptedPassword.any { !it.isLetterOrDigit() }) {
        strength++
    }
    if (decryptedPassword.length >= 8) {
        strength++
    }
    if (decryptedPassword.length >= 12) {
        strength++
    }

    val response = when (strength) {
        6 -> "$decryptedPassword is Very Strong"
        5 -> "$decryptedPassword is Strong"
        4 -> "$decryptedPassword is Moderate"
        else -> "$decryptedPassword is Weak"
    }
    return objectMapper.writeValueAsString(response)
}

fun encryptPassword(password: String, secretKey: SecretKeySpec): String {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val encryptedBytes = cipher.doFinal(password.toByteArray())
    return Base64.getEncoder().encodeToString(encryptedBytes)
}

fun decryptPassword(encryptedPassword: String, secretKey: SecretKeySpec): String {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, secretKey)
    val decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedPassword))
    return String(decryptedBytes)
}

// Generate random password of length 16 with at least:
// 1 uppercase, lowercase, digit and special character
fun generateRandomPassword(): String {
    val length = 16
    val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val lowercase = "abcdefghijklmnopqrstuvwxyz"
    val digits = "0123456789"
    val specialChars = "!@#$%^&*()-_+[]{}"
    val chars = uppercase + lowercase + digits + specialChars

    val random = SecureRandom()
    val password = StringBuilder()
    password.append(uppercase[random.nextInt(uppercase.length)])
    password.append(lowercase[random.nextInt(lowercase.length)])
    password.append(digits[random.nextInt(digits.length)])
    password.append(specialChars[random.nextInt(specialChars.length)])

    for (i in 4 until length) {
        password.append(chars[random.nextInt(chars.length)])
    }

    return password.toList().shuffled(random).joinToString("")
}

// Check if password exists in a known data breach
fun isPasswordPwned(password: String): Boolean {
    val sha1 = MessageDigest.getInstance("SHA-1")
        .digest(password.toByteArray())
        .joinToString("") { "%02x".format(it) }

    val prefix = sha1.substring(0, 5)
    val suffix = sha1.substring(5)

    val url = URL("https://api.pwnedpasswords.com/range/$prefix")

    val response = url.openConnection() as HttpURLConnection
    response.inputStream.bufferedReader().use {
        return it.readText().contains(suffix, ignoreCase = true)
    }
}