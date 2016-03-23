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

/**
 * All messages used in the lib.
 */
public enum Sdcf4jMessage {

    MISSING_PERMISSIONS("You are not allowed to use this command!");

    private String message;

    Sdcf4jMessage(String message) {
        this.message = message;
    }

    /**
     * Gets the message.
     *
     * @return The message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the message. Set it to <code>null</code> if you don't want any output.
     *
     * @param message The message to set.
     */
    public void setMessage(String message) {
        this.message = message;
    }
}
