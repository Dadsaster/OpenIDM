/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011-2015 ForgeRock AS.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package org.forgerock.openidm.audit.impl;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.patch.JsonPatch;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ConnectionFactory;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResultHandler;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.RoutingMode;
import org.forgerock.json.resource.ServerContext;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.openidm.config.enhanced.EnhancedConfig;
import org.forgerock.openidm.config.enhanced.JSONEnhancedConfig;
import org.forgerock.openidm.core.ServerConstants;
import org.forgerock.openidm.router.RouteService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This audit service is the entry point for audit logging on the router.
 */
@Component(name = "org.forgerock.openidm.audit", immediate=true, policy=ConfigurationPolicy.REQUIRE)
@Service
@Properties({
    @Property(name = "service.description", value = "Audit Service"),
    @Property(name = "service.vendor", value = "ForgeRock AS"),
    @Property(name = "openidm.router.prefix", value = "/audit/*")
})
public class AuditServiceImpl implements RequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(AuditServiceImpl.class);

    // ----- Declarative Service Implementation

    /** The connection factory */
    @Reference(policy = ReferencePolicy.STATIC, target="(service.pid=org.forgerock.openidm.internal)")
    protected ConnectionFactory connectionFactory;

    /** Although we may not need the router here,
     https://issues.apache.org/jira/browse/FELIX-3790
     if using this with for scr 1.6.2
     Ensure we do not get bound on router whilst it is activating
     */
    @Reference(target = "("+ServerConstants.ROUTER_PREFIX + "=/*)")
    RouteService routeService;

    EnhancedConfig enhancedConfig = new JSONEnhancedConfig();

    JsonValue config;

    org.forgerock.audit.impl.AuditServiceImpl auditService =
            new org.forgerock.audit.impl.AuditServiceImpl();

    Router router = new Router();

    @Activate
    void activate(ComponentContext compContext) throws Exception {
        logger.debug("Activating Service with configuration {}", compContext.getProperties());
        auditService.activate(enhancedConfig.getConfigurationAsJson(compContext));
        router.addRoute(RoutingMode.STARTS_WITH, "/audit", auditService);
        logger.info("Audit service started.");
    }

    /**
     * Configuration modified handling
     * Ensures audit logging service stays registered
     * even whilst configuration changes
     */
    @Modified
    void modified(ComponentContext compContext) throws Exception {
        logger.debug("Reconfiguring audit service with configuration {}", compContext.getProperties());
        auditService.modified(enhancedConfig.getConfigurationAsJson(compContext));
    }

    private boolean hasConfigChanged(JsonValue existingConfig, JsonValue newConfig) {
        return JsonPatch.diff(existingConfig, newConfig).size() > 0;
    }

    @Deactivate
    void deactivate(ComponentContext compContext) {
        logger.debug("Deactivating Service {}", compContext.getProperties());
        auditService.deactivate(enhancedConfig.getConfigurationAsJson(compContext));
        router.removeAllRoutes();
        logger.info("Audit service stopped.");
    }

    /**
     * Gets an object from the audit logs by identifier. The returned object is not validated
     * against the current schema and may need processing to conform to an updated schema.
     * <p>
     * The object will contain metadata properties, including object identifier {@code _id},
     * and object version {@code _rev} to enable optimistic concurrency
     *
     * {@inheritDoc}
     */
    @Override
    public void handleRead(final ServerContext context, final ReadRequest request, final ResultHandler<Resource> handler) {
        router.handleRead(
                context,
                Requests.newReadRequest("/audit/" + request.getResourceName()),
                handler);
    }

    /**
     * Creates a new object in the object set.
     * <p>
     * This method sets the {@code _id} property to the assigned identifier for the object,
     * and the {@code _rev} property to the revised object version (For optimistic concurrency)
     *
     * {@inheritDoc}
     */
    @Override
    public void handleCreate(final ServerContext context, final CreateRequest request,
            final ResultHandler<Resource> handler) {
        router.handleCreate(
                context,
                Requests.newCreateRequest("/audit/" + request.getResourceName(), request.getContent()),
                handler);
    }

    /**
     * Audit service does not support changing audit entries.
     */
    @Override
    public void handleUpdate(final ServerContext context, final UpdateRequest request,
            final ResultHandler<Resource> handler) {
        router.handleUpdate(
                context,
                Requests.newUpdateRequest("/audit/" + request.getResourceName(), request.getContent()),
                handler);
    }

    /**
     * Audit service currently does not support deleting audit entries.
     *
     * Deletes the specified object from the object set.
     *
     * {@inheritDoc}
     */
    @Override
    public void handleDelete(ServerContext context, DeleteRequest request,
            ResultHandler<Resource> handler) {
        router.handleDelete(
                context,
                Requests.newDeleteRequest("/audit/" + request.getResourceName()),
                handler);
    }

    /**
     * Audit service does not support changing audit entries.
     *
     * {@inheritDoc}
     */
    @Override
    public void handlePatch(final ServerContext context, final PatchRequest request,
            final ResultHandler<Resource> handler) {
        router.handlePatch(
                context,
                Requests.newPatchRequest("/audit/" + request.getResourceName(), (PatchOperation[]) request.getPatchOperations().toArray()),
                handler);
    }

    /**
     * Performs the query on the specified object and returns the associated results.
     * <p>
     * Queries are parametric; a set of named parameters is provided as the query criteria.
     * The query result is a JSON object structure composed of basic Java types.
     *
     * The returned map is structured as follow:
     * <ul>
     * <li>The top level map contains meta-data about the query, plus an entry with the actual result records.
     * <li>The <code>QueryConstants</code> defines the map keys, including the result records (QUERY_RESULT)
     * </ul>
     *
     * {@inheritDoc}
     */
    @Override
    public void handleQuery(final ServerContext context, final QueryRequest request, final QueryResultHandler handler) {
        router.handleQuery(
                context,
                Requests.newQueryRequest("/audit/" + request.getResourceName()),
                handler);
    }
    /**
     * Audit service does not support actions on audit entries.
     *
     * {@inheritDoc}
     */
    @Override
    public void handleAction(final ServerContext context, final ActionRequest request,
            final ResultHandler<JsonValue> handler) {
        router.handleAction(
                context,
                Requests.newActionRequest("/audit/" + request.getResourceName(), request.getAction()),
                handler);
    }
}
