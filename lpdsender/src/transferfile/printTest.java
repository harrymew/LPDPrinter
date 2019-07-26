package transferfile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Scanner;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.DocAttributeSet;
import javax.print.attribute.HashDocAttributeSet;
import javax.print.attribute.HashPrintRequestAttributeSet;

public class printTest {
	public static void main(String[] args) throws IOException
	{
		//System.out.println(System.getProperty("user.dir"));
		String fileName = "test.txt";
		String path = "D:";
        File file = new File((path + java.io.File.separator + fileName)); // 获取选择的文件
        if(args.length> 0 )
		{
        	file = new File(args[0]);
        	fileName = file.getName();
		}
        System.out.println("transferring " + file.getAbsolutePath());
        
        /*byte[] newfile = new byte[1024*1024];
        Arrays.fill(newfile, (byte)23);
        DataOutputStream dos = new DataOutputStream(new FileOutputStream(file));
        for(int i = 0; i<230; i++)
        {
        	dos.write(newfile);
        	dos.flush();
        }
        dos.close();*/
        
        byte [] md5 = checkmd5(file);
        
        // 构建打印请求属性集
        HashPrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();
        // 设置打印格式，因为未确定类型，所以选择autosense
        DocFlavor flavor = DocFlavor.BYTE_ARRAY.JPEG;
        // 查找所有的可用的打印服务
        PrintService printService[] = PrintServiceLookup.lookupPrintServices(flavor, pras);
        // 定位默认的打印服务
        //PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
        // 显示打印对话框
        //PrintService service = ServiceUI.printDialog(null, 200, 200, printService, defaultService, flavor, pras);
        PrintService service = null;
        System.out.println("input LPDPrinter name(0 for defalut name):");
        Scanner sin = new Scanner(System.in);
        String ldp = sin.next();
        String printerName = "LPDPrinter(from HDP redirection)";
        if(ldp.length()>5)
        	printerName = ldp;
        System.out.println("finding " + printerName);
        sin.close();
        for(PrintService ip: printService)
        {
            if(ip.getName().equals(printerName))
            {
                service = ip;
                break;
            }
        }
        if (service != null) {
            try {
                DocPrintJob job = service.createPrintJob(); // 创建打印作业
                FileInputStream fis = new FileInputStream(file); // 构造待打印的文件流
                DocAttributeSet das = new HashDocAttributeSet();
                
                byte[] infobytes;
                infobytes = Arrays.copyOf(fileName.getBytes(), 512+16);
                
                System.arraycopy(md5, 0, infobytes, 512, 16);
             
                InputStream fns = new ByteArrayInputStream(infobytes);
                
                
                SequenceInputStream printStream = new SequenceInputStream(fns, fis);
                Doc fdoc = new SimpleDoc(printStream, DocFlavor.INPUT_STREAM.AUTOSENSE, das);
                job.print(fdoc, null);
                System.out.println("transition finished");
                fis.close();
                fns.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else
        	System.out.println("cant find lpdprinter.");
	}
	
	private static byte[] checkmd5(File file)
	{
		DigestInputStream digestInputStream;
		try {
			byte[] result = null;
			byte[] buf = new byte[8196];
 			FileInputStream fis = new FileInputStream(file);
			MessageDigest messageDigest = MessageDigest.getInstance("MD5");
		    digestInputStream= new DigestInputStream(fis, messageDigest);
			while(digestInputStream.read(buf) > 0)
			{
				messageDigest = digestInputStream.getMessageDigest();
				result = messageDigest.digest();
			}
			digestInputStream.close();
			return result;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return new byte[16];
		}
	}
}
