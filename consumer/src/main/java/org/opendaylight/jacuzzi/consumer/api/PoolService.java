package org.opendaylight.jacuzzi.consumer.api;

import java.util.concurrent.Future;

import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.HozeType;
import org.opendaylight.yangtools.yang.common.RpcResult;

public interface PoolService {
    Future<RpcResult<Void>> prepareSpa( TowelType towel, Class<? extends HozeType> hoze, int programLengh );
}
