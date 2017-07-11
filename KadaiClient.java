import java.net.*;  // for DatagramSocket, DatagramPacket, and InetAddress
import java.io.*;   // for IOException

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import java.util.Random;

public class KadaiClient {

    private static final int TIMEOUT = 3000;   // Resend timeout (milliseconds) 3秒
    private static final int MAXTRIES = 5;     // Maximum retransmissions 5回チャレンジ
    private static final int TOTALDATASIZE = 10000000; // 総データサイズ 10Mbyte
    private static final int MAXPACKETLENGTH = 8192; // 最大パケット長 8192
    private static final int MINPACKETLENGTH = 6912; // 最少パケット長 256
    private static final int SENDCOUNT = 5; // パケット送信回数 10
    private static final int INCREMENTAL = 256; // 256

    public static void main(String[] args) throws IOException {
        
        BufferedWriter buffWriter = null;
        
        packetLenErrCheck();

        if (args.length != 2) // Test for correct # of args
        {
            throw new IllegalArgumentException("Parameter(s): <Server> [<Port>]");
        }

        InetAddress serverAddress = InetAddress.getByName(args[0]);  // 相手のIPアドレス
        // Convert input String to bytes using the default character encoding
        
        int servPort = (args.length == 2) ? Integer.parseInt(args[1]) : 7;
        
        int[][] timeToCalculate = new int[numOfArrayPacket()][SENDCOUNT]; // 計測時間格納配列
        double[][] bps = new double[numOfArrayPacket()][SENDCOUNT]; // bps格納配列
        int i, suffixCount;
        
        DatagramSocket socket = new DatagramSocket(); // ソケット生成
        socket.setSoTimeout(TIMEOUT);  // Maximum receive blocking time (milliseconds)  
        
        // 256～8192パケット長毎に送信する
        for (i = MINPACKETLENGTH, suffixCount = 0; i <= MAXPACKETLENGTH; i += INCREMENTAL, suffixCount++) {
            System.out.println(i + "パケット長を送信");
            // 指定パケット長で10回を送る
            for (int j = 0; j < SENDCOUNT; j++) {
                timeToCalculate[suffixCount][j] = packetTransmission(socket, serverAddress, servPort, i); // 10Mbyte送信時間を取得
                System.out.println((j + 1)  + "回目パケット送信完了しました。\r\n");
            }
        }
        
        socket.close();
        
        // 計測時間の表示
        for (i = 0; i < numOfArrayPacket(); i++) {
            System.out.println("\r\n送信パケット長" + showNowPacket(i));
            for (int j = 0; j < SENDCOUNT; j++) {
                bps[i][j] = doBpsCalculation(timeToCalculate[i][j]); // bps計算
                System.out.print((j + 1) + "回目の計測時間：" + timeToCalculate[i][j] + "ms：" + bps[i][j] + "bps\r\n");
            }
        }
        
        // txtファイルに書き込み
        try {
            buffWriter = new BufferedWriter(new FileWriter("result.txt"));
            for (i = 0; i < numOfArrayPacket(); i++) {
                buffWriter.write("\r\nパケット長" + showNowPacket(i) + "\r\n");
                for (int j = 0; j < SENDCOUNT; j++) {
                    buffWriter.write(timeToCalculate[i][j] + "ms:" + bps[i][j] + "bps\r\n");
                }
            }
        } catch (Exception e) {
            System.out.println("書き込みエラー");
        } finally {
            try {
                buffWriter.flush();
                buffWriter.close();
            } catch (Exception e) {
                System.out.println("終了処理エラー");
            }
        }
        
    }// main
    
    // MAXPACKETLENGTHとMINPACKETLENGTHが正しく設定されているか
    private static void packetLenErrCheck(){
        if(MAXPACKETLENGTH % INCREMENTAL != 0){
            System.out.println("MAXPACKETLENGTHが正しく設定されていません。");
            System.exit(1);
        }
        
        if(MINPACKETLENGTH % INCREMENTAL != 0){
            System.out.println("MINPACKETLENGTHが正しく設定されていません。");
            System.exit(1);
        }
        
        if(MINPACKETLENGTH > MAXPACKETLENGTH){
            System.out.println("MINPACKETLENGTHはMAXPACKETLENGTH以下の値にしてください");
            System.exit(1);
        }
        
        if(MINPACKETLENGTH <= 255){
            System.out.println("MINPACKETLENGTHは256以上の値にしてください");
            System.exit(1);
        }
        
        if(MINPACKETLENGTH >= 8193){
            System.out.println("MINPACKETLENGTHは8192以下の値にしてください");
            System.exit(1);
        }
    }
    
    // パケット長の数を返す
    private static int numOfArrayPacket(){
        return (MAXPACKETLENGTH - MINPACKETLENGTH) / INCREMENTAL + 1;
    }
    
    // 現在のパケット長を返す
    private static int showNowPacket(int i){
        return MINPACKETLENGTH + (INCREMENTAL * i);
    }
    
    // bpsを求めて返す
    private static double doBpsCalculation(int timeToCalculate){
        return TOTALDATASIZE / (timeToCalculate / 1000.0);
    }
    
    // 10Mbyteパケット送信(ソケット, サーバーアドレス, サーバーポート, 256の累乗)して送信時間を返す
    private static int packetTransmission(DatagramSocket socket, InetAddress serverAddress, int servPort, int packetLength)  throws IOException {
        
        long start = System.currentTimeMillis(); // 時間計測開始
        
        int cnt = 0; // 合計バイト数  
        
        // 10Mbyte送信
        while (cnt < TOTALDATASIZE) {
            
            // 乱数生成
            Random r = new Random();
            byte[] bytesToSend = new byte[packetLength];
            r.nextBytes(bytesToSend); // バイト配列を乱数で満たす
            
            int packetSize = bytesToSend.length; // バイト数
            cnt += bytesToSend.length;

            // 送ったバイト数が総データサイズを超えた場合
            if (cnt > TOTALDATASIZE) {
                packetSize -= cnt - TOTALDATASIZE; // 総データ数からあふれた分だけ、送るバイト数を減らす
                cnt -= cnt - TOTALDATASIZE; // 表示用
            }

            DatagramPacket sendPacket = new DatagramPacket(bytesToSend, // Sending packet
                    packetSize, serverAddress, servPort);

            DatagramPacket receivePacket = // Receiving packet
                    new DatagramPacket(new byte[packetSize], packetSize);

            int tries = 0;      // Packets may be lost, so we have to keep trying
            boolean receivedResponse = false;
            do {
                socket.send(sendPacket);          // Send the echo string

                try {
                    socket.receive(receivePacket);  // Attempt echo reply reception

                    if (!receivePacket.getAddress().equals(serverAddress)) // Check source
                    {
                        throw new IOException("Received packet from an unknown source");
                    }

                    receivedResponse = true;
                } catch (InterruptedIOException e) {  // We did not get anything
                    tries += 1;
                    System.out.println("Timed out, " + (MAXTRIES - tries) + " more tries...");
                }

            } while ((!receivedResponse) && (tries < MAXTRIES));

            if (receivedResponse) {
//                
//                for (byte b : receivePacket.getData()) {
//                    System.out.print(b + " ");
//                } 
//                System.out.println("\r\n合計バイト数：" + cnt);
//                System.out.println("送ったバイト数：" + packetSize);
            } else {
                System.out.println("No response -- giving up.");
            }

        }// while(cnt < TOTALDATASIZE)
        
        long end = System.currentTimeMillis(); // 時間計測終了
        return (int)(end - start);
    }
}