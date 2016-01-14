package org.opendaylight.jacuzzi.impl;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.OptimisticLockFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.DisplayString;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.Jacuzzi;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.Jacuzzi.JacuzziStatus;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.JacuzziBuilder;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.JacuzziData;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.JacuzziOutOfCashBuilder;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.JacuzziRestocked;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.JacuzziRestockedBuilder;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.JacuzziService;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.RestockJacuzziInput;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.jacuzzi.rev160112.StartProgramInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.jacuzzi.impl.rev141210.JacuzziRuntimeMXBean;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

public class OpendaylightJacuzzi implements AutoCloseable, JacuzziService, JacuzziData, DataChangeListener, JacuzziRuntimeMXBean{

    private static final Logger LOG = LoggerFactory.getLogger(OpendaylightJacuzzi.class);
    public static final InstanceIdentifier <Jacuzzi> JACUZZI_IID = InstanceIdentifier.builder(Jacuzzi.class).build();

    private static final DisplayString JACUZZI_MANUFACTURER = new DisplayString("Opendaylight");
    private static final DisplayString JACUZZI_MODEL_NUMBER = new DisplayString("Model 1 - Binding Aware");

    private DataBroker dataProvider;

    private final ExecutorService executor;

    private final AtomicReference<Future<?>> currentStartProgramTask = new AtomicReference<>();
    private final AtomicLong programMade = new AtomicLong(0);
    private final AtomicLong amountOfCash = new AtomicLong(100);
    private AtomicLong massageFactor = new AtomicLong( 1000 );

    private NotificationProviderService notificationProvider;
    public void setNotificationProvider(NotificationProviderService salService) {
        this.notificationProvider = salService;
    }

    public OpendaylightJacuzzi(){
        executor = Executors.newFixedThreadPool(1);
    }

    private Jacuzzi buildJacuzzi( JacuzziStatus status ) {

        // note - we are simulating a device whose manufacture and model are
        // fixed (embedded) into the hardware.
        // This is why the manufacture and model number are hardcoded.
        return new JacuzziBuilder().setJacuzziManufacturer( JACUZZI_MANUFACTURER )
                                   .setJacuzziModelNumber( JACUZZI_MODEL_NUMBER )
                                   .setJacuzziStatus( status )
                                   .build();
    }

    public void setDataProvider( final DataBroker salDataProvider ) {
        this.dataProvider = salDataProvider;
        setJacuzziStatusFull( null );
   }
    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub
        executor.shutdown();
    }

    private void setJacuzziStatusFull(final Function<Boolean,Void> resultCallback){
        WriteTransaction tx = dataProvider.newWriteOnlyTransaction();
        tx.put( LogicalDatastoreType.OPERATIONAL,JACUZZI_IID, buildJacuzzi( JacuzziStatus.Full ) );

        ListenableFuture<RpcResult<TransactionStatus>> commitFuture = tx.commit();

        Futures.addCallback( commitFuture, new FutureCallback<RpcResult<TransactionStatus>>() {
            @Override
            public void onSuccess( RpcResult<TransactionStatus> result ) {
                if( result.getResult() != TransactionStatus.COMMITED ) {
                    LOG.error( "Failed to update jacuzzi status: " + result.getErrors() );
                }

                notifyCallback( result.getResult() == TransactionStatus.COMMITED );
            }

            @Override
            public void onFailure( Throwable t ) {
                // We shouldn't get an OptimisticLockFailedException (or any ex) as no
                // other component should be updating the operational state.
                LOG.error( "Failed to update jacuzzi status", t );

                notifyCallback( false );
            }

            void notifyCallback( boolean result ) {
                if( resultCallback != null ) {
                    resultCallback.apply( result );
                }
            }
        } );
    }

    @Override
    public Future<RpcResult<Void>> cancelProgram() {
        Future<?> current = currentStartProgramTask.getAndSet( null );
        if( current != null ) {
            current.cancel( true );
        }

        // Always return success from the cancel toast call.
        return Futures.immediateFuture( RpcResultBuilder.<Void> success().build());
    }

    @Override
    public Future<RpcResult<Void>> startProgram(StartProgramInput input) {
        final SettableFuture<RpcResult<Void>> futureResult = SettableFuture.create();

        checkStatusAndStartProgram( input, futureResult );

        return futureResult;
    }

    private void checkStatusAndStartProgram( final StartProgramInput input,
            final SettableFuture<RpcResult<Void>> futureResult ) {

        final ReadWriteTransaction tx = dataProvider.newReadWriteTransaction();
        ListenableFuture<Optional<Jacuzzi>> readFuture =
                                           tx.read( LogicalDatastoreType.OPERATIONAL, JACUZZI_IID );
        final ListenableFuture<Void> commitFuture =
                Futures.transform( readFuture, new AsyncFunction<Optional<Jacuzzi>,Void>(){
                    @Override
                    public ListenableFuture<Void> apply(
                            Optional<Jacuzzi> jacuzziData ) throws Exception {

                        JacuzziStatus jacuzziStatus = JacuzziStatus.Full;
                        if( jacuzziData.isPresent() ) {
                            jacuzziStatus = jacuzziData.get().getJacuzziStatus();
                        }

                        LOG.debug( "Read Jacuzzi status: {}", jacuzziStatus );

                        if( jacuzziStatus == JacuzziStatus.Full ) {
                            if(outOfDollar()) {
                                LOG.debug( "Jacuzzi out of $$" );

                                new TransactionCommitFailedException("", RpcResultBuilder.newError( ErrorType.APPLICATION, "resource-denied",
                                        "Jacuzzi out of $$", "out-of-stock", null, null ));
                            }
                            LOG.debug( "Setting Jacuzzi status to empty" );

                            // We're not currently making toast - try to update the status to Down
                            // to indicate we're going to make toast. This acts as a lock to prevent
                            // concurrent toasting.
                            tx.put( LogicalDatastoreType.OPERATIONAL, JACUZZI_IID,
                                    buildJacuzzi( JacuzziStatus.Empty ) );
                            return tx.submit();
                        }

                        LOG.debug( "Oops - already making program!" );

                        // Return an error since we are already making toast. This will get
                        // propagated to the commitFuture below which will interpret the null
                        // TransactionStatus in the RpcResult as an error condition.

                        return Futures
                                .immediateFailedCheckedFuture(new TransactionCommitFailedException(
                                        "", jacuzziInUseError()));
                    }
                    private RpcError jacuzziInUseError() {
                        return RpcResultBuilder.newError( ErrorType.APPLICATION, "resource-denied",
                                "THis jacuzzi is already in use", "not available", null, null );
                    }
                });

        Futures.addCallback( commitFuture, new FutureCallback<Void>() {

            @Override
            public void onFailure( Throwable ex ) {
                if( ex instanceof OptimisticLockFailedException ) {

                    // Another thread is likely trying to make toast simultaneously and updated the
                    // status before us. Try reading the status again - if another make toast is
                    // now in progress, we should get ToasterStatus.Down and fail.

                    LOG.debug( "Got OptimisticLockFailedException - trying again" );

                    checkStatusAndStartProgram( input, futureResult );

                } else {

                    LOG.error( "Failed to commit jacuzzi status", ex );

                    // Got some unexpected error so fail.
                    futureResult.set(RpcResultBuilder
                            .<Void> failed()
                            .withRpcErrors(
                                    ((TransactionCommitFailedException) ex)
                                    .getErrorList()).build());
                }
            }

            @Override
            public void onSuccess(Void result) {

                    currentStartProgramTask.set( executor.submit(
                                                     new StartProgramTask( input, futureResult ) ) );
            }
        });

    }

    private class StartProgramTask implements Callable<Void> {

        final StartProgramInput programRequest;
        final SettableFuture<RpcResult<Void>> futureResult;

        public StartProgramTask( final StartProgramInput programRequest,
                              final SettableFuture<RpcResult<Void>> futureResult ) {
            this.programRequest = programRequest;
            this.futureResult = futureResult;
        }

        @Override
        public Void call() {
            try
            {
                // make toast just sleeps for n seconds.
                long massageFactor = OpendaylightJacuzzi.this.massageFactor.get();
                Thread.sleep(programRequest.getProgramLengh()*massageFactor);
            }
            catch( InterruptedException e ) {
                LOG.info( "Interrupted while making the program" );
            }

            programMade.incrementAndGet();

            amountOfCash.getAndDecrement();
            if( outOfDollar() ) {
                LOG.info( "Jacuzzi out of $$!" );

                notificationProvider.publish( new JacuzziOutOfCashBuilder().build() );
            }

            // Set the Toaster status back to up - this essentially releases the toasting lock.
            // We can't clear the current toast task nor set the Future result until the
            // update has been committed so we pass a callback to be notified on completion.

            setJacuzziStatusFull( new Function<Boolean,Void>() {
                @Override
                public Void apply( Boolean result ) {

                    currentStartProgramTask.set( null );

                    LOG.debug("program done");

                    futureResult.set( RpcResultBuilder.<Void> success().build() );

                    return null;
                }
            } );
            return null;
       }
    }
    private boolean outOfDollar(){
        return amountOfCash.get()==0;
    }

    @Override
    public Jacuzzi getJacuzzi() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        DataObject dataObject = change.getUpdatedSubtree();
        if( dataObject instanceof Jacuzzi )
        {
            Jacuzzi toaster = (Jacuzzi) dataObject;
            Long darkness = toaster.getMassageFactor();
            if( darkness != null )
            {
                massageFactor.set( darkness );
            }
        }
    }

    @Override
    public Long getProgramMade() {
        return programMade.get();
    }

    @Override
    public void clearProgramMade() {
        LOG.info("clear programMade");
        programMade.set(0);

    }

    /**
     * RestConf RPC call implemented from the JacuzziService interface.
     * Restocks the cash for the jacuzzi and sends a ToasterRestocked notification.
     */
    @Override
    public Future<RpcResult<Void>> restockJacuzzi(RestockJacuzziInput input) {
        LOG.info( "restockToaster: " + input );

        amountOfCash.set( input.getAmountOfCashToStock() );

        if( amountOfCash.get() > 0 ) {
            JacuzziRestocked reStockedNotification =
                new JacuzziRestockedBuilder().setAmountOfCash( input.getAmountOfCashToStock() ).build();
            notificationProvider.publish( reStockedNotification );
        }

        return Futures.immediateFuture(RpcResultBuilder.<Void> success().build());
    }
}
