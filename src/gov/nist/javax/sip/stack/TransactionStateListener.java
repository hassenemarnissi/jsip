package gov.nist.javax.sip.stack;

public interface TransactionStateListener
{
    public void TransactionStateChanged(SIPTransaction transaction,
                                        int oldState,
                                        int newState);
}
