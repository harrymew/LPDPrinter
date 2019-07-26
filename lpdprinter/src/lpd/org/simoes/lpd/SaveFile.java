package org.simoes.lpd;

import org.apache.log4j.PropertyConfigurator;
import org.simoes.lpd.command.*;
import org.simoes.lpd.exception.LPDException;
import org.simoes.lpd.savefile.CommandSaveJob;
import org.simoes.lpd.util.*;

import java.io.*;
import java.net.*;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.simoes.util.StringUtil;


/**
 * The Line Printer Daemon (SFD).  Sets up all of the network communication
 * to run our SFD.  All commands are handled by {@link LPDCommands}.
 *
 * @author Chris_Simoes
 *
 */
public class SaveFile implements Runnable {
    static {
        PropertyConfigurator.configure("logConfig.ini");
    }
    static Logger log = Logger.getLogger(SaveFile.class);

    //	private final static int QUEUE_SIZE = Integer.parseInt(ConfigResources.getProperty("QUEUE_SIZE"));
    private final static int COMMAND_PORT = 515;
    private final static SaveFile INSTANCE = new SaveFile();

    /**
     * Constructor for SFD.
     */
    private SaveFile() {
        super();
        log.debug("SFD(): STARTED");
    }

    /**
     * This class is a singleton.
     * @return the only instance of SFD.
     */
    public static SaveFile getInstance() {
        return INSTANCE;
    }

    /**
     * The run method is implemented so this can be run in its own Thread if desired.
     */
    public void run() {
        final String METHOD_NAME = "run():  ";
        ServerSocket serverSocket = null;
        SFDCommands SFDCommands = new SFDCommands();
        NetUtil netUtil = new NetUtil();
        try {
            serverSocket = new ServerSocket(COMMAND_PORT);
            while(true) {
                log.debug(METHOD_NAME + "trying  to accept() socket connection.");
                Socket connection = serverSocket.accept();
                log.debug(METHOD_NAME + "Connection opened.");
                log.debug(METHOD_NAME + "Created a new PrintJob.");

                InputStream is = null;
                OutputStream os = null;
                ByteArrayOutputStream baos = null;
                // this reads the command and then closes the socket to prepare for another command
                try {
                    is = connection.getInputStream();
                    os = connection.getOutputStream();
                    log.debug(METHOD_NAME + "Got  InputStream.");
                    byte[] command = netUtil.readCommand(is);

                    log.debug(METHOD_NAME + "Command = " + new String(command));

                    // pass command on to SFDCommands
                    SFDCommands.handleCommand(command, is, os);
                } catch(IOException e) {
                    log.debug(METHOD_NAME + "ERROR in try 2");
                    log.debug(METHOD_NAME + e.getMessage());
                    e.printStackTrace();
                } finally {
                    if(null != connection) {
                        log.debug(METHOD_NAME + "about to close connection.");
                        try { connection.close(); }
                        catch(IOException e) {}
                    }
                    if(null != is) {
                        log.debug(METHOD_NAME + "about to close is.");
                        try { is.close(); }
                        catch(IOException e) {}
                    }
                    if(null != os) {
                        log.debug(METHOD_NAME + "about to close os.");
                        try { os.close(); }
                        catch(IOException e) {}
                    }
                }
                // if the Thread pool has over 100 labels, then stop accepting them
/*
				while(queue.size() > QUEUE_SIZE) {
					//TODO: better to wait on the queue and get notified when it changes
					try {
						Thread.sleep(60000); //sleep for a minute
						log.warn(METHOD_NAME + "Queue has over " + QUEUE_SIZE + " labels, going to sleep.");
					} catch(InterruptedException e) {
						log.error(METHOD_NAME + e.getMessage(), e);
					}
				}
*/
            }
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            if(null != serverSocket) {
                try { serverSocket.close(); }
                catch(IOException e) {}
            }
        }

    }

    public static void main(String args[]) {
        log.debug("main(): STARTED");
        System.setProperty("save.path", "d:");
        System.setProperty("save.name", "transferfile.dmp");
        System.setProperty("filebuffer", "1024");
        try {
            SaveFile sfd = SaveFile.getInstance();
            System.out.println("SFD start");
            sfd.run();
        } catch(Exception e) {
            log.fatal(e.getMessage(), e);
        }
        log.debug("main(): FINSHED");
        System.out.println("SFD finished");
    }
}


class SFDCommands {
    static Logger log = Logger.getLogger(SaveFile.class);

    public SFDCommands() {
        super();
    }

    /**
     * Creates the concrete instance of the command class required.
     * Then if calls that class' execute() method.
     * @param command byte[] passed in from the client
     * @param is InputStream from the client
     * @param os OutputStream to the client
     */
    public void handleCommand(byte[] command, InputStream is, OutputStream os)
    {
        final String METHOD_NAME = "handleCommand(): ";

        CommandHandler commandHandler = null;
        try{
            commandHandler = createCommandHandler(command, is, os);
            commandHandler.execute();
        } catch(LPDException e) {
            log.error(METHOD_NAME + "Could not properly handle command: " + command);
            log.error(METHOD_NAME + e.getMessage());
        }
    }

    private CommandHandler createCommandHandler(byte[] command, InputStream is, OutputStream os)
    {
        final String METHOD_NAME = "createCommandHandler():  ";

        CommandHandler result = null;
        Vector info = StringUtil.parseCommand(command);
        try {
            if(null != info && info.size() > 0) {
                byte[] cmd = (byte[]) info.get(0);
                // receive job command
                if(0x2 == cmd[0]) {
                    log.debug(METHOD_NAME + "Receive Job Command");
                    result = new CommandSaveJob(command, is, os);
                }
                else
                    throw new LPDException(METHOD_NAME + "We do not support command:" + new String(cmd));

            } else {
                throw new LPDException(METHOD_NAME + "command passed in was bad, command=" + new String(command));
            }
        } catch(LPDException e) {
            log.error(METHOD_NAME + "Could not properly handle command:" + new String(command));
            log.error(METHOD_NAME + e.getMessage());
        }
        return result;
    }

}