/*
 * Copyright (C) 2016 Bastian Oppermann
 * 
 * This file is part of SDCF4J.
 * 
 * Javacord is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser general Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 * 
 * SDCF4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, see <http://www.gnu.org/licenses/>.
 */
package de.btobastian.sdcf4j.handler;

import de.btobastian.sdcf4j.Command;
import de.btobastian.sdcf4j.CommandHandler;
import de.btobastian.sdcf4j.Sdcf4jMessage;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.GroupChannel;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.core.util.logging.LoggerUtil;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.regex.Matcher;

/**
 * A command handler for the Javacord library.
 */
public class JavacordHandler extends CommandHandler {

    /**
     * The logger of this class.
     */
    private static final Logger logger = LoggerUtil.getLogger(JavacordHandler.class);

    /**
     * Creates a new instance of this class.
     *
     * @param api The api.
     */
    public JavacordHandler(DiscordApi api) {
        api.addMessageCreateListener(event -> handleMessageCreate(api, event));
    }

    /**
     * Adds a permission for the user.
     *
     * @param user The user.
     * @param permission The permission to add.
     */
    public void addPermission(User user, String permission) {
        addPermission(String.valueOf(user.getId()), permission);
    }

    /**
     * Checks if the user has the required permission.
     *
     * @param user The user.
     * @param permission The permission to check.
     * @return If the user has the given permission.
     */
    public boolean hasPermission(User user, String permission) {
        return hasPermission(String.valueOf(user.getId()), permission);
    }

    /**
     * Handles a received message.
     *
     * @param api The api.
     * @param event The received event.
     */
    private void handleMessageCreate(DiscordApi api, final MessageCreateEvent event) {
        Message message = event.getMessage();
        if (message.getUserAuthor().map(User::isYourself).orElse(false)) {
            return;
        }
        String[] splitMessage = message.getContent().split("[\\s&&[^\\n]]++");
        String commandString = splitMessage[0];
        SimpleCommand command = commands.get(commandString.toLowerCase());
        if (command == null) {
            // maybe it requires a mention
            if (splitMessage.length > 1) {
                command = commands.get(splitMessage[1].toLowerCase());
                if (command == null || !command.getCommandAnnotation().requiresMention()) {
                    return;
                }
                // remove the first which is the mention
                splitMessage = Arrays.copyOfRange(splitMessage, 1, splitMessage.length);
            } else {
                return;
            }
        }
        Command commandAnnotation = command.getCommandAnnotation();
        if (commandAnnotation.requiresMention()) {
            Matcher matcher = USER_MENTION.matcher(commandString);
            if (!matcher.find() || !matcher.group("id").equals(api.getYourself().getIdAsString())) {
                return;
            }
        }
        if (message.getPrivateChannel().isPresent() && !commandAnnotation.privateMessages()) {
            return;
        }
        if (!message.getPrivateChannel().isPresent() && !commandAnnotation.channelMessages()) {
            return;
        }
        if (!hasPermission(message.getUserAuthor().map(User::getId).map(String::valueOf).orElse("-1"), commandAnnotation.requiredPermissions())) {
            if (Sdcf4jMessage.MISSING_PERMISSIONS.getMessage() != null) {
                message.getChannel().sendMessage(Sdcf4jMessage.MISSING_PERMISSIONS.getMessage());
            }
            return;
        }
        final Object[] parameters = getParameters(splitMessage, command, event, api);
        if (commandAnnotation.async()) {
            final SimpleCommand commandFinal = command;
            api.getThreadPool().getExecutorService().submit(() -> invokeMethod(commandFinal, message, parameters));
        } else {
            invokeMethod(command, message, parameters);
        }
    }

    /**
     * Invokes the method of the command.
     *
     * @param command The command.
     * @param message The original message.
     * @param parameters The parameters for the method.
     */
    private void invokeMethod(SimpleCommand command, Message message, Object[] parameters) {
        Method method = command.getMethod();
        Object reply = null;
        try {
            method.setAccessible(true);
            reply = method.invoke(command.getExecutor(), parameters);
        } catch (Exception e) {
            logger.warn("An error occurred while invoking method {}!", method.getName(), e);
        }
        if (reply != null) {
            message.getChannel().sendMessage(String.valueOf(reply));
        }
    }

    /**
     * Gets the parameters which are used to invoke the executor's method.
     *
     * @param splitMessage The spit message (index 0: command, index > 0: arguments)
     * @param command The command.
     * @param event The received event.
     * @param api The api.
     * @return The parameters which are used to invoke the executor's method.
     */
    private Object[] getParameters(String[] splitMessage, SimpleCommand command, MessageCreateEvent event, DiscordApi api) {
        Message message = event.getMessage();
        String[] args = Arrays.copyOfRange(splitMessage, 1, splitMessage.length);
        Class<?>[] parameterTypes = command.getMethod().getParameterTypes();
        final Object[] parameters = new Object[parameterTypes.length];
        int stringCounter = 0;
        for (int i = 0; i < parameterTypes.length; i++) { // check all parameters
            Class<?> type = parameterTypes[i];
            if (type == String.class) {
                if (stringCounter++ == 0) {
                    parameters[i] = splitMessage[0]; // the first split is the command
                } else {
                    if (args.length + 2 > stringCounter) {
                        // the first string parameter is the command, the other ones are the arguments
                        parameters[i] = args[stringCounter - 2];
                    }
                }
            } else if (type == String[].class) {
                parameters[i] = args;
            } else if (type == MessageCreateEvent.class) {
                parameters[i] = event;
            } else if (type == Message.class) {
                parameters[i] = message;
            } else if (type == DiscordApi.class) {
                parameters[i] = api;
            } else if (type == Channel.class) {
                parameters[i] = message.getChannel();
            } else if (type == GroupChannel.class) {
                parameters[i] = message.getChannel().asGroupChannel().orElse(null);
            } else if (type == PrivateChannel.class) {
                parameters[i] = message.getChannel().asPrivateChannel().orElse(null);
            } else if (type == ServerChannel.class) {
                parameters[i] = message.getChannel().asServerChannel().orElse(null);
            } else if (type == ServerTextChannel.class) {
                parameters[i] = message.getChannel().asServerTextChannel().orElse(null);
            } else if (type == TextChannel.class) {
                parameters[i] = message.getChannel().asTextChannel().orElse(null);
            } else if (type == User.class) {
                parameters[i] = message.getUserAuthor().orElse(null);
            } else if (type == MessageAuthor.class) {
                parameters[i] = message.getAuthor();
            }  else if (type == Server.class) {
                parameters[i] = message.getServerTextChannel().map(ServerTextChannel::getServer).orElse(null);
            } else if (type == Object[].class) {
                parameters[i] = getObjectsFromString(api, args);
            } else {
                // unknown type
                parameters[i] = null;
            }
        }
        return parameters;
    }

    /**
     * Tries to get objects (like channel, user, long) from the given strings.
     *
     * @param api The api.
     * @param args The string array.
     * @return An object array.
     */
    private Object[] getObjectsFromString(DiscordApi api, String[] args) {
        Object[] objects = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            objects[i] = getObjectFromString(api, args[i]);
        }
        return objects;
    }

    /**
     * Tries to get an object (like channel, user, long) from the given string.
     *
     * @param api The api.
     * @param arg The string.
     * @return The object.
     */
    private Object getObjectFromString(DiscordApi api, String arg) {
        try {
            // test long
            return Long.valueOf(arg);
        } catch (NumberFormatException ignored) {}
        // test user
        Matcher matcher = USER_MENTION.matcher(arg);
        if (matcher.find()) {
            String id = matcher.group("id");
            User user = api.getCachedUserById(id).orElse(null);
            if (user != null) {
                return user;
            }
        }
        // test channel
        if (arg.matches("<#([0-9]*)>")) {
            String id = arg.substring(2, arg.length() - 1);
            Channel channel = api.getChannelById(id).orElse(null);
            if (channel != null) {
                return channel;
            }
        }
        return arg;
    }

}
