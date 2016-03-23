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

import de.btobastian.javacord.DiscordAPI;
import de.btobastian.javacord.entities.Channel;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.entities.message.MessageReceiver;
import de.btobastian.javacord.listener.message.MessageCreateListener;
import de.btobastian.javacord.utils.LoggerUtil;
import de.btobastian.sdcf4j.CommandHandler;
import de.btobastian.sdcf4j.Sdcf4jMessage;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

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
    public JavacordHandler(DiscordAPI api) {
        api.registerListener(new MessageCreateListener() {
            @Override
            public void onMessageCreate(DiscordAPI api, Message message) {
                handleMessageCreate(api, message);
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
     * @param api The api.
     * @param message The received message.
     */
    private void handleMessageCreate(DiscordAPI api, final Message message) {
        if (message.getAuthor().isYourself()) {
            return;
        }
        String[] splitMessage = message.getContent().split(" ");
        String commandString = splitMessage[0];
        final SimpleCommand command = commands.get(commandString.toLowerCase());
        if (command == null) {
            return;
        }
        if (message.isPrivateMessage() && !command.getCommandAnnotation().privateMessages()) {
            return;
        }
        if (!message.isPrivateMessage() && !command.getCommandAnnotation().channelMessages()) {
            return;
        }
        if (!hasPermission(message.getAuthor(), command.getCommandAnnotation().requiredPermissions())) {
            if (Sdcf4jMessage.MISSING_PERMISSIONS.getMessage() != null) {
                message.reply(Sdcf4jMessage.MISSING_PERMISSIONS.getMessage());
            }
            return;
        }
        String[] args = Arrays.copyOfRange(splitMessage, 1, splitMessage.length);
        final Method method = command.getMethod();
        Class<?>[] parameterTypes = method.getParameterTypes();
        final Object[] parameters = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) { // check all parameters
            Class<?> type = parameterTypes[i];
            if (type == String.class) {
                parameters[i] = commandString;
            } else if (type == String[].class) {
                parameters[i] = args;
            } else if (type == Message.class) {
                parameters[i] = message;
            } else if (type == DiscordAPI.class) {
                parameters[i] = api;
            } else if (type == Channel.class) {
                parameters[i] = message.getChannelReceiver();
            } else if (type == User.class) {
                parameters[i] = message.getAuthor();
            } else if (type == MessageReceiver.class) {
                parameters[i] = message.getReceiver();
            } else {
                // unknown type
                parameters[i] = null;
            }
        }
        if (command.getCommandAnnotation().async()) {
            api.getThreadPool().getExecutorService().submit(new Runnable() {
                @Override
                public void run() {
                    Object reply = null;
                    try {
                        reply = method.invoke(command.getExecutor(), parameters);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        logger.warn("Cannot invoke method {}!", method.getName(), e);
                    }
                    if (reply != null && reply instanceof String) {
                        message.reply((String) reply);
                    }
                }
            });
        } else {
            Object reply = null;
            try {
                reply = method.invoke(command.getExecutor(), parameters);
            } catch (IllegalAccessException | InvocationTargetException e) {
                logger.warn("Cannot invoke method {}!", method.getName(), e);
            }
            if (reply != null && reply instanceof String) {
                message.reply((String) reply);
            }
        }
    }

}
