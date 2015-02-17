package gov.nist.core;

import java.net.InetSocketAddress;
import java.util.Properties;

import org.apache.log4j.Logger;

/**
 * This class abstracts away single-instanct and multi0instance loggers
 * legacyLogger is the old-school one logger per stack reference otherLogger is
 * multiinstance logger
 *
 * @author Vladimir Ralev
 *
 */
public class CommonLogger implements StackLogger
{
    private String name;
    private StackLogger otherLogger;

    public static boolean useLegacyLogger = true;
    public static StackLogger legacyLogger;

    public CommonLogger(String name)
    {
        this.name = name;
    }

    public static void init(Properties p)
    {
    }

    private StackLogger logger()
    {
        if (useLegacyLogger)
        {
            if (legacyLogger == null)
            {
                return new CommonLoggerLog4j(Logger.getLogger(name));
            }
            return legacyLogger;
        }
        else
        {
            if (otherLogger == null)
            {
                otherLogger = new CommonLoggerLog4j(Logger.getLogger(name));
            }
            return otherLogger;
        }
    }

    public static StackLogger getLogger(String name)
    {
        return new CommonLogger(name);
    }

    public static StackLogger getLogger(Class clazz)
    {
        return getLogger(clazz.getName());
    }

    public void disableLogging()
    {
        logger().disableLogging();
    }

    public void enableLogging()
    {
        logger().enableLogging();
    }

    public int getLineCount()
    {
        return logger().getLineCount();
    }

    public String getLoggerName()
    {
        return logger().getLoggerName();
    }

    public boolean isLoggingEnabled()
    {
        return logger().isLoggingEnabled();
    }

    public boolean isLoggingEnabled(int logLevel)
    {
        return logger().isLoggingEnabled(logLevel);
    }

    public void logTrace(String message)
    {
        logger().logTrace(message);
    }

    public void logDebug(String message)
    {
        logger().logDebug(message);
    }

    public void logInfo(String string)
    {
        logger().logInfo(string);
    }

    @Override
    public void logInfo(String string, Throwable cause)
    {
        logger().logInfo(string, cause);
    }

    public void logWarning(String string)
    {
        logger().logWarning(string);
    }

    @Override
    public void logWarning(String string, Throwable cause)
    {
        logger().logWarning(string, cause);
    }

    @Override
    public void logWarning(Throwable cause)
    {
        logger().logWarning(cause);
    }

    public void logError(String message)
    {
        logger().logError(message);
    }

    @Override
    public void logError(String message, Throwable cause)
    {
        logger().logError(message, cause);
    }

    public void logException(Throwable ex)
    {
        logger().logException(ex);
    }

    public void logFatalError(String message)
    {
        logger().logFatalError(message);
    }

    @Override
    public void logFatalError(String message, Throwable cause)
    {
        logger().logFatalError(message, cause);
    }

    public void logStackTrace()
    {

        logger().logStackTrace();
    }

    public void logStackTrace(int traceLevel)
    {
        logger().logStackTrace(traceLevel);
    }

    public void setBuildTimeStamp(String buildTimeStamp)
    {
        logger().setBuildTimeStamp(buildTimeStamp);
    }

    public void setStackProperties(Properties stackProperties)
    {
        legacyLogger.setStackProperties(stackProperties);
    }

    @Override
    public void logSent(InetSocketAddress clientAddr, InetSocketAddress serverAddr,
            String transportType, String body)
    {
        logger().logSent(clientAddr, serverAddr, transportType, body);
    }

    @Override
    public void logReceived(InetSocketAddress clientAddr, InetSocketAddress serverAddr,
            String transportType, String body)
    {
        logger().logReceived(clientAddr, serverAddr, transportType, body);
    }
}
