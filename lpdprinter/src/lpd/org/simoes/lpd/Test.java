package org.simoes.lpd;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;

public class Test {
    public static void main(String ... args) throws Exception
    {
        byte[] buf = new byte[1024];
        File file = new File("d:\\171511tmp.exe");
        DataInputStream dis = new DataInputStream(new FileInputStream(file));
        dis.skip(209715000);
        int len = dis.read(buf);
        byte[] bz = new byte[1024];
        Arrays.fill(bz, (byte)66);

        System.out.println(Arrays.equals(buf, bz));
    }
}
