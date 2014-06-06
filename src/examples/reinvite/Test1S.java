package examples.reinvite;

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
 * @author M. Ranganathan
 */

public class Test1S implements SipListener {

    // To run on two machines change these to suit.
    public static final String myAddress = "127.0.0.1";

    public static final int myPort = 5070;

    private ServerTransaction inviteTid;


    private static Logger logger = Logger.getLogger(Test1S.class);

    static {
        try {
        logger.addAppender(new FileAppender(new PatternLayout("%d{HH:mm:ss.SSS} %-5p [%t]: %m%n"),
                    ProtocolObjects.logFileDirectory + "shootmeconsolelog.txt"));
        } catch (Exception ex) {
            throw new RuntimeException ("could not open log file");
        }
    }


    private Dialog dialog;

    private boolean okRecieved;

    class ApplicationData {
        protected int ackCount;
    }

    protected static final String usageString = "java "
            + "examples.shootist.Shootist \n"
            + ">>>> is your class path set to the root?";



    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        ServerTransaction serverTransactionId = requestEvent
                .getServerTransaction();

        logger.info("\n\nReeequest " + request.getMethod()
                + " received at shootme "
                + " with server transaction id " + serverTransactionId);

        if (request.getMethod().equals(Request.INVITE)) {
            processInvite(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.ACK)) {
            processAck(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.INFO)) {
           logger.debug("INFOOOO");
            processInfo(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.BYE)) {
            processBye(requestEvent, serverTransactionId);
        }

    }

    /**
     * Process the ACK request. Send the bye and complete the call flow.
     */
    public void processAck(RequestEvent requestEvent,
            ServerTransaction serverTransaction) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        try {
            logger.info("shootme: got an ACK "
                    + requestEvent.getRequest());
            TestCase.assertTrue ("dialog mismatch ", this.dialog == serverTransaction.getDialog());
        } catch (Exception ex) {
            String s = "Unexpected error";
            logger.error(s,ex);
            TestCase.fail(s);
        }
    }

    /**
     * Process the invite request.
     */
    public void processInvite(RequestEvent requestEvent,
            ServerTransaction serverTransaction) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        logger.info("Got an INVITE  " + request);
        try {
            logger.info("shootme: got an Invite sending OK");
            // logger.info("shootme: " + request);
            Response response = ProtocolObjects.messageFactory.createResponse(180, request);
            ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
            toHeader.setTag("4321");
            Address address = ProtocolObjects.addressFactory.createAddress("Shootme <sip:"
                    + myAddress + ":" + myPort + ">");
            ContactHeader contactHeader = ProtocolObjects.headerFactory
                    .createContactHeader(address);
            response.addHeader(contactHeader);
            ServerTransaction st = requestEvent.getServerTransaction();

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
                }
            }

            // Thread.sleep(5000);
            logger.info("got a server tranasaction " + st);
            byte[] content = request.getRawContent();
            if (content != null) {
                logger.info(" content = " + new String(content));
                ContentTypeHeader contentTypeHeader = ProtocolObjects.headerFactory
                        .createContentTypeHeader("application", "sdp");
                logger.info("response = " + response);
                response.setContent(content, contentTypeHeader);
            }
            dialog = st.getDialog();
            if (dialog != null) {
                logger.info("Dialog " + dialog);
                logger.info("Dialog state " + dialog.getState());
            }
            st.sendResponse(response);
            response = ProtocolObjects.messageFactory.createResponse(200, request);
            toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
            toHeader.setTag("4321");
            // Application is supposed to set.
            response.addHeader(contactHeader);
            st.sendResponse(response);
            logger.info("TxState after sendResponse = " + st.getState());
            this.inviteTid = st;

        } catch (Exception ex) {
            String s = "unexpected exception";

            logger.error(s,ex);
            TestCase.fail(s);
        }
    }

    public void processInfo(RequestEvent requestEvent,
          ServerTransaction serverTransactionId) {

      SipProvider sipProvider = (SipProvider) requestEvent.getSource();
      Request request = requestEvent.getRequest();
      try {
          logger.info("shootme:  got an INFO sending OK.");
          Response response = ProtocolObjects.messageFactory.createResponse(200, request);
          if (serverTransactionId != null) {
              serverTransactionId.sendResponse(response);
              logger.info("Dialog State is "
                      + serverTransactionId.getDialog().getState());
          } else {
              logger.info("null server tx.");
              // sipProvider.sendResponse(response);
          }

      } catch (Exception ex) {
          String s = "Unexpected exception";
          logger.error(s,ex);
          TestCase.fail(s);

      }
  }

    /**
     * Process the bye request.
     */
    public void processBye(RequestEvent requestEvent,
            ServerTransaction serverTransactionId) {

        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        try {
            logger.info("shootme:  got a bye sending OK.");
            Response response = ProtocolObjects.messageFactory.createResponse(200, request);
            if (serverTransactionId != null) {
                serverTransactionId.sendResponse(response);
                logger.info("Dialog State is "
                        + serverTransactionId.getDialog().getState());
            } else {
                logger.info("null server tx.");
                // sipProvider.sendResponse(response);
            }

        } catch (Exception ex) {
            String s = "Unexpected exception";
            logger.error(s,ex);
            TestCase.fail(s);

        }
    }

    public void processResponse(ResponseEvent responseReceivedEvent) {
        logger.info("Got a response");
    }

    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
        Transaction transaction;
        if (timeoutEvent.isServerTransaction()) {
            transaction = timeoutEvent.getServerTransaction();
        } else {
            transaction = timeoutEvent.getClientTransaction();
        }
        logger.info("state = " + transaction.getState());
        logger.info("dialog = " + transaction.getDialog());
        logger.info("dialogState = "
                + transaction.getDialog().getState());
        logger.info("Transaction Time out");
    }




    public SipProvider createSipProvider() throws Exception {
        ListeningPoint lp = ProtocolObjects.sipStack.createListeningPoint(myAddress,
                myPort, ProtocolObjects.transport);


        SipProvider sipProvider = ProtocolObjects.sipStack.createSipProvider(lp);
        return sipProvider;
    }


    public static void main(String args[]) throws Exception {
        logger.addAppender( new ConsoleAppender(new PatternLayout("%d{HH:mm:ss.SSS} %-5p [%t]: %m%n")));
        ProtocolObjects.init("shootme", true);
        Test1S shootme = new Test1S();
        shootme.createSipProvider().addSipListener(shootme);

    }

    /*
     * (non-Javadoc)
     *
     * @see javax.sip.SipListener#processIOException(javax.sip.IOExceptionEvent)
     */
    public void processIOException(IOExceptionEvent exceptionEvent) {
        logger.error("An IO Exception was detected : "
                + exceptionEvent.getHost());

    }

    /*
     * (non-Javadoc)
     *
     * @see javax.sip.SipListener#processTransactionTerminated(javax.sip.TransactionTerminatedEvent)
     */
    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
        logger.info("Tx terminated event ");

    }

    /*
     * (non-Javadoc)
     *
     * @see javax.sip.SipListener#processDialogTerminated(javax.sip.DialogTerminatedEvent)
     */
    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        logger.info("Dialog terminated event detected ");

    }

}

