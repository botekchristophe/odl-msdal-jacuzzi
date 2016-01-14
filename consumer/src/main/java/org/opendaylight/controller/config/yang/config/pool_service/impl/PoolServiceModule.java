package org.opendaylight.controller.config.yang.config.pool_service.impl;

import java.util.concurrent.Future;

import org.opendaylight.jacuzzi.consumer.api.PoolService;
import org.opendaylight.jacuzzi.consumer.api.TowelType;
import org.opendaylight.jacuzzi.consumer.impl.PoolServiceImpl;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.HozeType;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.JacuzziService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.pool.service.impl.rev140131.AbstractPoolServiceModule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.pool.service.impl.rev140131.PoolServiceRuntimeRegistration;
import org.opendaylight.yangtools.yang.common.RpcResult;

public class PoolServiceModule extends AbstractPoolServiceModule {
    public PoolServiceModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public PoolServiceModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.controller.config.yang.config.pool_service.impl.PoolServiceModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        JacuzziService jacuzziService = getRpcRegistryDependency().getRpcService(JacuzziService.class);


        final PoolServiceImpl poolService = new PoolServiceImpl(jacuzziService);

        final PoolServiceRuntimeRegistration runtimeReg =
                getRootRuntimeBeanRegistratorWrapper().register( poolService );
        final class AutoCloseablePoolService implements PoolService, AutoCloseable {

            @Override
            public void close() throws Exception {
                runtimeReg.close();
            }

            @Override
            public Future<RpcResult<Void>> prepareSpa( TowelType towel, Class<? extends HozeType> hozeType, int programLengh ) {
                return poolService.prepareSpa( towel, hozeType, programLengh );
            }
        }

        AutoCloseable ret = new AutoCloseablePoolService();
        return ret;
    }

}
