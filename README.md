**⚠️ DEPRECATION WARNING ⚠️**

This project is no longer actively maintained.

**⚠️ DEPRECATION WARNING ⚠️**

# sdcf4j

Sdcf4j is a **s**imple **D**iscord **c**ommand **f**ramework **for** **J**ava, supporting [Javacord](https://github.com/BtoBastian/Javacord), [JDA](https://github.com/DV8FromTheWorld/JDA) and [Discord4J](https://github.com/austinv11/Discord4J). It helps you creating commands within seconds in a clean and simple way.

A ping command is as easy as this:
```java
@Command(aliases = "ping", description = "Pong!")
public String onPingCommand() {
    return "Pong!";
}
```

##  Maven
```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>
...
<!-- The core module -->
<dependency>
  <groupId>de.btobastian.sdcf4j</groupId>
  <artifactId>sdcf4j-core</artifactId>
  <version>%version%</version>
</dependency>
<!-- The module for your preferred lib -->
<dependency>
  <groupId>de.btobastian.sdcf4j</groupId>
  <!-- Possible artifact ids: sdcf4j-javacord, sdcf4j-jda3, sdcf4j-discord4j -->
  <artifactId>sdcf4j-javacord</artifactId>
  <version>%version%</version>
</dependency>
```
Make sure to replace `%version%` with the latest version number, e.g. `v1.0.0` (don't use this one!).

Latest version: <a href="#"><img src="https://shields.javacord.org/github/release/BtoBastian/sdcf4j.svg?colorB=brightgreen&label=Version" alt="Latest version"></a>
## Support
 
* [Javacord server](https://discord.gg/0qJ2jjyneLEgG7y3)
* [DiscordAPI #java_javacord channel](https://discord.gg/0SBTUU1wZTVXVKEo)

You can find me in one of these servers/channels. Feel free to contact me if you need help. :)

## Download
For those of you how don't use maven: [Jenkins](http://ci.ketrwu.de/job/sdcf4j/branch/master/lastSuccessfulBuild/)

Thanks to ketrwu (https://github.com/KennethWussmann).

## Javadocs
The javadocs can be found here: [JavaDocs](http://ci.ketrwu.de/job/sdcf4j/job/master/javadoc/)

Thanks to ketrwu, too.

## Tutorial

Take a look at the [wiki](https://github.com/BtoBastian/sdcf4j/wiki) for a detailed description on how to use the library.

## Examples

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
public String onCommand(DiscordApi api, Message message) {
    return "Pong!";
}

// no private messages and async (Javacord and JDA)
@Command(aliases = {"!channelInfo", "!ci"}, description = "Pong!", async = true, privateMessages = false)
public String onCommand(Channel channel) {
    return "You are in channel #" + channel.getName() + " with id " + channel.getId();
}
```

## Register a CommandExecutor

```java
// Javacord
CommandHandler cmdHandler = new JavacordHandler(api);
// JDA3
CommandHandler cmdHandler = new JDA3Handler(jda);
// Discord4J
CommandHandler cmdHandler = new Discord4JHandler(client);

// register the command
cmdHandler.registerCommand(new PingCommand());
```

