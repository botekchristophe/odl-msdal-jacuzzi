package org.opendaylight.jacuzzi.consumer.impl;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.opendaylight.jacuzzi.consumer.api.PoolService;
import org.opendaylight.jacuzzi.consumer.api.TowelType;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.HozeType;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.JacuzziService;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.StartProgramInput;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.StartProgramInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.pool.service.impl.rev140131.PoolServiceRuntimeMXBean;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

public class PoolServiceImpl implements PoolService, PoolServiceRuntimeMXBean{

    private static final Logger log = LoggerFactory.getLogger( PoolServiceImpl.class );

    private final ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

    private volatile boolean jacuzziOutOfCash;

    private final JacuzziService jacuzzi;

    public PoolServiceImpl(JacuzziService jacuzzi) {
        this.jacuzzi=jacuzzi;
    }

    @Override
    public Future<RpcResult<Void>> prepareSpa(TowelType towel, Class<? extends HozeType> hozetype, int programLengh) {
     // Call makeToast and use JdkFutureAdapters to convert the Future to a ListenableFuture,
        // The OpendaylightToaster impl already returns a ListenableFuture so the conversion is
        // actually a no-op.

        ListenableFuture<RpcResult<Void>> startProgramFuture = JdkFutureAdapters.listenInPoolThread(
                startProgram( hozetype, programLengh ), executor );

        ListenableFuture<RpcResult<Void>> setTowelFuture = setTowel( TowelType.ADULT_TOWEL );

        // Combine the 2 ListenableFutures into 1 containing a list of RpcResults.

        ListenableFuture<List<RpcResult<Void>>> combinedFutures =
                Futures.allAsList( ImmutableList.of( startProgramFuture, setTowelFuture ) );

        // Then transform the RpcResults into 1.

        return Futures.transform( combinedFutures,
            new AsyncFunction<List<RpcResult<Void>>,RpcResult<Void>>() {
                @Override
                public ListenableFuture<RpcResult<Void>> apply( List<RpcResult<Void>> results )
                                                                                 throws Exception {
                    boolean atLeastOneSucceeded = false;
                    Builder<RpcError> errorList = ImmutableList.builder();
                    for( RpcResult<Void> result: results ) {
                        if( result.isSuccessful() ) {
                            atLeastOneSucceeded = true;
                        }

                        if( result.getErrors() != null ) {
                            errorList.addAll( result.getErrors() );
                        }
                    }

                    return Futures.immediateFuture(
                            RpcResultBuilder.<Void> success().build());
                }
        } );
    }
    private ListenableFuture<RpcResult<Void>> setTowel( TowelType towelType ) {

        return executor.submit( new Callable<RpcResult<Void>>() {

            @Override
            public RpcResult<Void> call() throws Exception {

                // We don't actually do anything here - just return a successful result.
                return RpcResultBuilder.<Void> success().build();
            }
        } );
    }

    private Future<RpcResult<Void>> startProgram( Class<? extends HozeType> hozetype,
                                               long programLengh ) {
        if( jacuzziOutOfCash )
        {
            log.info( "We're out of $$ but we can set you a towel" );
            return Futures.immediateFuture( RpcResultBuilder.<Void> success()
                    .withWarning( ErrorType.APPLICATION, "partial-operation",
                            "We're out of $$ but we can set you a towel" ).build() );
        }

        // Access the ToasterService to make the toast.

        StartProgramInput toastInput = new StartProgramInputBuilder()
            .setJacuzziHozeType(hozetype)
            .setProgramLengh(programLengh)
            .build();

        return jacuzzi.startProgram(toastInput);
    }

    @Override
    public Boolean makeSpa() {
        try {
            // This call has to block since we must return a result to the JMX client.
            RpcResult<Void> result = prepareSpa( TowelType.ADULT_TOWEL, HozeType.class, 2 ).get();
            if( result.isSuccessful() ) {
                log.info( "prepareSpa succeeded" );
            } else {
                log.warn( "prepareSpa failed: " + result.getErrors() );
            }

            return result.isSuccessful();

        } catch( InterruptedException | ExecutionException e ) {
            log.warn( "An error occurred while maing breakfast: " + e );
        }

        return Boolean.FALSE;
    }

}
