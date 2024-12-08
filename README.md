# KotlinHttpServer
This project's purpose was for me to learn new libraries and technologies within the Java ecosystem. This project is NOT fit for use.  
  
## Libraries & Technologies
- Kotlin
- Spark (HTTP Server)
- SQLite JDBC
- SLF4J (Logging)
- Jackson (Parsing JSON & Object Mapper)
- JBCrypt (Hashing) => Eventually changed to AES Encryption
- javax.crypto (Encryption)
- Have I Been Pwned (HIBP) API

## How to start
- Ensure you have SQLite installed. You know it is installed when `sqlite3 --version` works in your cmd
- Use [this guide](https://dev.to/dendihandian/installing-sqlite3-in-windows-44eb) if you don't know how to install SQLite3  
- Install dependencies with `mvn install`
- Go to Main.kt and click the "run" icon next to main to start the HTTP Server
- To test the application, download the Postman Client or an alternative like Insomnia. You need the desktop client because the web version might not be able to access the localhost server.
