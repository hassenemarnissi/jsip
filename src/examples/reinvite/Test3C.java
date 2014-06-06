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
 * Test3 - overlapping reinvite (SFR 459499).
 * 
 * C             S  State  0 - start
 * |--INVITE-1-->|         1 - sent INVITE-1
 * |<---100-1----|         2 - received 100-1
 * |<---200-1----|         3 - received 200-1
 * |<-INVITE-10--|         4 - received reINVITE 10 from peer
 * |----491-10-->|         5 - sent 491-10
 * |----ACK-1--->|         6 - sent ACK-1
 * 
 * In the failure case, ACK-1 is dropped and S continues:
 * |<---200-1----|           - Fail (too many OKs) // Though note this app probably won't see the ack - it's handled in the stack.
 * |----ACK-1--->|
 * |     ...     |
 * |<---200-1----|
 * |----ACK-1--->|
 *
 * @author M. Ranganathan
 */

public class Test3C implements SipListener {

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
    public static final int myPort = 5060;

    protected ClientTransaction clientInviteTid;
    private ServerTransaction serverInviteTid;

    private boolean okReceived;
    private Dialog dialog;

    protected static final String usageString = "java "
            + "examples.shootist.Shootist \n"
            + ">>>> is your class path set to the root?";

    private static Logger logger = Logger.getLogger(Test3C.class);

    private int state = 0;

    private RequestEvent lastRequestEvent;
    private Request lastRequest;
    private ResponseEvent lastResponseEvent;
    private Response lastResponse;

    // Key is method + cseq (e.g. "INVITE1")
    private Map<String, ServerTransaction> serverTransactions;

    class ApplicationData {
        protected int ackCount;
    }

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

    public Test3C() {
      serverTransactions = new HashMap<String, ServerTransaction>();
    }

    private static long secondInvCseq = 10L;

    private synchronized void runStateMachine() {
      boolean runAgain = true;
      while (runAgain)
      {
        /*
         * Test3 - overlapping reinvite (SFR 459499).
         * 
         * C             S  State  0 - start
         * |--INVITE-1-->|    0 -> 1 - sent INVITE-1
         * |<---100-1----|    1 -> 2 - received 100-1
         * |<---200-1----|    2 -> 3 - received 200-1
         * |<-INVITE-10--|    3 -> 4 - received reINVITE 10 from peer
         * |----491-10-->|    4 -> 5 - sent 491-10
         * |----ACK-1--->|    5 -> 6 - sent ACK-1
         * 
         * In the failure case, ACK-1 is dropped and S continues:
         * |<---200-1----|           - Fail (too many OKs) // Though note this app probably won't see the ack - it's handled in the stack.
         * |----ACK-1--->|
         * |     ...     |
         * |<---200-1----|
         * |----ACK-1--->|
         */
        logger.info("\n\n  *** STATE " + state + " ***\n");
        switch (state)
        {
          case 0:
          {
            sendInvite(1L);
            runAgain = false;
          }
          break;
          
          case 1:
          {
            verifyLastResponse("INVITE", 1L, 100);
            runAgain = false;
          }
          break;

          case 2:
          {
            verifyLastResponse("INVITE", 1L, 200);
            runAgain = false;
          }
          break;

          case 3:
          {
            verifyLastRequest("INVITE", secondInvCseq);
            //verifyLastRequest("INVITE", 1L);      // this test isn't going to work unless we can bump up this cseq
          }
          break;

          case 4:
          {            //10?
            sendResponse(1L, 491);
          }
          break;

          case 5:
          {
            sendAck(1L);
            runAgain = false;
          }
          break;

          default:
          {
            TestCase.fail("Unexpectedly got message after test should have ended");
            System.exit(1);
          }
          break;
        }

        // Move to next state
        state += 1;
      }
    }

    public void processRequest(RequestEvent requestEvent) {
      logger.info("Got a request in state " + state);

       Request request = requestEvent.getRequest();

       lastRequestEvent = requestEvent;
       lastRequest = request;

       // did we expect this request?
       switch (state)
       {
         case 3:
         {
           runStateMachine();
         }
         break;
       
         default:
         {
           CSeqHeader cseq = (CSeqHeader) lastRequest.getHeader(CSeqHeader.NAME);
           String reqMethod = cseq.getMethod();
           long reqCseqNum = cseq.getSeqNumber();
           logger.error("Unexpected request in state " + state + " : " + reqMethod + " " + reqCseqNum);
           System.exit(1);
         }
         break;
       }
    }

    /*
     * (non-Javadoc)
     * @see javax.sip.SipListener#processResponse(javax.sip.ResponseEvent)
     */
    public void processResponse(ResponseEvent responseReceivedEvent) {
        logger.info("Got a response in state " + state);

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

        lastResponseEvent = responseReceivedEvent;
        lastResponse = response;

        // did we expect this response?
        switch (state)
        {
          case 1:
          case 2:
          {
            runStateMachine();
          }
          break;

          default:
          {
            String respMethod = cseq.getMethod();
            long respCseqNum = cseq.getSeqNumber();
            logger.error("Unexpected response in state " + state + " : " + respMethod + " " + respCseqNum);
            System.exit(1);
          }
          break;
        }
    }



    private void verifyLastResponse(String method, long cseqNum, int statusCode)
    {
        TestCase.assertNotNull("last response event was null!", lastResponseEvent);
        TestCase.assertNotNull("last response was null!", lastResponse);

        Transaction tid = lastResponseEvent.getClientTransaction();
        Response response = lastResponse;
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

        int respStatusCode = response.getStatusCode();
        String respMethod = cseq.getMethod();
        long respCseqNum = cseq.getSeqNumber();

        logger.info("Response received with client transaction id " + tid
                + ":\n" + respStatusCode + " " + respMethod + " " + respCseqNum);
        if (tid != null) {
            logger.info("transaction state is " + tid.getState());
            logger.info("Dialog = " + tid.getDialog());
            logger.info("Dialog State is " + tid.getDialog().getState());
        }

        try
        {
          TestCase.assertEquals("Unexpected method type: " + respMethod + " vs " + method,
                                respMethod, method);
          TestCase.assertEquals("CSEQ didn't match: " + respCseqNum + " vs " + cseqNum,
                                respCseqNum, cseqNum);
          TestCase.assertEquals("Status code didn't match: ", respStatusCode, statusCode);
        }
        catch (Exception ex)
        {
          logger.error("Method / cseq / code didn't match! " + respMethod + " vs " + method + ", " + respCseqNum + " vs " + cseqNum + " and " + respStatusCode + " vs " + statusCode);
          System.exit(1);
        }
    }

    private void verifyLastRequest(String method, long cseqNum)
    {
        TestCase.assertNotNull("last request was null!", lastRequest);

        logger.info("\n\nRequest " + lastRequest.getMethod() +
                                                      " received at shootist");

        CSeqHeader cseq = (CSeqHeader) lastRequest.getHeader(CSeqHeader.NAME);

        String reqMethod = cseq.getMethod();
        long reqCseqNum = cseq.getSeqNumber();

        logger.info("Request received " + reqMethod + " " + reqCseqNum);

        try
        {
          TestCase.assertEquals("Unexpected method type: " + reqMethod + " vs " + method,
                                reqMethod, method);
          TestCase.assertEquals("CSEQ didn't match: " + reqCseqNum + " vs " + cseqNum,
                                reqCseqNum, cseqNum);
        }
        catch (Exception ex)
        {
          logger.error("Method or cseq didn't match! " + reqMethod + " vs " + method + " and " + reqCseqNum + " vs " + cseqNum);
          System.exit(1);
        }
    }

    private void send200(long cseqNum) // ignore seqnum here - figure it out from the INVITE
    {
      TestCase.assertNotNull("last response was null!", lastRequest);
      TestCase.assertNotNull("last response event was null!", lastRequestEvent);

      SipProvider sipProvider = (SipProvider) lastRequestEvent.getSource();
      Request request = lastRequestEvent.getRequest();
      logger.info("Got an INVITE  " + request);
      try {
          logger.info("shootme: got an Invite sending OK");
 //       // logger.info("shootme: " + request);
 //       Response response = ProtocolObjects.messageFactory.createResponse(180, request);
 //       ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
 //       toHeader.setTag("4321");
 //       Address address = ProtocolObjects.addressFactory.createAddress("Shootme <sip:"
 //               + myAddress + ":" + myPort + ">");
 //       ContactHeader contactHeader = ProtocolObjects.headerFactory
 //               .createContactHeader(address);
 //       response.addHeader(contactHeader);
          ServerTransaction st = lastRequestEvent.getServerTransaction();

          if (st == null) {
              st = sipProvider.getNewServerTransaction(request);
              logger.info("Server transaction created!" + request);

              logger.info("Dialog = " + st.getDialog());
              if (st.getDialog().getApplicationData() == null) {
                  st.getDialog().setApplicationData(new ApplicationData());
              }
          } else {
              // If Server transaction is not null, then
              // this is a re-invite.
              logger.info("This is a RE INVITE ");
              if (st.getDialog() != dialog) {
                  logger.error("Whoopsa Daisy Dialog Mismatch "
                          + st.getDialog() + " / " + dialog);
                  TestCase.fail("Dialog mismatch " + st.getDialog() + " dialog  = " + dialog);
                  System.exit(1);
              }
          }

          logger.info("got a server tranasaction " + st);
          byte[] content = request.getRawContent();
          if (content != null) {
              logger.info(" content = " + new String(content));
              ContentTypeHeader contentTypeHeader = ProtocolObjects.headerFactory
                      .createContentTypeHeader("application", "sdp");
              //logger.info("response = " + response);
              //response.setContent(content, contentTypeHeader);
          }
          dialog = st.getDialog();
          if (dialog != null) {
              logger.info("Dialog " + dialog);
              logger.info("Dialog state " + dialog.getState());
          }
          Response response = ProtocolObjects.messageFactory.createResponse(200, request);
          //st.sendResponse(response);

          // tmp::
  //        FromHeader fromHeader = (FromHeader) response.getHeader(FromHeader.NAME);
  //        String fromTag = fromHeader.getTag();
  //        fromTag = "4321"; // TODO remove - go back to using the correct tags
  //        // end tmp...
          ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
//          toHeader.setTag(/*"4321"*/ fromTag);
          // Application is supposed to set.
          Address address = ProtocolObjects.addressFactory.createAddress("Shootme <sip:"
                  + myAddress + ":" + myPort + ">");
          ContactHeader contactHeader = ProtocolObjects.headerFactory
                  .createContactHeader(address);
          response.addHeader(contactHeader);
          st.sendResponse(response);
          logger.info("TxState after sendResponse = " + st.getState());
          this.serverInviteTid = st;

      } catch (Exception ex) {
          String s = "unexpected exception";

          logger.error(s,ex);
          TestCase.fail(s);
          System.exit(1);
      }
    }

    private void sendResponse(long cseqNum, int statusCode)
    {
      TestCase.assertNotNull("last response was null!", lastRequest);
      TestCase.assertNotNull("last response event was null!", lastRequestEvent);

      SipProvider sipProvider = (SipProvider) lastRequestEvent.getSource();
      Request request = lastRequestEvent.getRequest();
      logger.info("Got an INVITE  " + request);
      try {
          logger.info("shootme: got an Invite sending: " + statusCode);
          ServerTransaction st = lastRequestEvent.getServerTransaction();

          String key = "INVITE" + cseqNum;
          if (st == null) {
              if (serverTransactions.containsKey(key))
              {
                st = serverTransactions.get(key);
              }
              else
              {
                st = sipProvider.getNewServerTransaction(request);
                serverTransactions.put(key, st);
              }
              logger.info("Server transaction created!" + request);

              logger.info("Dialog = " + st.getDialog());
              if (st.getDialog().getApplicationData() == null) {
                  st.getDialog().setApplicationData(new ApplicationData());
              }
          } else {
              // If Server transaction is not null, then this is a re-invite.
              logger.info("This is a RE INVITE ");
              if (st.getDialog() != dialog) {
                  logger.error("Whoopsa Daisy Dialog Mismatch "
                          + st.getDialog() + " / " + dialog);
                  TestCase.fail("Dialog mismatch " + st.getDialog() + " dialog  = " + dialog);
                  System.exit(1);
              }
          }

          logger.info("got a server tranasaction " + st);
          byte[] content = request.getRawContent();
          if (content != null) {
              logger.info(" content = " + new String(content));
              ContentTypeHeader contentTypeHeader = ProtocolObjects.headerFactory
                      .createContentTypeHeader("application", "sdp");
          }
          dialog = st.getDialog();
          if (dialog != null) {
              logger.info("Dialog " + dialog);
              logger.info("Dialog state " + dialog.getState());
          }
          Response response = ProtocolObjects.messageFactory.createResponse(statusCode, request);
          ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
          toHeader.setTag("4321");
          // Application is supposed to set.
          Address address = ProtocolObjects.addressFactory.createAddress("Shootme <sip:"
                  + myAddress + ":" + myPort + ">");
          ContactHeader contactHeader = ProtocolObjects.headerFactory
                  .createContactHeader(address);
          response.addHeader(contactHeader);
          st.sendResponse(response);
          logger.info("TxState after sendResponse = " + st.getState());
          this.serverInviteTid = st;

      } catch (Exception ex) {
          String s = "unexpected exception";

          logger.error(s,ex);
          TestCase.fail(s);
          System.exit(1);
      }
    }

    private void sendAck(long cseqNum)
    {
        TestCase.assertNotNull("last response event was null", lastResponseEvent);

        Transaction tid = lastResponseEvent.getClientTransaction();
        Response response = lastResponse;
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
            Dialog dialog = tid.getDialog();
            logger.info("dialogs = " + dialog + " thisdialog = "  + this.dialog);
          //  TestCase.assertTrue("dialog mismatch", dialog == this.dialog);
            Request ackRequest = dialog.createAck( cseq.getSeqNumber() );
            logger.info("Ack request to send = " + ackRequest);
            logger.info("Sending ACK");
            dialog.sendAck(ackRequest);

        } catch (Exception ex) {
            logger.error(ex);
            System.exit(1);
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
     */
    public void sendReInvite() {

        try
        {
          Request inviteRequest = dialog.createRequest(Request.INVITE);
          MaxForwardsHeader mf = ProtocolObjects.headerFactory.createMaxForwardsHeader(10);
          inviteRequest.setHeader(mf);
          inviteRequest.setHeader(this.contactHeader);
          ClientTransaction ct = provider
                  .getNewClientTransaction(inviteRequest);
          dialog.sendRequest(ct);
        }
        catch (Exception ex)
        {
          logger.error("Hit exception trying reinvite: ", ex);
          TestCase.fail("Hit exception");
          System.exit(1);
        }
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
    public void sendInvite(long cseqNum) {

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
                    .createCSeqHeader(cseqNum, Request.INVITE);

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
            this.clientInviteTid = provider.getNewClientTransaction(request);

            // send the request out.
            this.clientInviteTid.sendRequest();
            this.dialog = this.clientInviteTid.getDialog();
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
        System.exit(1);
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
            Test3C shootist = new Test3C();
            shootist.createSipProvider();
            shootist.provider.addSipListener(shootist);

            shootist.runStateMachine();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

}

