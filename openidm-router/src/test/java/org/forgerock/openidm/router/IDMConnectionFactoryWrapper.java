/**
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.openidm.router;

import org.forgerock.json.resource.Connection;
import org.forgerock.json.resource.ConnectionFactory;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.util.promise.Promise;

/**
 * An IDMConnectionFactory that wraps a normal ConnectionFactory. Used to satisfy test cases.
 */
public class IDMConnectionFactoryWrapper implements IDMConnectionFactory {

    private final ConnectionFactory connectionFactory;
    public IDMConnectionFactoryWrapper(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Connection getExternalConnection() throws ResourceException {
        return getConnection();
    }

    @Override
    public Promise<Connection, ResourceException> getExternalConnectionAsync() {
        return getConnectionAsync();
    }

    @Override
    public void close() {
        connectionFactory.close();
    }

    @Override
    public Connection getConnection() throws ResourceException {
        return connectionFactory.getConnection();
    }

    @Override
    public Promise<Connection, ResourceException> getConnectionAsync() {
        return connectionFactory.getConnectionAsync();
    }
}
