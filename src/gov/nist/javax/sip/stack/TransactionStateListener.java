package gov.nist.javax.sip.stack;

/**
 * Transaction State Listeners are notified when the transaction transitions
 * from one state to another
 */
public interface TransactionStateListener
{
    /**
     * Notifies the listener that the transaction has transitioned to another
     * state.
     *
     * @param transaction the transaction that has changed state
     * @param oldState the old state of the transaction
     * @param newState the new state of the transaction
     */
    public void transactionStateChanged(SIPTransaction transaction,
                                        int oldState,
                                        int newState);
}
