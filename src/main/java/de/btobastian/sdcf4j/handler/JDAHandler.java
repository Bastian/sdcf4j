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
import net.dv8tion.jda.JDA;
import net.dv8tion.jda.entities.*;
import net.dv8tion.jda.events.Event;
import net.dv8tion.jda.events.message.MessageReceivedEvent;
import net.dv8tion.jda.hooks.EventListener;
import net.dv8tion.jda.utils.SimpleLog;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * A command handler for the JDA library.
 */
public class JDAHandler extends CommandHandler {

    /**
     * Creates a new instance of this class.
     *
     * @param jda A JDA instance.
     */
    public JDAHandler(JDA jda) {
        jda.addEventListener(new EventListener() {
            @Override
            public void onEvent(Event event) {
                if (event instanceof MessageReceivedEvent) {
                    handleMessageCreate((MessageReceivedEvent) event);
                }
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
        if (event.getAuthor().getId().equals(event.getJDA().getSelfInfo().getId())) {
            return;
        }
        String[] splitMessage = event.getMessage().getContent().split(" ");
        String commandString = splitMessage[0];
        final SimpleCommand command = commands.get(commandString.toLowerCase());
        if (command == null) {
            return;
        }
        if (event.isPrivate() && !command.getCommandAnnotation().privateMessages()) {
            return;
        }
        if (!event.isPrivate() && !command.getCommandAnnotation().channelMessages()) {
            return;
        }
        if (!hasPermission(event.getAuthor(), command.getCommandAnnotation().requiredPermissions())) {
            if (Sdcf4jMessage.MISSING_PERMISSIONS.getMessage() != null) {
                event.getChannel().sendMessage(Sdcf4jMessage.MISSING_PERMISSIONS.getMessage());
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
            } else if (type == MessageReceivedEvent.class) {
                parameters[i] = event;
            } else if (type == JDA.class) {
                parameters[i] = event.getJDA();
            } else if (type == MessageChannel.class) {
                parameters[i] = event.getChannel();
            } else if (type == User.class) {
                parameters[i] = event.getAuthor();
            } else if (type == TextChannel.class) {
                parameters[i] = event.getTextChannel();
            } else if (type == PrivateChannel.class) {
                parameters[i] = event.getPrivateChannel();
            } else if (type == Guild.class) {
                parameters[i] = event.getGuild();
            } else if (type == Integer.class || type == int.class) {
                parameters[i] = event.getResponseNumber();
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
                        SimpleLog.getLog(getClass().getName()).log(e);
                    }
                    if (reply != null) {
                        event.getChannel().sendMessage(String.valueOf(reply));
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
                SimpleLog.getLog(getClass().getName()).log(e);
            }
            if (reply != null) {
                event.getChannel().sendMessage(String.valueOf(reply));
            }
        }
    }

}
