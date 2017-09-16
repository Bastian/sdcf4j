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

import de.btobastian.javacord.DiscordApi;
import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.channels.TextChannel;
import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.entities.message.Messageable;
import de.btobastian.javacord.utils.logging.LoggerUtil;
import de.btobastian.sdcf4j.Command;
import de.btobastian.sdcf4j.CommandHandler;
import de.btobastian.sdcf4j.Sdcf4jMessage;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

/**
 * A command handler for the Javacord library.
 */
public class Javacord3Handler extends CommandHandler {

    /**
     * The logger of this class.
     */
    private static final Logger logger = LoggerUtil.getLogger(Javacord3Handler.class);

    /**
     * Creates a new instance of this class.
     *
     * @param api The api.
     */
    public Javacord3Handler(DiscordApi api) {
        api.addMessageCreateListener(event -> handleMessageCreate(event.getApi(), event.getMessage()));
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
     * @param message The received message.
     */
    private void handleMessageCreate(DiscordApi api, final Message message) {
        if (message.getAuthor().isPresent() && message.getAuthor().get().isYourself()) {
            return;
        }
        String[] splitMessage = message.getContent().split(" ");
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
        if (commandAnnotation.requiresMention() && !commandString.equals(api.getYourself().getMentionTag())) {
            return;
        }
        if (message.getPrivateChannel().isPresent() && !commandAnnotation.privateMessages()) {
            return;
        }
        if (!message.getServerTextChannel().isPresent() && !commandAnnotation.channelMessages()) {
            return;
        }
        if (!hasPermission(message.getAuthor().get(), commandAnnotation.requiredPermissions())) {
            if (Sdcf4jMessage.MISSING_PERMISSIONS.getMessage() != null) {
                message.getServerTextChannel().ifPresent(serverTextChannel -> serverTextChannel.sendMessage(String.valueOf(Sdcf4jMessage.MISSING_PERMISSIONS.getMessage())));
                message.getGroupChannel().ifPresent(groupTextChannel -> groupTextChannel.sendMessage(String.valueOf(Sdcf4jMessage.MISSING_PERMISSIONS.getMessage())));
                message.getPrivateChannel().ifPresent(privateTextChannel -> privateTextChannel.sendMessage(String.valueOf(Sdcf4jMessage.MISSING_PERMISSIONS.getMessage())));
            }
            return;
        }
        final Object[] parameters = getParameters(splitMessage, command, message, api);
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
            Object finalReply = reply;
            message.getServerTextChannel().ifPresent(serverTextChannel -> serverTextChannel.sendMessage(String.valueOf(finalReply)));
            message.getGroupChannel().ifPresent(groupTextChannel -> groupTextChannel.sendMessage(String.valueOf(finalReply)));
            message.getPrivateChannel().ifPresent(privateTextChannel -> privateTextChannel.sendMessage(String.valueOf(finalReply)));
        }
    }

    /**
     * Gets the parameters which are used to invoke the executor's method.
     *
     * @param splitMessage The spit message (index 0: command, index > 0: arguments)
     * @param command The command.
     * @param message The original message.
     * @param api The api.
     * @return The parameters which are used to invoke the executor's method.
     */
    private Object[] getParameters(String[] splitMessage, SimpleCommand command, final Message message, DiscordApi api) {
        String[] args = Arrays.copyOfRange(splitMessage, 1, splitMessage.length);
        Class<?>[] parameterTypes = command.getMethod().getParameterTypes();
        final Object[] parameters = new Object[parameterTypes.length];
        int stringCounter = 0;
        for (int i = 0; i < parameterTypes.length; i++) { // check all parameters
            final int index = i;
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
            } else if (type == Message.class) {
                parameters[i] = message;
            } else if (type == DiscordApi.class) {
                parameters[i] = api;
            } else if (type == TextChannel.class || type == Messageable.class) {
                message.getServerTextChannel().ifPresent(serverTextChannel -> parameters[index] = serverTextChannel);
                message.getPrivateChannel().ifPresent(privateTextChannel -> parameters[index] = privateTextChannel);
                message.getGroupChannel().ifPresent(groupTextChannel -> parameters[index] = groupTextChannel);
            } else if (type == User.class) {
                message.getAuthor().ifPresent(user -> parameters[index] = user);
            }  else if (type == Server.class) {
                message.getServerTextChannel().ifPresent(serverTextChannel -> parameters[index] = serverTextChannel.getServer());
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
     * Tries to get objects (like channel, user, integer) from the given strings.
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
     * Tries to get an object (like channel, user, integer) from the given string.
     *
     * @param api The api.
     * @param arg The string.
     * @return The object.
     */
    private Object getObjectFromString(DiscordApi api, String arg) {

        // test integer
        if(arg.matches("^-?\\d+$")) {
            return Integer.valueOf(arg);
        }

        // test user
        final String userTag = arg.replace("!", "");
        if (userTag.matches("<@([0-9]*)>")) {
            String id = userTag.substring(2, userTag.length() - 1);
            Optional<User> user = api.getUserById(id);
            if(user.isPresent())
                return user.get();
        }

        // test channel
        if (arg.matches("<#([0-9]*)>")) {
            String id = arg.substring(2, arg.length() - 1);
            Optional<TextChannel> channel = api.getTextChannelById(id);
            if (channel.isPresent()) {
                return channel.get();
            }
        }
        return arg;
    }

}
