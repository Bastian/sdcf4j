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
package de.btobastian.sdcf4j;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * The basic command handler.
 */
public abstract class CommandHandler {

    protected final HashMap<String, SimpleCommand> commands = new HashMap<>();
    protected final List<SimpleCommand> commandList = new ArrayList<>();
    private final HashMap<String, List<String>> permissions = new HashMap<>();

    /**
     * Registers an executor.
     *
     * @param executor The executor to register.
     */
    public void registerCommand(CommandExecutor executor) {
        for (Method method : executor.getClass().getMethods()) {
            Command annotation = method.getAnnotation(Command.class);
            if (annotation == null) {
                continue;
            }
            if (annotation.aliases().length == 0) {
                throw new IllegalArgumentException("Aliases array cannot be empty!");
            }
            SimpleCommand command = new SimpleCommand(annotation, method, executor);
            for (String alias : annotation.aliases()) {
                // add command to map. It's faster to access it from the map than iterating to the whole list
                commands.put(alias.toLowerCase(), command);
            }
            // we need a list, too, because a HashMap is not ordered.
            commandList.add(command);
        }
    }

    /**
     * Adds a permission for the user with the given id.
     *
     * @param userId The id of the user.
     * @param permission The permission to add.
     */
    public void addPermission(String userId, String permission) {
        List<String> permissions = this.permissions.get(userId);
        if (permissions == null) {
            permissions = new ArrayList<>();
            this.permissions.put(userId, permissions);
        }
        permissions.add(permission);
    }

    /**
     * Checks if the user with the given id has the required permission.
     *
     * @param userId The id of the user.
     * @param permission The permission to check.
     * @return If the user has the given permission.
     */
    public boolean hasPermission(String userId, String permission) {
        if (permission.equals("none") || permission.equals("")) {
            return true;
        }
        List<String> permissions = this.permissions.get(userId);
        if (permissions == null) {
            return false;
        }
        for (String perm : permissions) {
            // user has the permission
            if (checkPermission(perm, permission)) {
                return true;
            }
        }
        // user hasn't enough permissions
        return false;
    }

    /**
     * Gets a list with all commands in the order they were registered.
     * This is useful for automatic help commands.
     *
     * @return A list with all commands the the order they were registered.
     */
    public List<SimpleCommand> getCommands() {
        return Collections.unmodifiableList(commandList);
    }

    /**
     * Checks if you are allowed to do something with the given permission.
     *
     * @param has The permission the user has.
     * @param required The permission which is required.
     * @return If you can use the command with the given permission.
     */
    private boolean checkPermission(String has, String required) {
        String[] splitHas = has.split("\\.");
        String[] splitRequired = required.split("\\.");
        int lower = splitHas.length > splitRequired.length ? splitRequired.length : splitHas.length;
        for (int i = 0; i < lower; i++) {
            if (!splitHas[i].equalsIgnoreCase(splitRequired[i])) {
                return splitHas[i].equals("*");
            }
        }
        return splitRequired.length == splitHas.length;
    }

    /**
     * A simple representation of a command.
     */
    public class SimpleCommand {

        private final Command annotation;
        private final Method method;
        private final CommandExecutor executor;

        /**
         * Class constructor.
         *
         * @param annotation The annotation of the executor's method.
         * @param method The method which listens to the commands.
         * @param executor The executor of the method.
         */
        protected SimpleCommand(Command annotation, Method method, CommandExecutor executor) {
            this.annotation = annotation;
            this.method = method;
            this.executor = executor;
        }

        /**
         * The command annotation of the method.
         *
         * @return The command annotation of the method.
         */
        public Command getCommandAnnotation() {
            return annotation;
        }

        /**
         * Gets the method which listens to the commands.
         *
         * @return The method which listens to the commands.
         */
        public Method getMethod() {
            return method;
        }

        /**
         * Gets the executor of the method.
         *
         * @return The executor of the method.
         */
        public CommandExecutor getExecutor() {
            return executor;
        }
    }

}
