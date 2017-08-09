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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A command annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {

    /**
     * Gets whether the executor should listen to private messages or not.
     *
     * @return Whether the executor should listen to private messages or not.
     */
    boolean privateMessages() default true;

    /**
     * Gets whether the executor should listen to channel messages or not.
     *
     * @return Whether the executor should listen to channel messages or not.
     */
    boolean channelMessages() default true;

    /**
     * Gets the commands the executor should listen to. The first element is the main command.
     *
     * @return The commands the executor should listen to.
     */
    String[] aliases();

    /**
     * Gets the description of the command.
     *
     * @return The description of the command.
     */
    String description() default "none";

    /**
     * Gets the usage of the command.
     * If no usage was provided it will use the first alias.
     *
     * @return The usage of the command.
     */
    String usage() default "";

    /**
     * Gets the permissions required for a user to run the command.
     *
     * @return The permissions required for a user to run the command.
     */
    String requiredPermissions() default "none";

    /**
     * Gets whether the command should be shown in the help page or not.
     *
     * @return Whether the command should be shown if the help page or not.
     */
    boolean showInHelpPage() default true;

    /**
     * Gets whether the command should be executed async or not. If not the thread of the message listener is used.
     *
     * @return Whether the command should be executed async or not.
     */
    boolean async() default false;

    /**
     * Gets whether the bot has to be mentioned to react to a command.
     * This would look like <code>@botname alias</code>
     *
     * @return Whether the bot has to be mentioned to react to a command.
     */
    boolean requiresMention() default false;

}
