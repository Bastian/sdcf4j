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

import de.btobastian.sdcf4j.CommandHandler;
import de.btobastian.sdcf4j.Sdcf4jMessage;
import sx.blah.discord.Discord4J;
import sx.blah.discord.api.DiscordException;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.MissingPermissionsException;
import sx.blah.discord.handle.EventSubscriber;
import sx.blah.discord.handle.IListener;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.HTTP429Exception;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

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
        client.getDispatcher().registerListener(new IListener<MessageReceivedEvent> () {
            @Override
            public void handle(MessageReceivedEvent event) {
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
    public void addPermission(IUser user, String permission) {
        addPermission(user.getID(), permission);
    }

    /**
     * Checks if the user has the required permission.
     *
     * @param user The user.
     * @param permission The permission to check.
     * @return If the user has the given permission.
     */
    public boolean hasPermission(IUser user, String permission) {
        return hasPermission(user.getID(), permission);
    }

    /**
     * Handles a received message.
     *
     * @param event The MessageReceivedEvent.
     */
    private void handleMessageCreate(final MessageReceivedEvent event) {
        String[] splitMessage = event.getMessage().getContent().split(" ");
        String commandString = splitMessage[0];
        final SimpleCommand command = commands.get(commandString.toLowerCase());
        System.out.print(commandString);
        if (command == null) {
            return;
        }
        if (event.getMessage().getChannel().isPrivate() && !command.getCommandAnnotation().privateMessages()) {
            return;
        }
        if (!event.getMessage().getChannel().isPrivate() && !command.getCommandAnnotation().channelMessages()) {
            return;
        }
        if (!hasPermission(event.getMessage().getAuthor(), command.getCommandAnnotation().requiredPermissions())) {
            if (Sdcf4jMessage.MISSING_PERMISSIONS.getMessage() != null) {
                try {
                    event.getMessage().getChannel().sendMessage(Sdcf4jMessage.MISSING_PERMISSIONS.getMessage());
                } catch (MissingPermissionsException | HTTP429Exception | DiscordException ignored) { }
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
            } else if (type == IMessage.class) {
                parameters[i] = event.getMessage();
            } else if (type == IDiscordClient.class) {
                parameters[i] = event.getClient();
            } else if (type == IChannel.class) {
                parameters[i] = event.getMessage().getChannel();
            } else if (type == IUser.class) {
                parameters[i] = event.getMessage().getAuthor();
            } else if (type == IGuild.class) {
                parameters[i] = event.getMessage().getChannel().getGuild();
            } else {
                // unknown type
                parameters[i] = null;
            }
        }
        if (command.getCommandAnnotation().async()) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    Object reply = null;
                    try {
                        reply = method.invoke(command.getExecutor(), parameters);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        Discord4J.LOGGER.warn("Cannot invoke method {}!", method.getName(), e);
                    }
                    if (reply != null) {
                        try {
                            event.getMessage().getChannel().sendMessage(String.valueOf(reply));
                        } catch (MissingPermissionsException | HTTP429Exception | DiscordException ignored) { }
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        } else {
            Object reply = null;
            try {
                reply = method.invoke(command.getExecutor(), parameters);
            } catch (IllegalAccessException | InvocationTargetException e) {
                Discord4J.LOGGER.warn("Cannot invoke method {}!", method.getName(), e);
            }
            if (reply != null) {
                try {
                    event.getMessage().reply(String.valueOf(reply));
                } catch (MissingPermissionsException | HTTP429Exception | DiscordException ignored) { }
            }
        }
    }

}
