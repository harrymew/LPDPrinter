package org.simoes.lpd.savefile;

import org.simoes.lpd.command.CommandHandler;
import org.simoes.lpd.common.*;
import org.simoes.lpd.exception.*;
import org.simoes.lpd.util.*;
import org.simoes.util.*;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.log4j.Logger;


/**
 *
 * This class handles the Receive a printer job Command in RFC1179.
 * The RFC description is below:
 * <BR>
 * 5.2 02 - Receive a printer job
 * <BR>
 *       +----+-------+----+<BR>
 *       | 02 | Queue | LF |<BR>
 *       +----+-------+----+<BR>
 *       Command code - 2<BR>
 *       Operand - Printer queue name<BR>
 * <BR>
 *    Receiving a job is controlled by a second level of commands.  The
 *    daemon is given commands by sending them over the same connection.
 *    The commands are described in the next section (6).
 * <BR>
 *    After this command is sent, the client must read an acknowledgement
 *    octet from the daemon.  A positive acknowledgement is an octet of
 *    zero bits.  A negative acknowledgement is an octet of any other
 *    pattern.
 *
 *  @author Chris Simoes
 *
 */
public class CommandSaveJob extends CommandHandler {
    static Logger log = Logger.getLogger(CommandSaveJob.class);

    /**
     * Constructor for CommandReceiveJob.
     * @param command the lpr client command,
     * 		it should start with a 0x2 if this command was called
     * @param is the InputStream from the lpr client
     * @param os the OutputStream to the lpr client
     */
    public CommandSaveJob(byte[] command, InputStream is, OutputStream os) {
        super(command, is, os);
    }

    /**
     * Receives the print job and adds it to the queue.
     * @throws LPDException thrown when an error occurs
     */
    public void execute() throws LPDException {
        final String METHOD_NAME = "execute(): ";

        Vector info = StringUtil.parseCommand(command);
        if(null != info && info.size() > 1) {
            byte[] cmd = (byte[]) info.get(0);
            // receive job command
            if(0x2 != cmd[0])
                throw new LPDException(METHOD_NAME + "command passed in was bad, cmd[0]=" + new String(cmd));
            try {
                os.write(Constants.ACK); // write ACK to client
                log.debug(METHOD_NAME + "Save Job Command");
                ControlFile controlFile = null;
                try
                {
                    NetUtil netUtil = new NetUtil();
                    byte[] receiveInput;
                    Vector vcmd;
                    for (int i=1; i<=2; i++)
                    {
                        receiveInput = netUtil.readNextInput(is, os);
                        vcmd = StringUtil.parseCommand(receiveInput);
                        if(receiveInput[0] == 3) {
                            streamToFile(is, os, vcmd);
                            return;
                        }
                        else if (receiveInput[0] == 2) {
                            controlFile = setControlFile(is,os,vcmd);
                        }
                    }
                } catch(Exception ex) {
                    log.error(METHOD_NAME + "problems  reading Input");
                }

            } catch(IOException e) {
                log.error(METHOD_NAME + e.getMessage());
                throw new LPDException(METHOD_NAME + e.getMessage());
            }
        }
        else
        {
            throw new LPDException(METHOD_NAME + "command not understood, command=" + new String(command));
        }
    }



    private void streamToFile(InputStream is, OutputStream os, Vector cmd) throws LPDException
    {
        final String METHOD_NAME = "setDataFile(): ";

        String filename = System.getProperty("save.name");
        String savePath = System.getProperty("save.path");

        // get the data file
        try
        {
            String dataFileSize = new String((byte[]) cmd.get(1));
            log.info(METHOD_NAME + "DataFile size=" + dataFileSize);
            String dataFileHeader = new String((byte[]) cmd.get(2));
            Vector headerVector = StringUtil.parsePrintFileName(dataFileHeader);

            byte[] transfername = new byte[512];
            byte[] md5 = new byte[16];
            is.read(transfername);
            is.read(md5);
            SimpleDateFormat df = new SimpleDateFormat("HHmmss");//设置日期格式
            filename = new String(transfername).trim();
            filename =  df.format(new Date()) + filename.replace("\u0000", "");

            File transferfile =  new File(savePath+File.separator+ filename);

            if(null != headerVector && headerVector.size() == 3) {
                int dfSize = 0;
                try {
                    dfSize = Integer.parseInt(dataFileSize) - 512 - 16;
                } catch(NumberFormatException e) {
                    log.error(METHOD_NAME + e.getMessage());
                }
                int bufferSize = Integer.parseInt(System.getProperty("filebuffer"));
                byte[] buffer = new byte[bufferSize];
                int filesize = 0;
                if(0 == dfSize) {
                    os.write(Constants.ACK);
                } else {
                    DataOutputStream bos =  new DataOutputStream(new FileOutputStream(transferfile, false));
                    int count = 0;
                    int inlen = is.read(buffer);
                    count += inlen;
                    if(count == (dfSize +1)) {
                        bos.write(buffer,0,inlen-1);
                        filesize += inlen-1;
                        bos.flush();
                    }
                    else if(count == dfSize)
                    {
                        bos.write(buffer);
                        bos.flush();
                        filesize += inlen;
                        inlen = is.read(buffer);
                    }
                    else {
                        bos.write(buffer);
                        bos.flush();
                        filesize += inlen;
                        while (true)
                        { //should see a 0
                            inlen = is.read(buffer);
                            if(inlen == -1)
                                break;
                            count += inlen;
                            if(count == dfSize+1) {
                                bos.write(buffer, 0, inlen - 1);
                                bos.flush();
                                filesize += inlen-1;
                                break;
                            }
                            else if(count == dfSize) {
                                bos.write(buffer, 0, inlen);
                                bos.flush();
                                filesize += inlen;
                                inlen = is.read(buffer);
                                break;
                            }
                            else if(filesize > dfSize) {
                                System.out.println("IO error file size ");
                                break;
                            }
                            else {
                                bos.write(buffer);
                                bos.flush();
                                if(transferfile.length() != count) {
                                    System.out.println("IO error file size ");
                                    break;
                                }
                                filesize += inlen;
                            }
                        }
                    }
                    bos.flush();
                    bos.close();
                    System.out.println(transferfile.length());
                    if(checkMD5(transferfile, md5))
                        System.out.println("md5 right");
                    else
                        System.out.println("md5 wrong");
                    os.write(Constants.ACK);

                }
                log.debug("transferfile success save to :" +transferfile.getName());
                System.out.println("transferfile success save to :" +transferfile.getName() + " filesize " + filesize);
            } else {
                throw new LPDException(METHOD_NAME + "dataFileHeader did not parse properly, dataFileHeader=" + dataFileHeader);
            }
        } catch(IOException e) {
            log.error(METHOD_NAME + "Had trouble save the data file stream.");
            log.error(METHOD_NAME + e.getMessage());
            throw new LPDException(e);
        }
    }

    private boolean checkMD5(File file, byte[] md5)
    {
        try {
            FileInputStream dis = new FileInputStream((file));
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            DigestInputStream digestInputStream = new DigestInputStream(dis, messageDigest);
            byte[] result = null;
            byte[] buf = new byte[8196];
            while (digestInputStream.read(buf) > 0) {
                messageDigest = digestInputStream.getMessageDigest();
                result = messageDigest.digest();
            }
            digestInputStream.close();
            if (Arrays.equals(result, md5))
                return true;
            else
                return false;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Receives the ControlFile
     * @param is InputStream containing the ControlFile after the header
     * @param os OutputStream containing the ControlFile after the header
     * @param cmd the initial header to the controlFile
     * @return the ControlFile object populated
     * @throws LPDException
     */
    private ControlFile setControlFile(InputStream is, OutputStream os, Vector cmd) throws LPDException
    {
        final String METHOD_NAME = "setControlFile():";
        ControlFile controlFile = null;

        // get the control file
        try {
            NetUtil netUtil = new NetUtil();
            String controlFileSize = new String((byte[]) cmd.get(1));
            String controlFileHeader = new String((byte[]) cmd.get(2));
            Vector headerVector = StringUtil.parsePrintFileName(controlFileHeader);
            if(null != headerVector && headerVector.size() == 3) {
                byte[] cFile = netUtil.readControlFile(is, os);
                controlFile = new ControlFile();
                controlFile.setCount(controlFileSize);
                controlFile.setJobNumber((String) headerVector.get(1));
                controlFile.setHostName((String) headerVector.get(2));
                controlFile.setContents(cFile);
                log.debug(METHOD_NAME + "Control  File=" + new String(cFile));
                controlFile.setControlFileCommands(cFile);
                log.debug(METHOD_NAME + "Control File Commands=" + controlFile.getControlFileCommands().toString());
                return controlFile;
            } else {
                throw new LPDException(METHOD_NAME + "controlFileHeader did not parse properly, controlFileHeader=" + controlFileHeader);
            }
        } catch(IOException e) {
            log.error(METHOD_NAME + "Had trouble receiving the control file.");
            log.error(METHOD_NAME + e.getMessage());
            throw new LPDException(e);
        }
    }

}