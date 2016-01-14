/*
 * Copyright(c) Yoyodyne, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.jacuzzi.impl.rev141210;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.jacuzzi.impl.OpendaylightJacuzzi;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.JacuzziService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;

public class JacuzziModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.jacuzzi.impl.rev141210.AbstractJacuzziModule {
    public JacuzziModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public JacuzziModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.jacuzzi.impl.rev141210.JacuzziModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        final OpendaylightJacuzzi opendaylightJacuzzi = new OpendaylightJacuzzi();

        DataBroker dataBrokerService = getDataBrokerDependency();
        opendaylightJacuzzi.setDataProvider(dataBrokerService);

        final BindingAwareBroker.RpcRegistration<JacuzziService> rpcRegistration = getRpcRegistryDependency()
                .addRpcImplementation(JacuzziService.class, opendaylightJacuzzi);
        final ListenerRegistration<DataChangeListener> dataChangeListenerRegistration = dataBrokerService
                .registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, opendaylightJacuzzi.JACUZZI_IID,
                        opendaylightJacuzzi, DataChangeScope.SUBTREE);
        final JacuzziRuntimeRegistration runtimeReg = getRootRuntimeBeanRegistratorWrapper().register( opendaylightJacuzzi);

        // Wrap toaster as AutoCloseable and close registrations to md-sal at
        // close(). The close method is where you would generally clean up thread pools
        // etc.
        final class AutoCloseableJacuzzi implements AutoCloseable {

            @Override
            public void close() throws Exception {
                opendaylightJacuzzi.close();
                rpcRegistration.close();
                dataChangeListenerRegistration.close();
                runtimeReg.close();
            }
        }
        return new AutoCloseableJacuzzi();
    }

}
