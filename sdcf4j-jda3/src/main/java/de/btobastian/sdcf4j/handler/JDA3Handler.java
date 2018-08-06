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
import net.dv8tion.jda.client.entities.Group;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;

/**
 * A command handler for the JDA library.
 */
public class JDA3Handler extends CommandHandler {

    /**
     * The logger of this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(JDA3Handler.class);

    /**
     * Creates a new instance of this class.
     *
     * @param jda A JDA instance.
     */
    public JDA3Handler(JDA jda) {
        jda.addEventListener(new ListenerAdapter() {
            @Override
            public void onMessageReceived(MessageReceivedEvent event) {
                handleMessageCreate(event);
            }
        });
    }

    /**
     * Adds a permission for the user.
     *
     * @param user The user.
     * @param permission The permission to add.
     */
    public void addPermission(User user, String permission) {
        addPermission(user.getId(), permission);
    }

    /**
     * Checks if the user has the required permission.
     *
     * @param user The user.
     * @param permission The permission to check.
     * @return If the user has the given permission.
     */
    public boolean hasPermission(User user, String permission) {
        return hasPermission(user.getId(), permission);
    }

    /**
     * Handles a received message.
     *
     * @param event The MessageReceivedEvent.
     */
    private void handleMessageCreate(final MessageReceivedEvent event) {
        JDA jda = event.getJDA();
        if (event.getAuthor() == jda.getSelfUser()) {
            return;
        }
        String[] splitMessage = event.getMessage().getContentRaw().split("[\\s&&[^\\n]]++");
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
            if (!matcher.find() || !matcher.group("id").equals(jda.getSelfUser().getId())) {
                return;
            }
        }
        if (event.isFromType(ChannelType.PRIVATE) && !commandAnnotation.privateMessages()) {
            return;
        }
        if (!event.isFromType(ChannelType.PRIVATE) && !commandAnnotation.channelMessages()) {
            return;
        }
        if (!hasPermission(event.getAuthor(), commandAnnotation.requiredPermissions())) {
            if (Sdcf4jMessage.MISSING_PERMISSIONS.getMessage() != null) {
                event.getChannel().sendMessage(Sdcf4jMessage.MISSING_PERMISSIONS.getMessage()).queue();
            }
            return;
        }
        final Object[] parameters = getParameters(splitMessage, command, event);
        if (commandAnnotation.async()) {
            final SimpleCommand commandFinal = command;
            Thread t = new Thread(() -> invokeMethod(commandFinal, event, parameters));
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
            logger.warn("An error occurred while invoking method {}!", method.getName(), e);
        }
        if (reply != null) {
            event.getChannel().sendMessage(String.valueOf(reply)).queue();
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
            } else if (type == JDA.class) {
                parameters[i] = event.getJDA();
            } else if (type == MessageChannel.class) {
                parameters[i] = event.getChannel();
            } else if (type == Message.class) {
                parameters[i] = event.getMessage();
            } else if (type == User.class) {
                parameters[i] = event.getAuthor();
            } else if (type == Member.class) {
                parameters[i] = event.getMember();
            } else if (type == TextChannel.class) {
                parameters[i] = event.getTextChannel();
            } else if (type == PrivateChannel.class) {
                parameters[i] = event.getPrivateChannel();
            } else if (type == Channel.class) {
                parameters[i] = event.getTextChannel();
            } else if (type == Group.class) {
                parameters[i] = event.getGroup();
            } else if (type == Guild.class) {
                parameters[i] = event.getGuild();
            } else if (type == Integer.class || type == int.class) {
                parameters[i] = event.getResponseNumber();
            } else if (type == Object[].class) {
                parameters[i] = getObjectsFromString(event.getJDA(), args);
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
     * @param jda The jda object.
     * @param args The string array.
     * @return An object array.
     */
    private Object[] getObjectsFromString(JDA jda, String[] args) {
        Object[] objects = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            objects[i] = getObjectFromString(jda, args[i]);
        }
        return objects;
    }

    /**
     * Tries to get an object (like channel, user, long) from the given string.
     *
     * @param jda The jda object.
     * @param arg The string.
     * @return The object.
     */
    private Object getObjectFromString(JDA jda, String arg) {
        try {
            // test long
            return Long.valueOf(arg);
        } catch (NumberFormatException ignored) {}
        // test user
        Matcher matcher = USER_MENTION.matcher(arg);
        if (matcher.find()) {
            String id = matcher.group("id");
            User user = jda.getUserById(id);
            if (user != null) {
                return user;
            }
        }
        // test channel
        if (arg.matches("<#([0-9]*)>")) {
            String id = arg.substring(2, arg.length() - 1);
            Channel channel = jda.getTextChannelById(id);
            if (channel != null) {
                return channel;
            }
        }
        return arg;
    }

}
