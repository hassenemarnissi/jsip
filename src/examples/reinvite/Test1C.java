package examples.reinvite;

import gov.nist.javax.sip.address.SipUri;

import javax.sip.*;
import javax.sip.address.*;
import javax.sip.header.*;
import javax.sip.message.*;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.util.*;

import junit.framework.TestCase;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 *
 *
 * @author M. Ranganathan
 */

public class Test1C implements SipListener {

    private boolean reInviteFlag;

    private SipProvider provider;

    private int inviteCount = 0;

    private ContactHeader contactHeader;

    private ListeningPoint listeningPoint;

    private int counter;

    private static String PEER_ADDRESS = Shootme.myAddress;

    private static int PEER_PORT = Shootme.myPort;

    private static String peerHostPort = PEER_ADDRESS + ":" + PEER_PORT;

    // To run on two machines change these to suit.
    public static final String myAddress = "127.0.0.1";

    private static final int myPort = 5060;

    protected ClientTransaction inviteTid;

    private boolean okReceived;

    private Dialog dialog;

    protected static final String usageString = "java "
            + "examples.shootist.Shootist \n"
            + ">>>> is your class path set to the root?";

    private static Logger logger = Logger.getLogger(Test1C.class);

    static {
        try {
            logger.addAppender(new FileAppender(new PatternLayout("%d{HH:mm:ss.SSS} %-5p [%t]: %m%n"),
                    ProtocolObjects.logFileDirectory + "shootistconsolelog.txt"));
        } catch (Exception ex) {
            throw new RuntimeException("could not open shootistconsolelog.txt");
        }
    }

    private static void usage() {
        logger.info(usageString);
        System.exit(0);

    }

    public Test1C(int count) {
        this.counter = count;
    }

    public void processRequest(RequestEvent requestEvent) {
       Request request = requestEvent.getRequest();
       ServerTransaction serverTransactionId = requestEvent
               .getServerTransaction();

       logger.info("\n\nRequest " + request.getMethod()
               + " received at shootist "
               + " with server transaction id " + serverTransactionId);
    }

    /*
     * (non-Javadoc)
     * @see javax.sip.SipListener#processResponse(javax.sip.ResponseEvent)
     */
    public void processResponse(ResponseEvent responseReceivedEvent) {
        logger.info("Got a response");

        Response response = (Response) responseReceivedEvent.getResponse();
        Transaction tid = responseReceivedEvent.getClientTransaction();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

        logger.info("Response received with client transaction id " + tid
                + ":\n" + response.getStatusCode() + " " + cseq.getMethod() + " " + cseq.getSeqNumber());
        if (tid == null) {
            logger.info("Stray response -- dropping ");
            return;
        }
        logger.info("transaction state is " + tid.getState());
        logger.info("Dialog = " + tid.getDialog());
        logger.info("Dialog State is " + tid.getDialog().getState());

        final CSeqHeader cseqH = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        try {
            if (response.getStatusCode() == Response.OK
                    && Request.INVITE.equals(cseq.getMethod())) {

                Dialog dialog = tid.getDialog();
                logger.info("dialogs = " + dialog + " thisdialog = "  + this.dialog);
                TestCase.assertTrue("dialog mismatch", dialog == this.dialog);

                if (2 == inviteCount)
                {
                  logger.info("Too many INVITE responses");
                  TestCase.fail("Too many INVITE responses");
                }

                if (1 == inviteCount)
                {
                   logger.info("Sending INFO");
                   sendInfo();
                }
                else
                {
                   Request ackRequest = dialog.createAck( cseq.getSeqNumber() );
                   logger.info("Ack request to send = " + ackRequest);
                   logger.info("Sending ACK");
                   dialog.sendAck(ackRequest);
                }

                // Send a Re INVITE
                if (0 == inviteCount) {
                    /*try {*/ Thread.sleep(100); /*} catch (InterruptedException ex) { logger.warn("Interrupted exception: " + ex); }*/
                    
                    logger.info("Sending RE-INVITE");
                    this.sendReInvite();
                }
                inviteCount += 1;
            }
            else if (Request.INFO.equals(cseq.getMethod()))
            {
               // send the ACK for the INVITE (previous transaction)
               Request ackRequest = dialog.createAck( cseq.getSeqNumber()-1 );
               logger.info("INVITE Ack request to send = " + ackRequest);
               logger.info("Sending ACK for invite");
               dialog.sendAck(ackRequest);
            }
        } catch (Exception ex) {
            logger.error(ex);
            System.exit(0);
        }

    }

    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {

        logger.info("Transaction Time out");
        logger.info("TimeoutEvent " + timeoutEvent.getTimeout());
    }

    public SipProvider createSipProvider() {
        try {
            listeningPoint = ProtocolObjects.sipStack.createListeningPoint(
                    myAddress, myPort, ProtocolObjects.transport);

            provider = ProtocolObjects.sipStack
                    .createSipProvider(listeningPoint);
            return provider;
        } catch (Exception ex) {
            logger.error(ex);
            System.exit(0);
            return null;
        }
    }

    /**
     * Create and send a re-invitation.
     *
     * @throws Exception
     */
    public void sendReInvite() throws Exception {
        Request inviteRequest = dialog.createRequest(Request.INVITE);
        MaxForwardsHeader mf = ProtocolObjects.headerFactory.createMaxForwardsHeader(10);
        inviteRequest.setHeader(mf);
        inviteRequest.setHeader(this.contactHeader);
        ClientTransaction ct = provider
                .getNewClientTransaction(inviteRequest);
        dialog.sendRequest(ct);
    }

    public void sendInfo() throws Exception {
       Request inviteRequest = dialog.createRequest(Request.INFO);
       MaxForwardsHeader mf = ProtocolObjects.headerFactory.createMaxForwardsHeader(10);
       inviteRequest.setHeader(mf);
       inviteRequest.setHeader(this.contactHeader);
       ClientTransaction ct = provider
               .getNewClientTransaction(inviteRequest);
       dialog.sendRequest(ct);
   }

    /**
     * Create and send out the initial invite.
     *
     */
    public void sendInvite() {

        try {

            // Note that a provider has multiple listening points.
            // all the listening points must have the same IP address
            // and port but differ in their transport parameters.

            String fromName = "BigGuy";
            String fromSipAddress = "here.com";
            String fromDisplayName = "The Master Blaster";

            String toSipAddress = "there.com";
            String toUser = "LittleGuy";
            String toDisplayName = "The Little Blister";

            // create >From Header
            SipURI fromAddress = ProtocolObjects.addressFactory.createSipURI(
                    fromName, fromSipAddress);

            Address fromNameAddress = ProtocolObjects.addressFactory
                    .createAddress(fromAddress);
            fromNameAddress.setDisplayName(fromDisplayName);
            FromHeader fromHeader = ProtocolObjects.headerFactory
                    .createFromHeader(fromNameAddress, new Integer((int) (Math
                            .random() * Integer.MAX_VALUE)).toString());

            // create To Header
            SipURI toAddress = ProtocolObjects.addressFactory.createSipURI(
                    toUser, toSipAddress);
            Address toNameAddress = ProtocolObjects.addressFactory
                    .createAddress(toAddress);
            toNameAddress.setDisplayName(toDisplayName);
            ToHeader toHeader = ProtocolObjects.headerFactory.createToHeader(
                    toNameAddress, null);

            // create Request URI
            SipURI requestURI = ProtocolObjects.addressFactory.createSipURI(
                    toUser, peerHostPort);

            // Create ViaHeaders

            ArrayList viaHeaders = new ArrayList();
            int port = provider.getListeningPoint(ProtocolObjects.transport)
                    .getPort();

            ViaHeader viaHeader = ProtocolObjects.headerFactory
                    .createViaHeader(myAddress, port,
                            ProtocolObjects.transport, null);

            // add via headers
            viaHeaders.add(viaHeader);

            // Create ContentTypeHeader
            ContentTypeHeader contentTypeHeader = ProtocolObjects.headerFactory
                    .createContentTypeHeader("application", "sdp");

            // Create a new CallId header
            CallIdHeader callIdHeader = provider.getNewCallId();

            // Create a new Cseq header
            CSeqHeader cSeqHeader = ProtocolObjects.headerFactory
                    .createCSeqHeader(1L, Request.INVITE);

            // Create a new MaxForwardsHeader
            MaxForwardsHeader maxForwards = ProtocolObjects.headerFactory
                    .createMaxForwardsHeader(70);

            // Create the request.
            Request request = ProtocolObjects.messageFactory.createRequest(
                    requestURI, Request.INVITE, callIdHeader, cSeqHeader,
                    fromHeader, toHeader, viaHeaders, maxForwards);
            // Create contact headers

            // Create the contact name address.
            SipURI contactURI = ProtocolObjects.addressFactory.createSipURI(
                    fromName, myAddress);
            contactURI.setPort(provider.getListeningPoint(
                    ProtocolObjects.transport).getPort());

            Address contactAddress = ProtocolObjects.addressFactory
                    .createAddress(contactURI);

            // Add the contact address.
            contactAddress.setDisplayName(fromName);

            contactHeader = ProtocolObjects.headerFactory
                    .createContactHeader(contactAddress);
            request.addHeader(contactHeader);

            // Add the extension header.
            Header extensionHeader = ProtocolObjects.headerFactory
                    .createHeader("My-Header", "my header value");
            request.addHeader(extensionHeader);

            String sdpData = "v=0\r\n"
                    + "o=4855 13760799956958020 13760799956958020"
                    + " IN IP4  129.6.55.78\r\n" + "s=mysession session\r\n"
                    + "p=+46 8 52018010\r\n" + "c=IN IP4  129.6.55.78\r\n"
                    + "t=0 0\r\n" + "m=audio 6022 RTP/AVP 0 4 18\r\n"
                    + "a=rtpmap:0 PCMU/8000\r\n" + "a=rtpmap:4 G723/8000\r\n"
                    + "a=rtpmap:18 G729A/8000\r\n" + "a=ptime:20\r\n";

            request.setContent(sdpData, contentTypeHeader);

            // The following is the preferred method to route requests
            // to the peer. Create a route header and set the "lr"
            // parameter for the router header.

            Address address = ProtocolObjects.addressFactory
                    .createAddress("<sip:" + PEER_ADDRESS + ":" + PEER_PORT
                            + ">");
            // SipUri sipUri = (SipUri) address.getURI();
            // sipUri.setPort(PEER_PORT);

            RouteHeader routeHeader = ProtocolObjects.headerFactory
                    .createRouteHeader(address);
            SipUri sipUri = (SipUri)address.getURI();
            sipUri.setLrParam();
            request.addHeader(routeHeader);
            extensionHeader = ProtocolObjects.headerFactory.createHeader(
                    "My-Other-Header", "my new header value ");
            request.addHeader(extensionHeader);

            Header callInfoHeader = ProtocolObjects.headerFactory.createHeader(
                    "Call-Info", "<http://www.antd.nist.gov>");
            request.addHeader(callInfoHeader);

            // Create the client transaction.
            this.inviteTid = provider.getNewClientTransaction(request);

            // send the request out.
            this.inviteTid.sendRequest();
            this.dialog = this.inviteTid.getDialog();
            logger.info("created dialog " + dialog);

        } catch (Exception ex) {
            logger.error("Unexpected exception", ex);
            usage();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.sip.SipListener#processIOException(javax.sip.IOExceptionEvent)
     */
    public void processIOException(IOExceptionEvent exceptionEvent) {
        logger.info("IO Exception!");
        TestCase.fail("Unexpected exception");

    }

    /*
     * (non-Javadoc)
     *
     * @see javax.sip.SipListener#processTransactionTerminated(javax.sip.TransactionTerminatedEvent)
     */
    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {

        logger.info("Transaction Terminated Event!");
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.sip.SipListener#processDialogTerminated(javax.sip.DialogTerminatedEvent)
     */
    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        logger.info("Dialog Terminated Event!");

    }

    /////////////////////////////////////////////////////////////////////////////////
    // main method
    /////////////////////////////////////////////////////////////////////////////////
    public static void main(String args[]) {
        try {
            ProtocolObjects.init("shootist", true);
            logger.addAppender(new ConsoleAppender(new PatternLayout("%d{HH:mm:ss.SSS} %-5p [%t]: %m%n")));
            Test1C shootist = new Test1C(10);
            shootist.createSipProvider();
            shootist.provider.addSipListener(shootist);

            /*
             * Test is:
             * C send INVITE; S send 180, 200; C send ACK.
             * C send reINVITE; S send 180, 200;
             *   C send INFO; S reply 200;
             *   C send ACK for INVITE
             * S should not re-send 200 for INVITE (or INFO)
             *
             * This is all handled in processResponse()
             */
            shootist.sendInvite();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

}

