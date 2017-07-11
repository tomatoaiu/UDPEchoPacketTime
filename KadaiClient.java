import java.net.*;  // for DatagramSocket, DatagramPacket, and InetAddress
import java.io.*;   // for IOException

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import java.util.Random;

public class KadaiClient {

    private static final int TIMEOUT = 3000;   // Resend timeout (milliseconds) 3�b
    private static final int MAXTRIES = 5;     // Maximum retransmissions 5��`�������W
    private static final int TOTALDATASIZE = 10000000; // ���f�[�^�T�C�Y 10Mbyte
    private static final int MAXPACKETLENGTH = 8192; // �ő�p�P�b�g�� 8192
    private static final int MINPACKETLENGTH = 6912; // �ŏ��p�P�b�g�� 256
    private static final int SENDCOUNT = 5; // �p�P�b�g���M�� 10
    private static final int INCREMENTAL = 256; // 256

    public static void main(String[] args) throws IOException {
        
        BufferedWriter buffWriter = null;
        
        packetLenErrCheck();

        if (args.length != 2) // Test for correct # of args
        {
            throw new IllegalArgumentException("Parameter(s): <Server> [<Port>]");
        }

        InetAddress serverAddress = InetAddress.getByName(args[0]);  // �����IP�A�h���X
        // Convert input String to bytes using the default character encoding
        
        int servPort = (args.length == 2) ? Integer.parseInt(args[1]) : 7;
        
        int[][] timeToCalculate = new int[numOfArrayPacket()][SENDCOUNT]; // �v�����Ԋi�[�z��
        double[][] bps = new double[numOfArrayPacket()][SENDCOUNT]; // bps�i�[�z��
        int i, suffixCount;
        
        DatagramSocket socket = new DatagramSocket(); // �\�P�b�g����
        socket.setSoTimeout(TIMEOUT);  // Maximum receive blocking time (milliseconds)  
        
        // 256�`8192�p�P�b�g�����ɑ��M����
        for (i = MINPACKETLENGTH, suffixCount = 0; i <= MAXPACKETLENGTH; i += INCREMENTAL, suffixCount++) {
            System.out.println(i + "�p�P�b�g���𑗐M");
            // �w��p�P�b�g����10��𑗂�
            for (int j = 0; j < SENDCOUNT; j++) {
                timeToCalculate[suffixCount][j] = packetTransmission(socket, serverAddress, servPort, i); // 10Mbyte���M���Ԃ��擾
                System.out.println((j + 1)  + "��ڃp�P�b�g���M�������܂����B\r\n");
            }
        }
        
        socket.close();
        
        // �v�����Ԃ̕\��
        for (i = 0; i < numOfArrayPacket(); i++) {
            System.out.println("\r\n���M�p�P�b�g��" + showNowPacket(i));
            for (int j = 0; j < SENDCOUNT; j++) {
                bps[i][j] = doBpsCalculation(timeToCalculate[i][j]); // bps�v�Z
                System.out.print((j + 1) + "��ڂ̌v�����ԁF" + timeToCalculate[i][j] + "ms�F" + bps[i][j] + "bps\r\n");
            }
        }
        
        // txt�t�@�C���ɏ�������
        try {
            buffWriter = new BufferedWriter(new FileWriter("result.txt"));
            for (i = 0; i < numOfArrayPacket(); i++) {
                buffWriter.write("\r\n�p�P�b�g��" + showNowPacket(i) + "\r\n");
                for (int j = 0; j < SENDCOUNT; j++) {
                    buffWriter.write(timeToCalculate[i][j] + "ms:" + bps[i][j] + "bps\r\n");
                }
            }
        } catch (Exception e) {
            System.out.println("�������݃G���[");
        } finally {
            try {
                buffWriter.flush();
                buffWriter.close();
            } catch (Exception e) {
                System.out.println("�I�������G���[");
            }
        }
        
    }// main
    
    // MAXPACKETLENGTH��MINPACKETLENGTH���������ݒ肳��Ă��邩
    private static void packetLenErrCheck(){
        if(MAXPACKETLENGTH % INCREMENTAL != 0){
            System.out.println("MAXPACKETLENGTH���������ݒ肳��Ă��܂���B");
            System.exit(1);
        }
        
        if(MINPACKETLENGTH % INCREMENTAL != 0){
            System.out.println("MINPACKETLENGTH���������ݒ肳��Ă��܂���B");
            System.exit(1);
        }
        
        if(MINPACKETLENGTH > MAXPACKETLENGTH){
            System.out.println("MINPACKETLENGTH��MAXPACKETLENGTH�ȉ��̒l�ɂ��Ă�������");
            System.exit(1);
        }
        
        if(MINPACKETLENGTH <= 255){
            System.out.println("MINPACKETLENGTH��256�ȏ�̒l�ɂ��Ă�������");
            System.exit(1);
        }
        
        if(MINPACKETLENGTH >= 8193){
            System.out.println("MINPACKETLENGTH��8192�ȉ��̒l�ɂ��Ă�������");
            System.exit(1);
        }
    }
    
    // �p�P�b�g���̐���Ԃ�
    private static int numOfArrayPacket(){
        return (MAXPACKETLENGTH - MINPACKETLENGTH) / INCREMENTAL + 1;
    }
    
    // ���݂̃p�P�b�g����Ԃ�
    private static int showNowPacket(int i){
        return MINPACKETLENGTH + (INCREMENTAL * i);
    }
    
    // bps�����߂ĕԂ�
    private static double doBpsCalculation(int timeToCalculate){
        return TOTALDATASIZE / (timeToCalculate / 1000.0);
    }
    
    // 10Mbyte�p�P�b�g���M(�\�P�b�g, �T�[�o�[�A�h���X, �T�[�o�[�|�[�g, 256�̗ݏ�)���đ��M���Ԃ�Ԃ�
    private static int packetTransmission(DatagramSocket socket, InetAddress serverAddress, int servPort, int packetLength)  throws IOException {
        
        long start = System.currentTimeMillis(); // ���Ԍv���J�n
        
        int cnt = 0; // ���v�o�C�g��  
        
        // 10Mbyte���M
        while (cnt < TOTALDATASIZE) {
            
            // ��������
            Random r = new Random();
            byte[] bytesToSend = new byte[packetLength];
            r.nextBytes(bytesToSend); // �o�C�g�z��𗐐��Ŗ�����
            
            int packetSize = bytesToSend.length; // �o�C�g��
            cnt += bytesToSend.length;

            // �������o�C�g�������f�[�^�T�C�Y�𒴂����ꍇ
            if (cnt > TOTALDATASIZE) {
                packetSize -= cnt - TOTALDATASIZE; // ���f�[�^�����炠�ӂꂽ�������A����o�C�g�������炷
                cnt -= cnt - TOTALDATASIZE; // �\���p
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
//                System.out.println("\r\n���v�o�C�g���F" + cnt);
//                System.out.println("�������o�C�g���F" + packetSize);
            } else {
                System.out.println("No response -- giving up.");
            }

        }// while(cnt < TOTALDATASIZE)
        
        long end = System.currentTimeMillis(); // ���Ԍv���I��
        return (int)(end - start);
    }
}