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
import sx.blah.discord.Discord4J;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.IListener;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.regex.Matcher;

/**
 * A command handler for the Discord4J library.
 */
public class Discord4JHandler extends CommandHandler {

    /**
     * Creates a new instance of this class.
     *
     * @param client The discord client.
     */
    public Discord4JHandler(IDiscordClient client) {
        client.getDispatcher().registerListener((IListener<MessageReceivedEvent>) this::handleMessageCreate);
    }

    /**
     * Adds a permission for the user.
     *
     * @param user The user.
     * @param permission The permission to add.
     */
    public void addPermission(IUser user, String permission) {
        addPermission(user.getStringID(), permission);
    }

    /**
     * Checks if the user has the required permission.
     *
     * @param user The user.
     * @param permission The permission to check.
     * @return If the user has the given permission.
     */
    public boolean hasPermission(IUser user, String permission) {
        return hasPermission(user.getStringID(), permission);
    }

    /**
     * Handles a received message.
     *
     * @param event The MessageReceivedEvent.
     */
    private void handleMessageCreate(final MessageReceivedEvent event) {
        String[] splitMessage = event.getMessage().getContent().split("[\\s&&[^\\n]]++");
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
            if (!matcher.find() || !matcher.group("id").equals(event.getClient().getOurUser().getStringID())) {
                return;
            }
        }
        if (event.getMessage().getChannel().isPrivate() && !commandAnnotation.privateMessages()) {
            return;
        }
        if (!event.getMessage().getChannel().isPrivate() && !commandAnnotation.channelMessages()) {
            return;
        }
        if (!hasPermission(event.getMessage().getAuthor(), commandAnnotation.requiredPermissions())) {
            if (Sdcf4jMessage.MISSING_PERMISSIONS.getMessage() != null) {
                try {
                    event.getMessage().getChannel().sendMessage(Sdcf4jMessage.MISSING_PERMISSIONS.getMessage());
                } catch (MissingPermissionsException | RateLimitException | DiscordException ignored) { }
            }
            return;
        }
        final Object[] parameters = getParameters(splitMessage, command, event);
        if (commandAnnotation.async()) {
            final SimpleCommand commandFinal = command;
            Thread t = new Thread(() -> {
                invokeMethod(commandFinal, event, parameters);
            });
            t.setDaemon(true);
            t.start();
        } else {
            invokeMethod(command, event, parameters);
        }
    }

    /**
     * Invokes the method of the command.
     *
     * @param command The command.
     * @param event The event.
     * @param parameters The parameters for the method.
     */
    private void invokeMethod(SimpleCommand command, MessageReceivedEvent event, Object[] parameters) {
        Method method = command.getMethod();
        Object reply = null;
        try {
            method.setAccessible(true);
            reply = method.invoke(command.getExecutor(), parameters);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Discord4J.LOGGER.warn("Cannot invoke method {}!", method.getName(), e);
        }
        if (reply != null) {
            try {
                event.getMessage().getChannel().sendMessage(String.valueOf(reply));
            } catch (MissingPermissionsException | RateLimitException | DiscordException ignored) { }
        }
    }

    /**
     * Gets the parameters which are used to invoke the executor's method.
     *
     * @param splitMessage The spit message (index 0: command, index > 0: arguments)
     * @param command The command.
     * @param event The event.
     * @return The parameters which are used to invoke the executor's method.
     */
    private Object[] getParameters(String[] splitMessage, SimpleCommand command, MessageReceivedEvent event) {
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
            } else if (type == MessageReceivedEvent.class) {
                parameters[i] = event;
            }  else if (type == IMessage.class) {
                parameters[i] = event.getMessage();
            } else if (type == IDiscordClient.class) {
                parameters[i] = event.getClient();
            } else if (type == IChannel.class) {
                parameters[i] = event.getMessage().getChannel();
            } else if (type == IUser.class) {
                parameters[i] = event.getMessage().getAuthor();
            } else if (type == IGuild.class) {
                parameters[i] = event.getMessage().getChannel().getGuild();
            } else if (type == Object[].class) {
                parameters[i] = getObjectsFromString(event.getClient(), args);
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
     * @param client The client.
     * @param args The string array.
     * @return An object array.
     */
    private Object[] getObjectsFromString(IDiscordClient client, String[] args) {
        Object[] objects = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            objects[i] = getObjectFromString(client, args[i]);
        }
        return objects;
    }

    /**
     * Tries to get an object (like channel, user, long) from the given string.
     *
     * @param client The client.
     * @param arg The string.
     * @return The object.
     */
    private Object getObjectFromString(IDiscordClient client, String arg) {
        try {
            // test long
            return Long.valueOf(arg);
        } catch (NumberFormatException ignored) {}
        // test user
        Matcher matcher = USER_MENTION.matcher(arg);
        if (matcher.find()) {
            String id = matcher.group("id");
            IUser user = client.getUserByID(Long.valueOf(id));
            if (user != null) {
                return user;
            }
        }
        // test channel
        if (arg.matches("<#([0-9]*)>")) {
            String id = arg.substring(2, arg.length() - 1);
            IChannel channel = client.getChannelByID(Long.valueOf(id));
            if (channel != null) {
                return channel;
            }
        }
        return arg;
    }

}
