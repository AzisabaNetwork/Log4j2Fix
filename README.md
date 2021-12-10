# Log4j2Fix

This program fixes remote code execution vulnerability in log4j2 (v2.0.0 - v2.14.1).

## Usage
This program can be used as wrapper for another jar file:

`java -javaagent:Log4j2Fix-1.0.0.jar ...` or `java -jar Log4j2Fix-1.0.0.jar another-jar-file.jar [main class if MANIFEST.MF does not have Main-Class attribute] [arguments]`

## Note
- This does not protect from ldap server hosted by localhost (127.0.0.1)
- If installed on server, it does not protect client from being abused.
  To protect the client, you would need a different solution such as blocking a malicious packet.
- If installed on Minecraft server, you can protect the client by doing these additionally:
  - Cancel malicious `ChatEvent` on BungeeCord
  - Cancel malicious `AsyncPlayerChatEvent` on Spigot/Paper
  - Cancel outbound packet that contains malicious string
- Or alternatively, you can upgrade log4j2 to `2.15.0-SNAPSHOT`.
