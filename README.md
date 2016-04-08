#sdcf4j <a href="#"><img src="https://img.shields.io/badge/Version-1.0.2-brightgreen.svg" alt="Latest version"></a> <a href="http://ci.ketrwu.de/job/sdcf4j/de.btobastian.sdcf4j$sdcf4j-core/javadoc/"><img src="https://img.shields.io/badge/JavaDoc-latest-yellow.svg" alt="Latest JavaDocs"></a> <a href="https://github.com/BtoBastian/sdcf4j/wiki"><img src="https://img.shields.io/badge/Wiki-Home-red.svg" alt="Latest JavaDocs"></a>

#Maven
```xml
<repository>
  <id>sdcf4j-repo</id>
  <url>http://repo.bastian-oppermann.de</url>
</repository>
...
<!-- The core module -->
<dependency>
  <groupId>de.btobastian.sdcf4j</groupId>
  <artifactId>sdcf4j-core</artifactId>
  <version>1.0.2</version>
</dependency>
<!-- The module for your prefered lib-->
<dependency>
  <groupId>de.btobastian.sdcf4j</groupId>
  <!-- Possible artifact ids: sdcf4j-javacord, sdcf4j-jda, sdcf4j-discord4j -->
  <artifactId>sdcf4j-javacord</artifactId>
  <version>1.0.2</version>
</dependency>
```

## Support
 
* [Javacord server](https://discord.gg/0qJ2jjyneLEgG7y3)
* [DiscordAPI #java_javacord channel](https://discord.gg/0SBTUU1wZTVXVKEo)

You can find me on one of these servers/channels. Feel free to contact me if you need help. :)

#Download
For those of you how don't use maven: [Jenkins](http://ci.ketrwu.de/job/sdcf4j/lastSuccessfulBuild/)

Thanks to ketrwu (https://github.com/KennethWussmann).

#Javadocs
The javadocs can be found here: [JavaDocs](http://ci.ketrwu.de/job/sdcf4j/de.btobastian.sdcf4j$sdcf4j-core/javadoc/)

Thanks to ketrwu, too.

#Examples

Ping command:
```java
public class PingCommand implements CommandExecutor {

    @Command(aliases = {"!ping"}, description = "Pong!")
    public String onCommand(String command, String[] args) {
        return "Pong!";
    }

}
```

Parameters are completely dynamic, so all of this examples would work:
```java
// no parameters (Javacord, JDA and Discord4J)
@Command(aliases = {"!ping"}, description = "Pong!")
public String onCommand() {
    return "Pong!";
}

// DiscordAPI and Message as parameter (Javacord)
@Command(aliases = {"!ping"}, description = "Pong!")
public String onCommand(DiscordAPI api, Message message) {
    return "Pong!";
}

// only Message as parameter without return type (Javacord and JDA)
@Command(aliases = {"!ping"}, description = "Pong!")
public void onCommand(Message message) {
    message.reply("Pong!");
}

// no private messages and async (Javacord and JDA)
@Command(aliases = {"!channelInfo", "!ci"}, description = "Pong!", async = true, privateMessages = false)
public String onCommand(Channel channel) {
    return "You are in channel #" + channel.getName() + " with id " + channel.getId();
}
```

#Register a CommandExecutor

```java
// Javacord
CommandHandler cmdHandler = new JavacordHandler(api);
// JDA
CommandHandler cmdHandler = new JDAHandler(jda);
// Discord4J
CommandHandler cmdHandler = new Discord4JHandler(client);

// register the command
cmdHandler.registerCommand(new PingCommand());
```